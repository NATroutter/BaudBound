package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.foxlib.updates.GitHubVersionChecker;
import fi.natroutter.foxlib.updates.data.UpdateStatus;
import fi.natroutter.foxlib.updates.data.VersionInfo;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.helpers.GuiHelper;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.io.IOException;


public class AboutDialog {

    private FoxLogger logger = BaudBound.getLogger();

    private static final String AUTHOR = "NATroutter";
    private static final String REPO_OWNER = "NATroutter"; // Your GitHub username
    private static final String REPO_NAME = "BaudBound"; // Your repo name
    private static final String GITHUB = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;

    private VersionInfo versionInfo = null;
    private boolean checkingForUpdates = false;
    private String updateStatus = "Checking for updates...";

    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);


    public void show() {
        this.open = true;
        checkForUpdates();
    }

    private void checkForUpdates() {
        if (!checkingForUpdates && versionInfo == null) {
            checkingForUpdates = true;
            updateStatus = "Checking for updates...";

//            versionInfo = new VersionInfo("0.0.1", "2.0.0", UpdateStatus.YES, "test_name", "test_url", "agageaeigjaigjaeigja\naejiaefhjaejifioaefji\nahauiefhuaefhuaeu", "pub_at");

            GitHubVersionChecker checker = new GitHubVersionChecker(REPO_OWNER, REPO_NAME, BaudBound.VERSION);
            checker.setLogger(BaudBound.getLogger());
            checker.checkForUpdates().thenAccept(info -> {
                versionInfo = info;
                checkingForUpdates = false;
                switch (info.getUpdateAvailable()) {
                    case YES -> updateStatus = "Update available!";
                    case NO ->  updateStatus = "Up to date";
                    case ERROR -> updateStatus = "Connection failed!";
                }
            }).exceptionally(ex -> {
                checkingForUpdates = false;
                updateStatus = "Failed to check for updates";
                logger.error("Update check failed: " + ex.getMessage());
                return null;
            });
        }
    }

    public void render() {
        String popupTitle = "About BaudBound";
        if (open) {
            ImGui.openPopup(popupTitle);
            modalOpen.set(true);
            open = false;
        }

        ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySizeX() / 2,
                ImGui.getIO().getDisplaySizeY() / 2,
                ImGuiCond.Always,
                0.5f, 0.5f
        );

        ImGui.setNextWindowSizeConstraints(ImGui.getIO().getDisplaySizeX() * 0.9f, 0, ImGui.getIO().getDisplaySizeX() * 0.9f, Float.MAX_VALUE);

        boolean wasOpen = ImGui.isPopupOpen(popupTitle);
        if (ImGui.beginPopupModal(popupTitle, modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            // Version information
            ImGui.text("Current Version: " + BaudBound.VERSION);

            if (versionInfo != null) {
                ImGui.text("Latest Version:  " + versionInfo.getLatestVersion());
                ImGui.sameLine();

                switch (versionInfo.getUpdateAvailable()) {
                    case YES -> ImGui.textColored(1.0f, 0.8f, 0.0f, 1.0f, "(Update Available!)");
                    case NO ->  ImGui.textColored(0.0f, 1.0f, 0.0f, 1.0f, "(Up to date)");
                    case ERROR -> ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "(Connection failed!)");
                }

            } else {
                ImGui.text("Latest Version:  " + updateStatus);
            }

            ImGui.text("Build Date: " + BaudBound.BUILD_DATE);
            ImGui.text("Author: " + AUTHOR);
            ImGui.spacing();

            // Description
            ImGui.separator();
            ImGui.spacing();
            ImGui.text("A powerful tool for converting schematics between");
            ImGui.text("different formats with customizable block mappings.");
            ImGui.spacing();

            // Links
            ImGui.text("GitHub: ");
            ImGui.sameLine();
            GuiHelper.renderClickableLink(GITHUB, GITHUB);

            ImGui.text("Website: ");
            ImGui.sameLine();
            GuiHelper.renderClickableLink("https://natroutter.fi", "https://natroutter.fi");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // Features
            ImGui.text("Features:");
            ImGui.bulletText("Convert individual schematics or entire directories");
            ImGui.bulletText("Custom block mapping configurations");
            ImGui.bulletText("Support for multiple mapping presets");
            ImGui.bulletText("Cross-platform compatibility (Windows, Linux, Mac)");
            ImGui.bulletText("Real-time conversion progress tracking");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // System information
            ImGui.text("System Information:");
            ImGui.text("  Java Version: " + System.getProperty("java.version"));
            ImGui.text("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            ImGui.text("  Architecture: " + System.getProperty("os.arch"));

            ImGui.spacing();


            // Release notes (if available and update exists)
            if (versionInfo != null && versionInfo.getUpdateAvailable()==UpdateStatus.YES && !versionInfo.getReleaseNotes().isEmpty()) {
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

            // Buttons
            if (versionInfo != null && versionInfo.getUpdateAvailable() == UpdateStatus.YES) {
                ImGui.separator();
                ImGui.spacing();
                if (ImGui.button("Download Update", ImGui.getContentRegionAvailX(), 0)) {
                    try {
                        FoxLib.openURL(versionInfo.getReleaseUrl());
                    } catch (IOException e) {
                        logger.error("Failed to open URL '" + versionInfo.getReleaseUrl() + "': " + e.getMessage());
                    }
                }
                ImGui.sameLine();
            }

            ImGui.endPopup();
        } else if (wasOpen && !modalOpen.get()) {
            modalOpen.set(true);
        }
    }
}