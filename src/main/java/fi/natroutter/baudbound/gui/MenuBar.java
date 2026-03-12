package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.BaudBound;

import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.program.ProgramsDialog;
import fi.natroutter.baudbound.gui.dialog.webhook.WebhooksDialog;
import imgui.ImGui;

public class MenuBar {

    private final AboutDialog aboutDialog = BaudBound.getAboutDialog();
    private final SettingsDialog settingsDialog = BaudBound.getSettingsDialog();
    private final WebhooksDialog webhooksDialog = BaudBound.getWebhooksDialog();
    private final ProgramsDialog programDialog = BaudBound.getProgramsDialog();


    public void render() {
        if (ImGui.beginMenuBar()) {
            if (ImGui.menuItem("Settings")) {
                settingsDialog.show();
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
