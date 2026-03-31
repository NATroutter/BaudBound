package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.storage.DataStore;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;

/**
 * Modal dialog for injecting a simulated serial input line through the event system.
 * <p>
 * The user picks an optional device context from a combo box, types an input string,
 * and clicks "Fire" to pass the input through {@link fi.natroutter.baudbound.event.EventHandler#process}.
 * The last fired input and its device context are shown as feedback below the button.
 */
public class SimulateDialog extends BaseDialog {

    private final ImString input         = new ImString(256);
    private final ImInt    selectedDevice = new ImInt(0);

    private String  lastResult  = null;
    private boolean lastSuccess = false;

    @Override
    public void render() {
        if (beginModal("Simulate Input")) {
            List<DataStore.Device> devices = BaudBound.getStorageProvider().getData().getDevices();

            // Build device name list: index 0 = None, then configured devices
            String[] deviceNames = new String[devices.size() + 1];
            deviceNames[0] = "None";
            for (int i = 0; i < devices.size(); i++) {
                deviceNames[i + 1] = devices.get(i).getName();
            }

            // Clamp selection in case devices were removed while dialog is open
            if (selectedDevice.get() > devices.size()) selectedDevice.set(0);

            ImGui.text("Device");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##sim_device", selectedDevice, deviceNames);

            ImGui.spacing();
            ImGui.text("Input");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##sim_input", input);

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            boolean empty = input.get().isBlank();
            ImGui.beginDisabled(empty);
            if (ImGui.button("Fire", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                DataStore.Device device = selectedDevice.get() == 0 ? null : devices.get(selectedDevice.get() - 1);
                String inputStr = input.get().trim();
                String deviceTag = device != null ? " as device \"" + device.getName() + "\"" : "";
                BaudBound.getLogger().info("GUI: simulating input \"" + inputStr + "\"" + deviceTag, true);
                Thread.ofVirtual().start(() -> BaudBound.getEventHandler().process(inputStr, device));
                lastResult  = "Fired: \"" + inputStr + "\"" + deviceTag;
                lastSuccess = true;
            }
            ImGui.endDisabled();

            if (lastResult != null) {
                ImGui.spacing();
                if (lastSuccess) {
                    ImGui.textColored(0.45f, 0.9f, 0.45f, 1.0f, lastResult);
                } else {
                    ImGui.textColored(1.0f, 0.35f, 0.35f, 1.0f, lastResult);
                }
            }

            endModal();
        }
    }
}