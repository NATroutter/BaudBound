package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.system.UpdateManager;
import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.updates.data.UpdateStatus;
import fi.natroutter.foxlib.updates.data.VersionInfo;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Console command for checking and installing application updates.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code update} / {@code update check} — fetch the latest GitHub release and show a version comparison</li>
 *   <li>{@code update install} — check, then download the new JAR and restart the process</li>
 * </ul>
 */
public class UpdateCommand extends Command {

    public UpdateCommand() {
        super("update", "Check for or install application updates  (usage: update [check|install])");
    }

    @Override
    public void execute(String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "check";
        switch (sub) {
            case "check"   -> handleCheck();
            case "install" -> handleInstall();
            default -> FoxLib.println("  {BRIGHT_RED}Unknown subcommand: \"" + args[0] + "\"{RESET}  —  usage: {BRIGHT_YELLOW}update [check|install]{RESET}");
        }
    }

    // -------------------------------------------------------------------------

    private void handleCheck() {
        FoxLib.println("  {CYAN}Checking for updates...{RESET}");
        VersionInfo info = fetchVersionInfo();
        if (info == null) return;
        showVersionBox(info);
        if (info.getUpdateAvailable() == UpdateStatus.YES) {
            FoxLib.println("  Run {BRIGHT_YELLOW}update install{RESET} to download and restart.");
        }
    }

    private void handleInstall() {
        Optional<File> jar = UpdateManager.getJarFile();
        if (jar.isEmpty()) {
            FoxLib.println("  {BRIGHT_RED}Cannot update: not running from a JAR file.{RESET}");
            return;
        }

        FoxLib.println("  {CYAN}Checking for updates...{RESET}");
        VersionInfo info = fetchVersionInfo();
        if (info == null) return;

        showVersionBox(info);

        if (info.getUpdateAvailable() != UpdateStatus.YES) {
            FoxLib.println("  {BRIGHT_GREEN}Already up to date — nothing to install.{RESET}");
            return;
        }

        FoxLib.println("  {CYAN}Starting download...{RESET}");
        String url = UpdateManager.buildJarDownloadUrl(info.getReleaseUrl());
        UpdateManager.downloadAndRestart(url, jar.get(),
                (downloaded, total) -> {
                    String progress = total > 0
                            ? String.format("%d%%  (%s / %s)", downloaded * 100 / total, formatBytes(downloaded), formatBytes(total))
                            : formatBytes(downloaded);
                    FoxLib.print("\r  {CYAN}Downloading...{RESET}  {BRIGHT_WHITE}" + progress + "{RESET}   ");
                },
                err -> FoxLib.println("\n  {BRIGHT_RED}Download failed: " + err + "{RESET}")
        );
    }

    // -------------------------------------------------------------------------

    /**
     * Calls {@link UpdateManager#checkNow} and blocks the current thread until a result
     * arrives or the 20-second timeout expires.
     *
     * @return the {@link VersionInfo} on success, or {@code null} on timeout/error
     */
    private VersionInfo fetchVersionInfo() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<VersionInfo> result = new AtomicReference<>();
        AtomicReference<String> error  = new AtomicReference<>();

        UpdateManager.checkNow(
                info -> { result.set(info); latch.countDown(); },
                err  -> { error.set(err);   latch.countDown(); }
        );

        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                FoxLib.println("  {BRIGHT_RED}Update check timed out.{RESET}");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FoxLib.println("  {BRIGHT_RED}Update check interrupted.{RESET}");
            return null;
        }

        if (error.get() != null) {
            FoxLib.println("  {BRIGHT_RED}Update check failed: " + error.get() + "{RESET}");
            return null;
        }

        return result.get();
    }

    private void showVersionBox(VersionInfo info) {
        String statusColor = info.getUpdateAvailable() == UpdateStatus.YES ? "{BRIGHT_GREEN}" : (info.getUpdateAvailable() == UpdateStatus.NO ? "{CYAN}" : "{BRIGHT_RED}");
        String statusText  = switch (info.getUpdateAvailable()) {
            case YES   -> "Update available";
            case NO    -> "Up to date";
            case ERROR -> "Check failed";
        };

        ConsoleUI.printBox("Update Check", List.of(
                "{BLUE}Current{WHITE} :{RESET}  {CYAN}" + info.getCurrentVersion(),
                "{BLUE}Latest {WHITE} :{RESET}  {CYAN}" + info.getLatestVersion(),
                "{BLUE}Status {WHITE} :{RESET}  " + statusColor + statusText
        ));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}