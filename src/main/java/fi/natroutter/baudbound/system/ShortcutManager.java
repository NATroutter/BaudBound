package fi.natroutter.baudbound.system;

import fi.natroutter.baudbound.BaudBound;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Creates OS shortcuts pointing to the running JAR:
 * <ul>
 *   <li>Windows  — {@code BaudBound.lnk} via PowerShell WScript.Shell</li>
 *   <li>macOS    — {@code BaudBound.command} double-clickable shell script</li>
 *   <li>Linux    — {@code BaudBound.desktop} freedesktop entry</li>
 * </ul>
 */
public class ShortcutManager {

    /**
     * Returns a sensible default folder to open the folder picker on:
     * <ul>
     *   <li>Windows — Start Menu Programs folder in %APPDATA%</li>
     *   <li>macOS / Linux — Desktop (respects XDG_DESKTOP_DIR on Linux)</li>
     * </ul>
     */
    public static String defaultFolderPath() {
        String os = os();
        if (os.equals("windows")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return appData + "\\Microsoft\\Windows\\Start Menu\\Programs";
            }
        } else {
            // Linux: respect XDG_DESKTOP_DIR if set
            String xdgDesktop = System.getenv("XDG_DESKTOP_DIR");
            if (xdgDesktop != null && !xdgDesktop.isBlank()) return xdgDesktop;

            String desktop = System.getProperty("user.home") + File.separator + "Desktop";
            if (new File(desktop).isDirectory()) return desktop;
        }
        return System.getProperty("user.home");
    }

    /**
     * Creates a shortcut in {@code targetFolder} pointing to the running JAR.
     *
     * @param targetFolder absolute path to the folder where the shortcut is placed
     * @throws Exception if creation fails or the JAR path cannot be resolved
     */
    public static void createShortcut(String targetFolder) throws Exception {
        switch (os()) {
            case "windows" -> createShortcutWindows(targetFolder);
            case "mac"     -> createShortcutMac(targetFolder);
            default        -> createShortcutLinux(targetFolder);
        }
    }

    // -------------------------------------------------------------------------
    // Windows — .lnk via PowerShell WScript.Shell
    // -------------------------------------------------------------------------

    private static void createShortcutWindows(String targetFolder) throws Exception {
        String javaExe = javaExecutable().replace('/', '\\');
        if (javaExe.endsWith("java.exe")) {
            javaExe = javaExe.replace("java.exe", "javaw.exe");
        }

        String jarFile = jarPath().replace('/', '\\');
        String workDir = new File(jarFile).getParent();
        String lnkPath = targetFolder + "\\" + BaudBound.APP_NAME + ".lnk";

        String iconPath = extractIcon("icon.ico", ".ico").replace('/', '\\');

        String psScript = String.join(";",
                "$s = (New-Object -ComObject WScript.Shell).CreateShortcut('" + psEscape(lnkPath) + "')",
                "$s.TargetPath = '"         + psEscape(javaExe) + "'",
                "$s.Arguments = '-jar \"" + jarFile.replace("'", "''") + "\"'",
                "$s.WorkingDirectory = '"  + psEscape(workDir != null ? workDir : "") + "'",
                "$s.Description = '"       + psEscape(BaudBound.APP_NAME) + "'",
                "$s.IconLocation = '"      + psEscape(iconPath) + "'",
                "$s.Save()"
        );

        runProcess("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript);
    }

    // -------------------------------------------------------------------------
    // macOS — .command double-clickable shell script
    // -------------------------------------------------------------------------

    private static void createShortcutMac(String targetFolder) throws Exception {
        String java  = javaExecutable();
        String jar   = jarPath();
        Path   dest  = Path.of(targetFolder, BaudBound.APP_NAME + ".command");

        String script = "#!/bin/bash\n"
                + "exec " + shQuote(java) + " -jar " + shQuote(jar) + "\n";

        Files.writeString(dest, script);
        Files.setPosixFilePermissions(dest, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    // -------------------------------------------------------------------------
    // Linux — .desktop freedesktop entry
    // -------------------------------------------------------------------------

    private static void createShortcutLinux(String targetFolder) throws Exception {
        String java = javaExecutable();
        String jar  = jarPath();
        Path   dest = Path.of(targetFolder, BaudBound.APP_NAME + ".desktop");

        String iconPath = extractIcon("icon.png", ".png");

        String desktop = "[Desktop Entry]\n"
                + "Version=1.0\n"
                + "Type=Application\n"
                + "Name=" + BaudBound.APP_NAME + "\n"
                + "Comment=Serial port event mapper\n"
                + "Exec=" + java + " -jar " + jar + "\n"
                + "Icon=" + iconPath + "\n"
                + "Terminal=false\n"
                + "Categories=Utility;\n";

        Files.writeString(dest, desktop);
        Files.setPosixFilePermissions(dest, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts an icon resource from the JAR to a file next to the JAR,
     * returning its absolute path. Skips extraction if the file already exists.
     */
    private static String extractIcon(String resource, String extension) throws Exception {
        File jar = new File(jarPath());
        File iconFile = new File(jar.getParent(), BaudBound.APP_NAME + extension);
        if (!iconFile.exists()) {
            try (InputStream is = ShortcutManager.class.getResourceAsStream("/" + resource)) {
                if (is == null) throw new Exception("Icon resource not found: " + resource);
                Files.copy(is, iconFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return iconFile.getAbsolutePath();
    }

    private static String os() {
        String name = System.getProperty("os.name").toLowerCase();
        if (name.contains("win"))   return "windows";
        if (name.contains("mac"))   return "mac";
        return "linux";
    }

    /** Escapes a value for use inside a PowerShell single-quoted string. */
    private static String psEscape(String value) {
        return value.replace("'", "''");
    }

    /** Wraps a path in single quotes for POSIX shell, escaping embedded single quotes. */
    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void runProcess(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new Exception(output.isBlank() ? "Process returned non-zero exit code." : output.strip());
        }
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }

    private static String jarPath() throws Exception {
        return new File(ShortcutManager.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
                .getAbsolutePath();
    }
}