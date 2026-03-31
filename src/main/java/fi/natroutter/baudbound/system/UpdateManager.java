package fi.natroutter.baudbound.system;

import fi.natroutter.foxlib.updates.GitHubVersionChecker;
import fi.natroutter.foxlib.updates.data.UpdateStatus;
import fi.natroutter.foxlib.updates.data.VersionInfo;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.storage.StorageProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Handles downloading a new application JAR from a GitHub release and restarting the process.
 * <p>
 * Download-and-restart requires the application to be running from a JAR file. In IDE /
 * class-file mode {@link #isRunningFromJar()} returns {@code false} and callers should
 * hide or disable update download controls accordingly.
 * <p>
 * The restart mechanism writes a small platform-specific script ({@code .bat} on Windows,
 * {@code .sh} on Unix) alongside the current JAR. The script waits briefly for this JVM
 * to exit, replaces the old JAR with the downloaded one, launches the new JAR, and
 * deletes itself. Once the script is launched, this JVM calls {@link System#exit(int) System.exit(0)}.
 */
public class UpdateManager {

    /** Expected asset filename in GitHub releases. Must match the maven-shade finalName. */
    private static final String JAR_ASSET_NAME = "BaudBound.jar";

    private static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1_000;

    private UpdateManager() {}

    /**
     * Starts a background virtual thread that periodically checks GitHub for a new release.
     * Runs immediately on startup, then repeats every {@value #CHECK_INTERVAL_MS} ms.
     * Checks are skipped when
     * {@link fi.natroutter.baudbound.storage.DataStore.Settings.Generic#isCheckForUpdatesEnabled()}
     * is {@code false}. When an update is found it is logged at INFO level; {@code onUpdateFound}
     * is called so the caller can take additional action (e.g. showing a dialog).
     *
     * @param storage       the live storage provider used to read the current setting each cycle
     * @param onUpdateFound called on the checker virtual thread with the update info
     */
    public static void startBackgroundChecker(StorageProvider storage,
                                              Consumer<VersionInfo> onUpdateFound) {
        Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (storage.getData().getSettings().getGeneric().isCheckForUpdatesEnabled()) {
                    runCheck(onUpdateFound);
                }
                try { Thread.sleep(CHECK_INTERVAL_MS); } catch (InterruptedException e) { return; }
            }
        });
    }

    /**
     * Runs a one-shot update check on a virtual thread and delivers the full
     * {@link VersionInfo} result (regardless of update status) to {@code onResult},
     * or an error message to {@code onError} on failure.
     * Intended for manual "Check for Updates" triggers in the UI.
     */
    public static void checkNow(Consumer<VersionInfo> onResult, Consumer<String> onError) {
        Thread.ofVirtual().start(() -> {
            GitHubVersionChecker checker = new GitHubVersionChecker("NATroutter", "BaudBound", BaudBound.VERSION);
            checker.checkForUpdates()
                    .thenAccept(onResult)
                    .exceptionally(ex -> {
                        onError.accept(ex.getMessage() != null ? ex.getMessage() : "Unknown error");
                        return null;
                    });
        });
    }

    private static void runCheck(Consumer<VersionInfo> onUpdateFound) {
        GitHubVersionChecker checker = new GitHubVersionChecker("NATroutter", "BaudBound", BaudBound.VERSION);
        checker.checkForUpdates().thenAccept(info -> {
            if (info.getUpdateAvailable() == UpdateStatus.YES) {
                BaudBound.getLogger().info("Update available: " + info.getLatestVersion()
                        + " (current: " + BaudBound.VERSION + ") — " + info.getReleaseUrl());
                onUpdateFound.accept(info);
            }
        }).exceptionally(ex -> {
            BaudBound.getLogger().warn("Background update check failed: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Returns {@code true} if the application is running from a JAR file.
     * Download-and-restart is only possible in JAR mode.
     */
    public static boolean isRunningFromJar() {
        return getJarFile().isPresent();
    }

    /**
     * Returns the currently-running JAR {@link File}, or empty when not running from a JAR
     * (e.g. IDE launch from class files).
     */
    public static Optional<File> getJarFile() {
        try {
            File f = new File(
                    BaudBound.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI());
            if (f.isFile() && f.getName().endsWith(".jar")) return Optional.of(f);
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    /**
     * Constructs the direct JAR asset download URL from a GitHub release page URL.
     * Assumes the release contains an asset named {@value #JAR_ASSET_NAME}.
     *
     * @param releaseUrl the HTML URL of the GitHub release
     *                   (e.g. {@code https://github.com/owner/repo/releases/tag/v1.2.0})
     * @return the direct download URL for the JAR asset
     */
    public static String buildJarDownloadUrl(String releaseUrl) {
        String tag = releaseUrl.substring(releaseUrl.lastIndexOf('/') + 1);
        return "https://github.com/NATroutter/BaudBound/releases/download/"
                + tag + "/" + JAR_ASSET_NAME;
    }

    /**
     * Downloads the new JAR on a virtual thread and, on success, writes a restart script and
     * calls {@link System#exit(int) System.exit(0)}.
     * <p>
     * Progress is reported via {@code onProgress(downloadedBytes, totalBytes)};
     * {@code totalBytes} is {@code -1} when the server does not send a Content-Length.
     * On failure the downloaded partial file is deleted and {@code onError} is called with a
     * human-readable description. The caller is responsible for resetting any UI state.
     *
     * @param downloadUrl URL to download the new JAR from
     * @param currentJar  the currently-running JAR file (from {@link #getJarFile()})
     * @param onProgress  invoked periodically with running byte totals
     * @param onError     invoked once on failure with an error description
     */
    public static void downloadAndRestart(String downloadUrl, File currentJar,
                                          BiConsumer<Long, Long> onProgress,
                                          Consumer<String> onError) {
        Thread.ofVirtual().start(() -> {
            File replacement = new File(currentJar.getParentFile(), currentJar.getName() + ".new");
            try {
                download(downloadUrl, replacement, onProgress);
                launchRestartScript(currentJar, replacement);
                System.exit(0);
            } catch (Exception e) {
                replacement.delete();
                String msg = e.getMessage();
                onError.accept(msg != null ? msg : e.getClass().getSimpleName());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void download(String downloadUrl, File dest,
                                  BiConsumer<Long, Long> onProgress) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        conn.setRequestProperty("User-Agent", "BaudBound/" + BaudBound.VERSION);
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Server returned HTTP " + status + " for " + downloadUrl);
        }

        long total = conn.getContentLengthLong();
        try (InputStream in  = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            long downloaded = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                downloaded += n;
                onProgress.accept(downloaded, total);
            }
        }
    }

    /**
     * Writes a platform-specific restart script next to the current JAR and launches it
     * as a detached process. The script waits for this JVM to exit, replaces
     * {@code current} with {@code replacement}, starts the new JAR, then deletes itself.
     */
    private static void launchRestartScript(File current, File replacement) throws IOException {
        File dir = current.getParentFile();
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            File script = new File(dir, "baudbound_update.bat");
            try (PrintWriter w = new PrintWriter(new FileWriter(script))) {
                w.println("@echo off");
                // ping waits ~2 s — enough for this JVM to finish its shutdown
                w.println("ping -n 3 127.0.0.1 >nul");
                w.println("move /y \"" + replacement.getAbsolutePath()
                        + "\" \"" + current.getAbsolutePath() + "\"");
                w.println("start javaw -jar \"" + current.getAbsolutePath() + "\"");
                w.println("del \"%~f0\"");
            }
            new ProcessBuilder("cmd", "/c", script.getAbsolutePath()).start();
        } else {
            File script = new File(dir, "baudbound_update.sh");
            try (PrintWriter w = new PrintWriter(new FileWriter(script))) {
                w.println("#!/bin/sh");
                w.println("sleep 2");
                w.println("mv -f " + shellQuote(replacement.getAbsolutePath())
                        + " " + shellQuote(current.getAbsolutePath()));
                w.println("java -jar " + shellQuote(current.getAbsolutePath()) + " &");
                w.println("rm -- \"$0\"");
            }
            script.setExecutable(true);
            new ProcessBuilder("/bin/sh", script.getAbsolutePath()).start();
        }
    }

    /** Single-quotes a path for use in a POSIX shell script, escaping any embedded single-quotes. */
    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}