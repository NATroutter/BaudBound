package fi.natroutter.baudbound.gui.dialog;

import com.fazecast.jSerialComm.SerialPort;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.helpers.GuiHelper;
import fi.natroutter.baudbound.serial.SerialHelper;
import fi.natroutter.baudbound.serial.data.FlowControl;
import fi.natroutter.baudbound.serial.data.Parity;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.utilities.Utils;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;


public class SettingsDialog {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();

    private final ImBoolean optionStartWithOS = new ImBoolean(false);
    private final ImBoolean optionStartHidden = new ImBoolean(false);
    private final ImBoolean optionAutoConnect = new ImBoolean(false);

    private final ImBoolean optionRunFirstEventOnly = new ImBoolean(false);


    private final ImInt optionDevicePort = new ImInt(0);
    private final ImInt optionDeviceBaudRate = new ImInt(0);
    private final ImInt optionDeviceDataBit = new ImInt(0);
    private final ImInt optionDeviceStopBit = new ImInt(0);
    private final ImInt optionDeviceParity = new ImInt(0);
    private final ImInt optionDeviceFlowControl = new ImInt(0);

    private final String[] baudRates = {"9600", "19200", "38400", "115200"};
    private final String[] dataBits = {"5", "6", "7", "8"};
    private final String[] stopBits = {"1", "2"};
    private enum ConnectionStatus {WAITING_TEST, NO_DEVICE, CONNECTED, FAILED }

