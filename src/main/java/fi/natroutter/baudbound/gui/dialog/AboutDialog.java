package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.updates.GitHubVersionChecker;
import fi.natroutter.foxlib.updates.data.UpdateStatus;
import fi.natroutter.foxlib.updates.data.VersionInfo;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import imgui.ImGui;

import java.io.IOException;


public class AboutDialog extends BaseDialog {

    private static final String AUTHOR = "NATroutter";
    private static final String REPO_OWNER = "NATroutter";
    private static final String REPO_NAME = "BaudBound";
    private static final String GITHUB = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    private static final String AUTHOR_GITHUB = "https://github.com/" + REPO_OWNER;
    private static final String AUTHOR_WEBSITE = "https://natroutter.fi";

    private VersionInfo versionInfo = null;
    private boolean checkingForUpdates = false;
    private String updateStatus = "Checking for updates...";

    @Override
    public void show() {
        checkForUpdates();
        requestOpen();
    }

    private void checkForUpdates() {
        if (!checkingForUpdates && versionInfo == null) {
            checkingForUpdates = true;
            updateStatus = "Checking for updates...";

            GitHubVersionChecker checker = new GitHubVersionChecker(REPO_OWNER, REPO_NAME, BaudBound.VERSION);
            checker.setLogger(BaudBound.getLogger());
            checker.checkForUpdates().thenAccept(info -> {
                versionInfo = info;
                checkingForUpdates = false;
                updateStatus = switch (info.getUpdateAvailable()) {
                    case YES   -> "Update available!";
                    case NO    -> "Up to date";
                    case ERROR -> "Connection failed!";
                };
            }).exceptionally(ex -> {
                checkingForUpdates = false;
                updateStatus = "Failed to check for updates";
                BaudBound.getLogger().error("Update check failed: " + ex.getMessage());
                return null;
            });
        }
    }

    @Override
    public void render() {
        if (beginModal("About BaudBound")) {

            ImGui.text("Current Version: " + BaudBound.VERSION);

            if (versionInfo != null) {
                ImGui.text("Latest Version:  " + versionInfo.getLatestVersion());
                ImGui.sameLine();
                switch (versionInfo.getUpdateAvailable()) {
                    case YES   -> ImGui.textColored(1.0f, 0.8f, 0.0f, 1.0f, "(Update Available!)");
                    case NO    -> ImGui.textColored(0.0f, 1.0f, 0.0f, 1.0f, "(Up to date)");
                    case ERROR -> ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "(Connection failed!)");
                }
            } else {
                ImGui.text("Latest Version:  " + updateStatus);
            }

            ImGui.text("Build Date: " + BaudBound.BUILD_DATE);
            ImGui.spacing();

            ImGui.separator();
            ImGui.spacing();
            ImGui.textWrapped("BaudBound listens to a serial port and fires configurable actions whenever incoming data matches your conditions. Hook up any serial device — a microcontroller, a barcode scanner, a custom sensor — and turn its output into webhooks, launched programs, opened URLs, or simulated keystrokes.");
            ImGui.spacing();

            ImGui.text("GitHub: ");
            ImGui.sameLine();
            GuiHelper.clickableLink(GITHUB, GITHUB);

            ImGui.text("Website:");
            ImGui.sameLine();
            GuiHelper.clickableLink("https://baudbound.app", "https://baudbound.app");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text("Developer:");
            ImGui.text("Name:    " + AUTHOR);
            ImGui.text("GitHub: ");
            ImGui.sameLine();
            GuiHelper.clickableLink(AUTHOR_GITHUB, AUTHOR_GITHUB);
            ImGui.text("Website:");
            ImGui.sameLine();
            GuiHelper.clickableLink(AUTHOR_WEBSITE, AUTHOR_WEBSITE);

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text("Features:");
            ImGui.bullet(); ImGui.textWrapped("Trigger actions when serial input matches configurable conditions");
            ImGui.bullet(); ImGui.textWrapped("Conditions: starts/ends with, contains, equals, regex, numeric comparisons, and more");
            ImGui.bullet(); ImGui.textWrapped("Actions: webhooks, open URL/program, type text, clipboard, notifications, write/append to file, play sound");
            ImGui.bullet(); ImGui.textWrapped("Multiple conditions and actions per event");
            ImGui.bullet(); ImGui.textWrapped("Event ordering controls and per-event enable/disable");
            ImGui.bullet(); ImGui.textWrapped("System tray support with auto-connect and start hidden");
            ImGui.bullet(); ImGui.textWrapped("Cross-platform (Windows, Linux, macOS)");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text("System Information:");
            ImGui.text("  Java Version: " + System.getProperty("java.version"));
            ImGui.text("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            ImGui.text("  Architecture: " + System.getProperty("os.arch"));

            ImGui.spacing();

            if (versionInfo != null && versionInfo.getUpdateAvailable() == UpdateStatus.YES && !versionInfo.getReleaseNotes().isEmpty()) {
                ImGui.separator();
                ImGui.spacing();
                ImGui.text("Release Notes (" + versionInfo.getLatestVersion() + "):");
                ImGui.beginDisabled();
                String[] lines = versionInfo.getReleaseNotes().split("\n");
                int lineCount = 0;
                for (String line : lines) {
                    if (lineCount++ > 10) {
                        ImGui.text("... (see GitHub for full notes)");
                        break;
                    }
                    ImGui.textWrapped(line);
                }
                ImGui.endDisabled();
                ImGui.spacing();
            }

            if (versionInfo != null && versionInfo.getUpdateAvailable() == UpdateStatus.YES) {
                ImGui.separator();
                ImGui.spacing();
                if (ImGui.button("Download Update", ImGui.getContentRegionAvailX(), 0)) {
                    try {
                        FoxLib.openURL(versionInfo.getReleaseUrl());
                    } catch (IOException e) {
                        BaudBound.getLogger().error("Failed to open URL '" + versionInfo.getReleaseUrl() + "': " + e.getMessage());
                    }
                }
            }

            endModal();
        }
    }
}