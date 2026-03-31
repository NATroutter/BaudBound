package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import imgui.ImGui;

/**
 * Modal dialog showing static application information: version, build date, description,
 * developer contact, feature list, and system details.
 * <p>
 * Update checking is handled separately via the Help menu "Check for Updates" item,
 * which uses {@link fi.natroutter.baudbound.system.UpdateManager#checkNow} and opens
 * {@link UpdateDialog} when a new release is found.
 */
public class AboutDialog extends BaseDialog {

    private static final String AUTHOR         = "NATroutter";
    private static final String REPO_OWNER     = "NATroutter";
    private static final String REPO_NAME      = "BaudBound";
    private static final String GITHUB         = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    private static final String AUTHOR_GITHUB  = "https://github.com/" + REPO_OWNER;
    private static final String AUTHOR_WEBSITE = "https://natroutter.fi";

    @Override
    public void render() {
        if (beginModal("About BaudBound")) {

            ImGui.text("Version:    " + BaudBound.VERSION);
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

            endModal();
        }
    }
}