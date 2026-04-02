package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.windows.DevicesWindow;
import fi.natroutter.baudbound.gui.windows.EventsWindow;
import fi.natroutter.baudbound.gui.windows.DocumentationWindow;
import fi.natroutter.baudbound.gui.windows.LogsWindow;
import fi.natroutter.baudbound.gui.windows.ProgramsWindow;
import fi.natroutter.baudbound.gui.windows.SimulateWindow;
import fi.natroutter.baudbound.gui.windows.StatesWindow;
import fi.natroutter.baudbound.gui.windows.WebSocketWindow;
import fi.natroutter.baudbound.gui.windows.WebhooksWindow;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.system.UpdateManager;
import fi.natroutter.foxlib.updates.data.UpdateStatus;
import imgui.ImGui;

/**
 * Renders the standalone ImGui main menu bar via {@code ImGui.beginMainMenuBar()}.
 * <p>
 * Panel windows (Events, Devices, Webhooks, etc.) are toggled open/closed via checkmark
 * menu items. Modal dialogs (Settings, About) are opened directly. Must be called from
 * the GLFW main thread each frame.
 */
public class MenuBar {

    private final EventsWindow    eventsWindow    = BaudBound.getEventsWindow();
    private final AboutDialog     aboutDialog     = BaudBound.getAboutDialog();
    private final SettingsDialog  settingsDialog  = BaudBound.getSettingsDialog();
    private final DevicesWindow   devicesWindow   = BaudBound.getDevicesWindow();
    private final WebSocketWindow webSocketWindow = BaudBound.getWebSocketWindow();
    private final WebhooksWindow  webhooksWindow  = BaudBound.getWebhooksWindow();
    private final ProgramsWindow  programsWindow  = BaudBound.getProgramsWindow();
    private final StatesWindow    statesWindow    = BaudBound.getStatesWindow();
    private final SimulateWindow  simulateWindow  = BaudBound.getSimulateWindow();
    private final LogsWindow           logsWindow           = BaudBound.getLogsWindow();
    private final DocumentationWindow  documentationWindow  = BaudBound.getDocumentationWindow();
    private final StorageProvider      storage              = BaudBound.getStorageProvider();

    private volatile boolean checkingForUpdates = false;

    public void render() {
        if (ImGui.beginMainMenuBar()) {

            if (ImGui.menuItem("Events", "", eventsWindow.isOpen())) {
                eventsWindow.toggle();
            }

            if (ImGui.menuItem("Settings")) {
                settingsDialog.show();
            }

            if (ImGui.beginMenu("Triggers")) {
                if (ImGui.menuItem("Serial Devices", "", devicesWindow.isOpen())) {
                    devicesWindow.toggle();
                }
                if (ImGui.menuItem("WebSocket", "", webSocketWindow.isOpen())) {
                    webSocketWindow.toggle();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Actions")) {
                if (ImGui.menuItem("Webhooks", "", webhooksWindow.isOpen())) {
                    webhooksWindow.toggle();
                }
                if (ImGui.menuItem("Programs", "", programsWindow.isOpen())) {
                    programsWindow.toggle();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Debug")) {
                if (ImGui.menuItem("Simulate", "", simulateWindow.isOpen())) {
                    simulateWindow.toggle();
                }
                if (ImGui.menuItem("Logs", "", logsWindow.isOpen())) {
                    logsWindow.toggle();
                }
                if (ImGui.menuItem("States", "", statesWindow.isOpen())) {
                    statesWindow.toggle();
                }

                boolean overlayEnabled = storage.getData().getSettings().getDebug().isOverlay();
                if (ImGui.menuItem("Debug Overlay", "", overlayEnabled)) {
                    storage.getData().getSettings().getDebug().setOverlay(!overlayEnabled);
                    storage.save();
                }

                ImGui.separator();
                if (ImGui.menuItem("Stop All Sounds")) {
                    BaudBound.getEventHandler().stopAllSounds();
                }

                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("Documentation", "", documentationWindow.isOpen())) {
                    documentationWindow.toggle();
                }
                ImGui.separator();
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

            ImGui.endMainMenuBar();
        }
    }
}
