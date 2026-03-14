package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.BaudBound;

import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.StatesDialog;
import fi.natroutter.baudbound.gui.dialog.program.ProgramsDialog;
import fi.natroutter.baudbound.gui.dialog.webhook.WebhooksDialog;
import imgui.ImGui;

/**
 * Renders the ImGui menu bar embedded in the main window.
 * <p>
 * Menu items delegate directly to the relevant singleton dialog's {@code show()} method.
 * Must be called between {@code ImGui.begin} / {@code ImGui.end} with
 * {@link imgui.flag.ImGuiWindowFlags#MenuBar} set on the parent window.
 */
public class MenuBar {

    private final AboutDialog aboutDialog = BaudBound.getAboutDialog();
    private final SettingsDialog settingsDialog = BaudBound.getSettingsDialog();
    private final WebhooksDialog webhooksDialog = BaudBound.getWebhooksDialog();
    private final ProgramsDialog programDialog = BaudBound.getProgramsDialog();
    private final StatesDialog statesDialog = BaudBound.getStatesDialog();


    public void render() {
        if (ImGui.beginMenuBar()) {
            if (ImGui.menuItem("Settings")) {
                settingsDialog.show();
            }

            if (ImGui.menuItem("States")) {
                statesDialog.show();
            }

            if (ImGui.beginMenu("Actions")) {
                if (ImGui.menuItem("Webhooks")) {
                    webhooksDialog.show();
                }
                if (ImGui.menuItem("Programs")) {
                    programDialog.show();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("About")) {
                    aboutDialog.show();
                }
                ImGui.endMenu();
            }
            ImGui.endMenuBar();
        }
    }

}
