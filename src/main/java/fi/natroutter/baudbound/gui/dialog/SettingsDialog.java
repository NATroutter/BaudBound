package fi.natroutter.baudbound.gui.dialog;

import com.fazecast.jSerialComm.SerialPort;
import fi.natroutter.baudbound.serial.SerialHandler;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.enums.FlowControl;
import fi.natroutter.baudbound.enums.Parity;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.system.StartupManager;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public class SettingsDialog {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();
    private SerialHandler serialHandler = BaudBound.getSerialHandler();

    private final ImBoolean optionStartWithOS = new ImBoolean(false);
    private final ImBoolean optionStartHidden = new ImBoolean(false);
    private final ImBoolean optionAutoConnect = new ImBoolean(false);

    private final ImBoolean optionRunFirstEventOnly = new ImBoolean(false);
    private final ImBoolean optionUseDefaultEvent = new ImBoolean(false);
    private final ImInt optionDefaultEvent = new ImInt(0);


    private final ImInt optionDevicePort = new ImInt(0);
    private final ImInt optionDeviceBaudRate = new ImInt(0);
    private final ImInt optionDeviceDataBit = new ImInt(0);
    private final ImInt optionDeviceStopBit = new ImInt(0);
    private final ImInt optionDeviceParity = new ImInt(0);
    private final ImInt optionDeviceFlowControl = new ImInt(0);

    private final String[] baudRates = {"9600", "19200", "38400", "115200"};
    private final String[] dataBits = {"8", "7", "6", "5"};
    private final String[] stopBits = {"1", "2"};


    private final boolean disablePortSelection = false;
    private List<SerialPort> devices;
    private String[] portNames;
    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);

    public SettingsDialog() {
        this.devices = serialHandler.getDevices();
        load();
    }

    public void show() {
        validateDefaultEvent();
        this.open = true;
    }

    private void validateDefaultEvent() {
        if (!optionUseDefaultEvent.get()) return;
        DataStore.Settings.Event event = storage.getData().getSettings().getEvent();
        List<DataStore.Event> events = storage.getData().getEvents();
        String savedName = event.getDefaultEvent();
        boolean found = savedName != null && events.stream().anyMatch(e -> e.getName().equals(savedName));
        if (!found) {
            optionUseDefaultEvent.set(false);
            optionDefaultEvent.set(0);
            event.setUseDefaultEvent(false);
            event.setDefaultEvent(null);
            storage.save();
        }
    }

    public void render() {
        if (open) {
            ImGui.openPopup("Settings");
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

        boolean wasOpen = ImGui.isPopupOpen("Settings");
        if (ImGui.beginPopupModal("Settings",modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            ImGui.separatorText("General Settings");

            ImGui.checkbox("Start with the OS", optionStartWithOS);
            GuiHelper.toolTip("Automatically launch BaudBound when operating system starts.");

            ImGui.checkbox("Start hidden", optionStartHidden);
            GuiHelper.toolTip("Start minimized to the system tray instead of showing the window.");

            ImGui.checkbox("Auto connect device", optionAutoConnect);
            GuiHelper.toolTip("Automatically connect to the configured serial device on startup.");



            ImGui.separatorText("Event Settings");

            ImGui.checkbox("Run first only", optionRunFirstEventOnly);
            GuiHelper.toolTip("Enable this to trigger only the first matching event.\n" +
                    "Disable it to run all events whose conditions match the serial input.");

            ImGui.spacing();
            ImGui.checkbox("Use default event", optionUseDefaultEvent);

            List<DataStore.Event> events = storage.getData().getEvents();

            if (optionUseDefaultEvent.get()) {
                ImGui.text("Default Event:");
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.beginDisabled(events.isEmpty());
                ImGui.combo("##defaultevent", optionDefaultEvent, events.stream().map(DataStore.Event::getName).toArray(String[]::new));
                ImGui.endDisabled();
            }



            ImGui.separatorText("Device Settings");

            if (devices != null && !devices.isEmpty()) {
                portNames = devices.stream().map(SerialPort::getDescriptivePortName).toArray(String[]::new);
            }

            float refreshWidth = ImGui.calcTextSize("Refresh").x + ImGui.getStyle().getFramePaddingX() * 2;
            String longestPort = portNames != null ? getLongest(portNames) : null;
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
                this.devices = serialHandler.getDevices();
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

    public static String getLongest(String[] array) {
        return Arrays.stream(array)
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    private void load() {
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();
        DataStore.Settings.Device device = settings.getDevice();

        //Load Generic Settings
        optionStartWithOS.set(StartupManager.isEnabled());
        optionStartHidden.set(generic.isStartHidden());
        optionAutoConnect.set(generic.isAutoConnect());

        //Load Event Settings
        optionRunFirstEventOnly.set(event.isRunFirstOnly());
        optionUseDefaultEvent.set(event.isUseDefaultEvent());

        List<DataStore.Event> events = storage.getData().getEvents();
        if (event.getDefaultEvent() != null && !events.isEmpty()) {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).getName().equals(event.getDefaultEvent())) {
                    optionDefaultEvent.set(i);
                    break;
                }
            }
        }

        validateDefaultEvent();

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
        try {
            // Always call setEnabled when checked so the JAR path is kept up to date
            StartupManager.setEnabled(optionStartWithOS.get());
        } catch (Exception e) {
            logger.error("Failed to update startup registration: " + e.getMessage());
            optionStartWithOS.set(false);
        }
        generic.setStartWithOS(optionStartWithOS.get());
        generic.setStartHidden(optionStartHidden.get());
        generic.setAutoConnect(optionAutoConnect.get());

        //Save Event Settings
        event.setRunFirstOnly(optionRunFirstEventOnly.get());
        event.setUseDefaultEvent(optionUseDefaultEvent.get());

        List<DataStore.Event> events = storage.getData().getEvents();
        if (!events.isEmpty()) {
            event.setDefaultEvent(events.get(optionDefaultEvent.get()).getName());
        } else {
            event.setDefaultEvent(null);
        }



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