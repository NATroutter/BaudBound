package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.BaudBound;

import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.actions.WebhooksDialog;
import imgui.ImGui;

public class MenuBar {

    private final AboutDialog aboutDialog = BaudBound.getAboutDialog();
    private final SettingsDialog settingsDialog = BaudBound.getSettingsDialog();
    private final WebhooksDialog webhooksDialog = BaudBound.getWebhooksDialog();

    public void render() {
        if (ImGui.beginMenuBar()) {
            if (ImGui.menuItem("Settings")) {
                settingsDialog.show();
            }

            if (ImGui.beginMenu("Actions")) {
                if (ImGui.menuItem("Webhooks")) {
                    webhooksDialog.show();
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
