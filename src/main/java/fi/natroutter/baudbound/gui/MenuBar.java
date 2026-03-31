package fi.natroutter.baudbound.gui;

import fi.natroutter.foxlib.updates.data.UpdateStatus;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.SimulateDialog;
import fi.natroutter.baudbound.gui.dialog.StatesDialog;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.dialog.device.DevicesDialog;
import fi.natroutter.baudbound.gui.dialog.program.ProgramsDialog;
import fi.natroutter.baudbound.gui.dialog.webhook.WebhooksDialog;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.system.UpdateManager;
import imgui.ImGui;

/**
 * Renders the ImGui menu bar embedded in the main window.
 * <p>
 * Menu items delegate directly to the relevant singleton dialog's {@code show()} method.
 * Must be called between {@code ImGui.begin} / {@code ImGui.end} with
 * {@link imgui.flag.ImGuiWindowFlags#MenuBar} set on the parent window.
 */
public class MenuBar {

    private final AboutDialog aboutDialog       = BaudBound.getAboutDialog();
    private final SettingsDialog settingsDialog = BaudBound.getSettingsDialog();
    private final DevicesDialog devicesDialog   = BaudBound.getDevicesDialog();
    private final WebhooksDialog webhooksDialog = BaudBound.getWebhooksDialog();
    private final ProgramsDialog programDialog  = BaudBound.getProgramsDialog();
    private final StatesDialog statesDialog       = BaudBound.getStatesDialog();
    private final SimulateDialog simulateDialog   = BaudBound.getSimulateDialog();
    private final StorageProvider storage         = BaudBound.getStorageProvider();

    private volatile boolean checkingForUpdates = false;


    public void render() {
        if (ImGui.beginMenuBar()) {
            if (ImGui.menuItem("Settings")) {
                settingsDialog.show();
            }

            if (ImGui.menuItem("Devices")) {
                devicesDialog.show();
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

            if (ImGui.beginMenu("Debug")) {

                if (ImGui.menuItem("Simulate")) {
                    simulateDialog.show();
                }

                if (ImGui.menuItem("Logs")) {
                    BaudBound.getLogsDialog().show();
                }

                if (ImGui.menuItem("States")) {
                    statesDialog.show();
                }

                boolean overlayEnabled = storage.getData().getSettings().getDebug().isOverlay();
                if (ImGui.menuItem("Debug Overlay", "", overlayEnabled)) {
                    storage.getData().getSettings().getDebug().setOverlay(!overlayEnabled);
                    storage.save();
                }

                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("About")) {
                    aboutDialog.show();
                }
                ImGui.separator();
                ImGui.beginDisabled(checkingForUpdates);
                if (ImGui.menuItem(checkingForUpdates ? "Checking..." : "Check for Updates")) {
                    checkingForUpdates = true;
                    UpdateManager.checkNow(
                        info -> {
                            checkingForUpdates = false;
                            if (info.getUpdateAvailable() == UpdateStatus.YES) {
                                BaudBound.getUpdateDialog().openWith(info);
                            } else {
                                BaudBound.getMessageDialog().show("Up to Date",
                                        "BaudBound " + BaudBound.VERSION + " is the latest version.",
                                        new DialogButton("OK", () -> {}));
                            }
                        },
                        err -> {
                            checkingForUpdates = false;
                            BaudBound.getMessageDialog().show("Check Failed",
                                    "Could not check for updates:\n" + err,
                                    new DialogButton("OK", () -> {}));
                        }
                    );
                }
                ImGui.endDisabled();
                ImGui.endMenu();
            }
            ImGui.endMenuBar();
        }
    }

}