package fi.natroutter.baudbound.system;

import fi.natroutter.baudbound.storage.StorageProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Registers or unregisters the app in the OS startup mechanism so it launches
 * automatically when the user logs in.
 *
 * <ul>
 *   <li>Windows — {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run} via PowerShell,
 *       invokes {@code javaw.exe -jar BaudBound.jar} (no console window)</li>
 *   <li>macOS — {@code ~/Library/LaunchAgents/<id>.plist}</li>
 *   <li>Linux — {@code ~/.config/autostart/BaudBound.desktop}</li>
 * </ul>
 */
public class StartupManager {

    private static final String APP_NAME = "BaudBound";
    private static final String PLIST_ID = "fi.natroutter.baudbound";

    /**
     * Returns true if the app is currently registered to start with the OS.
     */
    public static boolean isEnabled() {
        String os = os();
        return switch (os) {
            case "windows" -> isEnabledWindows();
            case "mac"     -> isEnabledMac();
            case "linux"   -> isEnabledLinux();
            default        -> false;
        };
    }

    /**
     * Registers or unregisters the app from OS startup.
     *
     * @param enable true to register, false to remove the startup entry
     * @throws Exception if the OS operation fails or the JAR path cannot be resolved
     */
    public static void setEnabled(boolean enable) throws Exception {
        String os = os();
        switch (os) {
            case "windows" -> { if (enable) enableWindows(); else disableWindows(); }
            case "mac"     -> { if (enable) enableMac();     else disableMac();     }
            case "linux"   -> { if (enable) enableLinux();   else disableLinux();   }
        }
    }

    // -------------------------------------------------------------------------
    // Windows — HKCU\Software\Microsoft\Windows\CurrentVersion\Run
    // -------------------------------------------------------------------------

    private static boolean isEnabledWindows() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", APP_NAME
            });
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void enableWindows() throws Exception {
        String java = javaExecutable().replace('/', '\\');
        // Use javaw.exe to suppress the console window on Windows.
        if (java.endsWith("java.exe")) java = java.replace("java.exe", "javaw.exe");
        String jar = jarPath().replace('/', '\\');
        String value = "\"" + java + "\" -jar \"" + jar + "\"";

        // Use PowerShell — reg.exe mis-parses /d values that contain embedded quotes.
        // PowerShell single-quoted strings pass the value verbatim (escape ' as '').
        String psScript = "Set-ItemProperty " +
                "-Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' " +
                "-Name '" + APP_NAME + "' " +
                "-Value '" + value.replace("'", "''") + "'";

        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psScript);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new Exception(output.strip());
    }

    private static void disableWindows() throws Exception {
        if (!isEnabledWindows()) return;
        ProcessBuilder pb = new ProcessBuilder(
                "reg", "delete",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", APP_NAME,
                "/f"
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new Exception("reg delete failed: " + output.strip());
    }

    // -------------------------------------------------------------------------
    // macOS — ~/Library/LaunchAgents/<id>.plist
    // -------------------------------------------------------------------------

    private static Path plistPath() {
        return Paths.get(System.getProperty("user.home"), "Library", "LaunchAgents", PLIST_ID + ".plist");
    }

    private static boolean isEnabledMac() {
        return Files.exists(plistPath());
    }

    private static void enableMac() throws Exception {
        String java = javaExecutable();
        String jar  = jarPath();
        String plist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                        <string>%s</string>
                        <string>-jar</string>
                        <string>%s</string>
                    </array>
                    <key>RunAtLoad</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(PLIST_ID, java, jar);

        Path path = plistPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, plist);
    }

    private static void disableMac() throws Exception {
        Files.deleteIfExists(plistPath());
    }

    // -------------------------------------------------------------------------
    // Linux — ~/.config/autostart/<name>.desktop
    // -------------------------------------------------------------------------

    private static Path desktopPath() {
        String configHome = System.getenv("XDG_CONFIG_HOME");
        if (configHome == null || configHome.isBlank()) {
            configHome = System.getProperty("user.home") + "/.config";
        }
        return Paths.get(configHome, "autostart", APP_NAME + ".desktop");
    }

    private static boolean isEnabledLinux() {
        return Files.exists(desktopPath());
    }

    private static void enableLinux() throws Exception {
        String exec = "\"" + javaExecutable() + "\" -jar \"" + jarPath() + "\"";
        String icon = new File(StorageProvider.getConfigDir(), "icon.png").getAbsolutePath();
        String desktop = """
                [Desktop Entry]
                Type=Application
                Name=%s
                Exec=%s
                Icon=%s
                Hidden=false
                X-GNOME-Autostart-enabled=true
                """.formatted(APP_NAME, exec, icon);

        Path path = desktopPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, desktop);
    }

    private static void disableLinux() throws Exception {
        Files.deleteIfExists(desktopPath());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String os() {
        String name = System.getProperty("os.name").toLowerCase();
        if (name.contains("win"))   return "windows";
        if (name.contains("mac"))   return "mac";
        if (name.contains("linux")) return "linux";
        return "unknown";
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }

    private static String jarPath() throws Exception {
        return new File(StartupManager.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
                .getAbsolutePath();
    }
}
