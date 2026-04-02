package fi.natroutter.baudbound.gui.windows;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.TriggerSource;
import fi.natroutter.baudbound.event.TriggerContext;
import fi.natroutter.baudbound.gui.BaseWindow;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.storage.DataStore;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;

/**
 * Floating panel window for injecting simulated trigger events through the event system.
 * <p>
 * Supports all four {@link TriggerSource} types:
 * <ul>
 *   <li><b>Serial Input</b> — optional device context + arbitrary input string</li>
 *   <li><b>WebSocket</b> — message body + optional channel path</li>
 *   <li><b>Device Connected</b> — fires a device-connected lifecycle event for a chosen device</li>
 *   <li><b>Device Disconnected</b> — fires a device-disconnected lifecycle event for a chosen device</li>
 * </ul>
 * The last fired input and its result are shown as feedback below the Fire button.
 */
public class SimulateWindow extends BaseWindow {

    private final ImInt    selectedSource = new ImInt(0);
    private final ImInt    selectedDevice = new ImInt(0);
    private final ImString fieldInput     = new ImString(256);
    private final ImString fieldChannel   = new ImString(256);

    private String  lastResult  = null;
    private boolean lastSuccess = false;

    @Override
    public void render() {
        if (!open.get()) return;

        ImGui.setNextWindowSize(420, 0, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(300, 0, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("Simulate Input##simulatewindow", open, imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            List<DataStore.Device> devices = BaudBound.getStorageProvider().getData().getDevices();

            String[] sourceNames = TriggerSource.asFriendlyArray();
            ImGui.text("Trigger Source");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo("##sim_source", selectedSource, sourceNames)) {
                lastResult = null;
            }

            ImGui.spacing();

            TriggerSource source = TriggerSource.values()[selectedSource.get()];

            switch (source) {
                case SERIAL -> renderSerial(devices);
                case WEBSOCKET -> renderWebSocket();
                case DEVICE_CONNECTED, DEVICE_DISCONNECTED -> renderDeviceLifecycle(devices);
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            boolean canFire = canFire(source, devices);
            ImGui.beginDisabled(!canFire);
            if (ImGui.button("Fire", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                fire(source, devices);
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
        }
        ImGui.end();
    }

    private void renderSerial(List<DataStore.Device> devices) {
        String[] deviceNames = new String[devices.size() + 1];
        deviceNames[0] = "None";
        for (int i = 0; i < devices.size(); i++) deviceNames[i + 1] = devices.get(i).getName();
        if (selectedDevice.get() > devices.size()) selectedDevice.set(0);

        ImGui.text("Device");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.combo("##sim_device", selectedDevice, deviceNames);

        ImGui.spacing();
        ImGui.text("Input");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputText("##sim_input", fieldInput);
    }

    private void renderWebSocket() {
        ImGui.text("Message");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputText("##sim_input", fieldInput);

        ImGui.spacing();
        ImGui.text("Channel");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##sim_channel", "/  (default)", fieldChannel);
    }

    private void renderDeviceLifecycle(List<DataStore.Device> devices) {
        if (devices.isEmpty()) {
            ImGui.textDisabled("No devices configured.");
            return;
        }
        String[] deviceNames = devices.stream().map(DataStore.Device::getName).toArray(String[]::new);
        if (selectedDevice.get() >= devices.size()) selectedDevice.set(0);

        ImGui.text("Device");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.combo("##sim_device", selectedDevice, deviceNames);
    }

    private boolean canFire(TriggerSource source, List<DataStore.Device> devices) {
        return switch (source) {
            case SERIAL    -> !fieldInput.get().isBlank();
            case WEBSOCKET -> !fieldInput.get().isBlank();
            case DEVICE_CONNECTED, DEVICE_DISCONNECTED -> !devices.isEmpty();
        };
    }

    private void fire(TriggerSource source, List<DataStore.Device> devices) {
        TriggerContext ctx;
        String label;

        switch (source) {
            case SERIAL -> {
                DataStore.Device device = selectedDevice.get() == 0 ? null : devices.get(selectedDevice.get() - 1);
                String input = fieldInput.get().trim();
                String deviceTag = device != null ? " as device \"" + device.getName() + "\"" : "";
                BaudBound.getLogger().info("GUI: simulating serial input \"" + input + "\"" + deviceTag);
                ctx = TriggerContext.serial(input, device);
                label = "Fired serial: \"" + input + "\"" + deviceTag;
            }
            case WEBSOCKET -> {
                String message = fieldInput.get().trim();
                String channel = fieldChannel.get().trim();
                if (channel.isEmpty()) channel = "/";
                BaudBound.getLogger().info("GUI: simulating WebSocket message \"" + message + "\" on channel " + channel);
                ctx = TriggerContext.webSocket(message, channel, null);
                label = "Fired WebSocket: \"" + message + "\"  channel=" + channel;
            }
            case DEVICE_CONNECTED -> {
                DataStore.Device device = devices.get(selectedDevice.get());
                BaudBound.getLogger().info("GUI: simulating device connected \"" + device.getName() + "\"");
                ctx = TriggerContext.deviceConnected(device);
                label = "Fired device connected: \"" + device.getName() + "\"";
            }
            case DEVICE_DISCONNECTED -> {
                DataStore.Device device = devices.get(selectedDevice.get());
                BaudBound.getLogger().info("GUI: simulating device disconnected \"" + device.getName() + "\"");
                ctx = TriggerContext.deviceDisconnected(device);
                label = "Fired device disconnected: \"" + device.getName() + "\"";
            }
            default -> { return; }
        }

        TriggerContext finalCtx = ctx;
        Thread.ofVirtual().start(() -> BaudBound.getEventHandler().process(finalCtx));
        lastResult  = label;
        lastSuccess = true;
    }
}