    private final boolean disablePortSelection = false;
    private List<SerialPort> devices;
    private String[] portNames;
    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);
    private ConnectionStatus connectionStatus = ConnectionStatus.WAITING_TEST;
    private long statusResetAt = -1;
    private long resetInterval = 3000;

    public SettingsDialog() {
        this.devices = SerialHelper.getDevices();
        load();
    }

    public void show() {
        this.open = true;
    }

    public void render() {
        if (open) {
            ImGui.openPopup("Settings");
            modalOpen.set(true);
            open = false;
        }

        if (statusResetAt > 0 && System.currentTimeMillis() >= statusResetAt) {
            connectionStatus = ConnectionStatus.WAITING_TEST;
            statusResetAt = -1;
        }

        ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySizeX() / 2,
                ImGui.getIO().getDisplaySizeY() / 2,
                ImGuiCond.Always,
                0.5f, 0.5f
        );

        ImGui.setNextWindowSizeConstraints(ImGui.getIO().getDisplaySizeX() * 0.9f, 0, ImGui.getIO().getDisplaySizeX() * 0.9f, Float.MAX_VALUE);

        boolean wasOpen = ImGui.isPopupOpen("Settings");
        if (ImGui.beginPopupModal("Settings",modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            ImGui.separatorText("General Settings");

            ImGui.checkbox("Start with the OS", optionStartWithOS);
            GuiHelper.toolTip("Automatically launch BaudBound when operating system starts.");

            ImGui.checkbox("Start hidden", optionStartHidden);
            GuiHelper.toolTip("Start minimized to the system tray instead of showing the window.");

            ImGui.checkbox("Auto connect device", optionAutoConnect);
            GuiHelper.toolTip("Automatically connect to the configured serial device on startup.");

            ImGui.separatorText("Action Settings");

            ImGui.checkbox("Run first event only", optionRunFirstEventOnly);
            GuiHelper.toolTip("Enable this to trigger only the first matching event.\n" +
                    "Disable it to run all events whose conditions match the serial input.");


            ImGui.separatorText("Device Settings");

            if (devices != null && !devices.isEmpty()) {
                portNames = devices.stream().map(SerialHelper::getDeviceName).toArray(String[]::new);
            }

            float refreshWidth = ImGui.calcTextSize("Refresh").x + ImGui.getStyle().getFramePaddingX() * 2;
            String longestPort = portNames != null ? Utils.getLongest(portNames) : null;
            float minComboWidth = longestPort != null ? ImGui.calcTextSize(longestPort).x + ImGui.getStyle().getFramePaddingX() * 2 + 20 : 150;
            float comboWidth = Math.max(minComboWidth, ImGui.getContentRegionAvailX() - refreshWidth - ImGui.getStyle().getItemSpacingX());

            ImGui.beginDisabled((devices == null || devices.isEmpty()));
            ImGui.text("Device");
            GuiHelper.toolTip("The com port your device is connected to.");
            ImGui.setNextItemWidth(comboWidth);
            ImGui.combo("##portselector", optionDevicePort, portNames);
            ImGui.endDisabled();

            ImGui.sameLine();
            if (ImGui.button("Refresh", new ImVec2(refreshWidth, 20))) {
                this.devices = SerialHelper.getDevices();
            }

            ImGui.text("Baud Rate");
            GuiHelper.toolTip("Data transmission speed in bits per second. Must match your device's setting.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##baudrate", optionDeviceBaudRate, baudRates);

            ImGui.text("Data Bits");
            GuiHelper.toolTip("Number of bits per data frame. Most devices use 8.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##databits", optionDeviceDataBit, dataBits);

            ImGui.text("Stop Bits");
            GuiHelper.toolTip("Number of stop bits marking the end of each frame. Most devices use 1.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##stopbits", optionDeviceStopBit, stopBits);

            ImGui.text("Parity");
            GuiHelper.toolTip("Error-checking method. Must match your device's setting. Most devices use None.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##parity", optionDeviceParity, Parity.asArray());

            ImGui.text("Flow Control");
            GuiHelper.toolTip("Controls data flow between devices to prevent buffer overflow. Most device's use None.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##flowcontrol", optionDeviceFlowControl, FlowControl.asArray());


            ImGui.text("Connection:  ");
            ImGui.sameLine();

            switch (connectionStatus) {
                case WAITING_TEST -> ImGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, "Waiting For Test...");
                case NO_DEVICE -> ImGui.textColored(1.0f, 0.549f, 0.0f, 1.0f, "Device Not Found!");
                case CONNECTED -> ImGui.textColored(0.0f, 1.0f, 0.0f, 1.0f, "Connected successfully!");
                case FAILED -> ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "Connection failed!");
            }

            ImGui.sameLine();
            if (ImGui.button("Test", new ImVec2(ImGui.calcTextSize("Test").x + ImGui.getStyle().getItemSpacingX() * 2, 20))) {
                connectionStatus = testConnection();
                statusResetAt = System.currentTimeMillis() + resetInterval;
            }


            // Buttons
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();


            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), 20))) {
                save();
            }

            ImGui.endPopup();
        } else if (wasOpen && !modalOpen.get()) {
            modalOpen.set(true);
        }
    }

    private ConnectionStatus testConnection() {
        if (devices == null || devices.isEmpty()) {
            return ConnectionStatus.NO_DEVICE;
        }

        SerialPort port = devices.get(optionDevicePort.get());
        port.setBaudRate(Integer.parseInt(baudRates[optionDeviceBaudRate.get()]));
        port.setNumDataBits(Integer.parseInt(dataBits[optionDeviceDataBit.get()]));
        port.setNumStopBits(Integer.parseInt(stopBits[optionDeviceStopBit.get()]));
        port.setParity(Parity.values()[optionDeviceParity.get()].getBit());
        port.setFlowControl(FlowControl.values()[optionDeviceFlowControl.get()].getBit());

        if (port.openPort()) {
            port.closePort();
            return ConnectionStatus.CONNECTED;
        } else {
            return ConnectionStatus.FAILED;
        }
    }

    private void load() {
        logger.info("Loading settings...");
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();
        DataStore.Settings.Device device = settings.getDevice();

        //Load Generic Settings
        optionStartWithOS.set(generic.isStartWithOS());
        optionStartHidden.set(generic.isStartHidden());
        optionAutoConnect.set(generic.isAutoConnect());

        //Load Event Settings
        optionRunFirstEventOnly.set(event.isRunFirstOnly());

        //Load Device Settings
        if (devices != null && device.getPort() != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (devices.get(i).getSystemPortName().equals(device.getPort())) {
                    optionDevicePort.set(i);
                    break;
                }
            }
        }

        for (int i = 0; i < baudRates.length; i++) {
            if (Integer.parseInt(baudRates[i]) == device.getBaudRate()) {
                optionDeviceBaudRate.set(i);
                break;
            }
        }

        for (int i = 0; i < dataBits.length; i++) {
            if (Integer.parseInt(dataBits[i]) == device.getDataBits()) {
                optionDeviceDataBit.set(i);
                break;
            }
        }

        for (int i = 0; i < stopBits.length; i++) {
            if (Integer.parseInt(stopBits[i]) == device.getStopBits()) {
                optionDeviceStopBit.set(i);
                break;
            }
        }

        if (device.getParity() != null) {
            Parity[] parities = Parity.values();
            for (int i = 0; i < parities.length; i++) {
                if (parities[i].name().equals(device.getParity())) {
                    optionDeviceParity.set(i);
                    break;
                }
            }
        }

        FlowControl[] flowControls = FlowControl.values();
        for (int i = 0; i < flowControls.length; i++) {
            if (flowControls[i].name().equals(device.getFlowControl())) {
                optionDeviceFlowControl.set(i);
                break;
            }
        }
    }

    private void save() {
        logger.info("Saving settings...");
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();
        DataStore.Settings.Device device = settings.getDevice();

        //Save Generic Settings
        generic.setStartWithOS(optionStartWithOS.get());
        generic.setStartHidden(optionStartHidden.get());
        generic.setAutoConnect(optionAutoConnect.get());

        //Save Event Settings
        event.setRunFirstOnly(optionRunFirstEventOnly.get());



        //Save Device settings
        if (devices != null && !devices.isEmpty()) {
            device.setPort(devices.get(optionDevicePort.get()).getSystemPortName());
        }
        device.setBaudRate(Integer.parseInt(baudRates[optionDeviceBaudRate.get()]));
        device.setDataBits(Integer.parseInt(dataBits[optionDeviceDataBit.get()]));
        device.setStopBits(Integer.parseInt(stopBits[optionDeviceStopBit.get()]));
        device.setParity(Parity.values()[optionDeviceParity.get()].name());
        device.setFlowControl(FlowControl.values()[optionDeviceFlowControl.get()].name());

        storage.save();
    }
}