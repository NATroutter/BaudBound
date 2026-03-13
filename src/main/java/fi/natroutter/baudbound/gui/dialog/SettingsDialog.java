package fi.natroutter.baudbound.gui.dialog;

import com.fazecast.jSerialComm.SerialPort;
import fi.natroutter.baudbound.serial.SerialHandler;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.enums.FlowControl;
import fi.natroutter.baudbound.enums.Parity;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.system.ShortcutManager;
import fi.natroutter.baudbound.system.StartupManager;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;


/**
 * Modal settings dialog covering General, Event, and Device configuration sections,
 * plus the Utilities shortcut-creator.
 * <p>
 * ImGui state fields (prefixed {@code option}) shadow the persisted settings and are
 * populated by {@link #load()} when the dialog opens. {@link #save()} writes them back
 * to {@link DataStore} and persists to disk.
 */
public class SettingsDialog extends BaseDialog {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final SerialHandler serialHandler = BaudBound.getSerialHandler();

    private final ImBoolean optionStartWithOS       = new ImBoolean(false);
    private final ImBoolean optionStartHidden       = new ImBoolean(false);
    private final ImBoolean optionAutoConnect       = new ImBoolean(false);
    private final ImBoolean optionRunFirstEventOnly  = new ImBoolean(false);
    private final ImBoolean optionConditionEventsFirst = new ImBoolean(false);
    private final ImBoolean optionSkipEmptyConditions = new ImBoolean(false);
    private final ImInt     optionDevicePort        = new ImInt(0);
    private final ImInt     optionDeviceBaudRate    = new ImInt(0);
    private final ImInt     optionDeviceDataBit     = new ImInt(0);
    private final ImInt     optionDeviceStopBit     = new ImInt(0);
    private final ImInt     optionDeviceParity      = new ImInt(0);
    private final ImInt     optionDeviceFlowControl = new ImInt(0);

    private final String[] baudRates = {"9600", "19200", "38400", "115200"};
    private final String[] dataBits  = {"8", "7", "6", "5"};
    private final String[] stopBits  = {"1", "2"};

    private List<SerialPort> devices;
    private String[] portNames;
    private volatile boolean creatingShortcut = false;

    public SettingsDialog() {
        this.devices = serialHandler.getDevices();
        load();
    }

    public void render() {
        if (beginModal("Settings")) {

            ImGui.separatorText("General Settings");

            ImGui.checkbox("Start with the OS", optionStartWithOS);
            GuiHelper.toolTip("Automatically launch BaudBound when operating system starts.");

            ImGui.checkbox("Start hidden", optionStartHidden);
            GuiHelper.toolTip("Start minimized to the system tray instead of showing the window.");

            ImGui.checkbox("Auto connect device", optionAutoConnect);
            GuiHelper.toolTip("Automatically connect to the configured serial device on startup.");

            ImGui.separatorText("Event Settings");

            ImGui.checkbox("Run first only", optionRunFirstEventOnly);
            GuiHelper.toolTip("Enable to trigger only the first matching event.\n" +
                    "Disable to run all events whose conditions match.");

            ImGui.beginDisabled(optionSkipEmptyConditions.get());
            ImGui.checkbox("Process conditional events first", optionConditionEventsFirst);
            GuiHelper.toolTip(optionSkipEmptyConditions.get()
                    ? "Not needed — all unconditioned events are already being skipped."
                    : "When enabled, events with conditions are always evaluated before\nevents without conditions, regardless of their order in the list.");
            ImGui.endDisabled();

            ImGui.checkbox("Skip events without conditions", optionSkipEmptyConditions);
            GuiHelper.toolTip("When enabled, events that have no conditions set are ignored entirely.");

            ImGui.separatorText("Device Settings");

            if (devices != null && !devices.isEmpty()) {
                portNames = devices.stream().map(SerialPort::getDescriptivePortName).toArray(String[]::new);
            }

            float refreshWidth = ImGui.calcTextSize("Refresh").x + ImGui.getStyle().getFramePaddingX() * 2;
            String longestPort = portNames != null ? getLongestString(portNames) : null;
            float minComboWidth = longestPort != null ? ImGui.calcTextSize(longestPort).x + ImGui.getStyle().getFramePaddingX() * 2 + GuiTheme.BUTTON_HEIGHT : 150;
            float comboWidth = Math.max(minComboWidth, ImGui.getContentRegionAvailX() - refreshWidth - ImGui.getStyle().getItemSpacingX());

            ImGui.beginDisabled(devices == null || devices.isEmpty());
            ImGui.text("Device");
            GuiHelper.toolTip("The COM port your device is connected to.");
            ImGui.setNextItemWidth(comboWidth);
            ImGui.combo("##portselector", optionDevicePort, portNames);
            ImGui.endDisabled();

            ImGui.sameLine();
            if (ImGui.button("Refresh", new ImVec2(refreshWidth, GuiTheme.BUTTON_HEIGHT))) {
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
            GuiHelper.toolTip("Number of stop bits marking end of each frame. Most devices use 1.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##stopbits", optionDeviceStopBit, stopBits);

            ImGui.text("Parity");
            GuiHelper.toolTip("Error-checking method. Must match your device's setting. Most use None.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##parity", optionDeviceParity, Parity.asArray());

            ImGui.text("Flow Control");
            GuiHelper.toolTip("Controls data flow between devices to prevent buffer overflow. Most use None.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##flowcontrol", optionDeviceFlowControl, FlowControl.asArray());

            ImGui.separatorText("Utilities");

            ImGui.beginDisabled(creatingShortcut);
            if (ImGui.button(creatingShortcut ? "Selecting folder..." : "Create Shortcut...",
                    new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                openShortcutDialog();
            }
            ImGui.endDisabled();
            GuiHelper.toolTip("Create a shortcut to BaudBound in a folder of your choice.\n" +
                    "Opens in the startup folder by default.");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                save();
            }

            endModal();
        }
    }

    /**
     * Opens a Swing {@link javax.swing.JFileChooser} on a virtual thread (to avoid blocking
     * the GLFW main thread) and creates the shortcut in the selected folder.
     * <p>
     * An invisible always-on-top {@link javax.swing.JFrame} is used as the parent so the
     * chooser appears in front of the GLFW window.
     */
    private void openShortcutDialog() {
        creatingShortcut = true;
        Thread.ofVirtual().start(() -> {
            try {
                String[] selectedFolder = {null};
                SwingUtilities.invokeAndWait(() -> {
                    // Use an always-on-top invisible frame as parent so the dialog
                    // appears in front of the GLFW window.
                    JFrame parent = new JFrame();
                    parent.setUndecorated(true);
                    parent.setAlwaysOnTop(true);
                    parent.setVisible(true);
                    parent.setLocationRelativeTo(null);

                    JFileChooser chooser = new JFileChooser(ShortcutManager.defaultFolderPath());
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle("Select Shortcut Location");
                    if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                        selectedFolder[0] = chooser.getSelectedFile().getAbsolutePath();
                    }
                    parent.dispose();
                });

                if (selectedFolder[0] == null) {
                    creatingShortcut = false;
                    return;
                }

                ShortcutManager.createShortcut(selectedFolder[0]);
                creatingShortcut = false;
                BaudBound.getMessageDialog().show("Shortcut Created",
                        "Shortcut created successfully in:\n" + selectedFolder[0],
                        new DialogButton("OK", () -> {}));
            } catch (Exception e) {
                creatingShortcut = false;
                logger.error("Failed to create shortcut: " + e.getMessage());
                BaudBound.getMessageDialog().show("Error",
                        "Failed to create shortcut:\n" + e.getMessage(),
                        new DialogButton("OK", () -> {}));
            }
        });
    }

    /** Copies current {@link DataStore} values into the ImGui state fields. */
    private void load() {
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();
        DataStore.Settings.Device device = settings.getDevice();

        optionStartWithOS.set(StartupManager.isEnabled());
        optionStartHidden.set(generic.isStartHidden());
        optionAutoConnect.set(generic.isAutoConnect());

        optionRunFirstEventOnly.set(event.isRunFirstOnly());
        optionConditionEventsFirst.set(event.isConditionEventsFirst());
        optionSkipEmptyConditions.set(event.isSkipEmptyConditions());

        if (devices != null && device.getPort() != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (devices.get(i).getSystemPortName().equals(device.getPort())) {
                    optionDevicePort.set(i);
                    break;
                }
            }
        }

        optionDeviceBaudRate.set(findIntIndex(baudRates, device.getBaudRate()));
        optionDeviceDataBit.set(findIntIndex(dataBits, device.getDataBits()));
        optionDeviceStopBit.set(findIntIndex(stopBits, device.getStopBits()));
        optionDeviceParity.set(findEnumIndex(Parity.values(), device.getParity()));
        optionDeviceFlowControl.set(findEnumIndex(FlowControl.values(), device.getFlowControl()));
    }

    /** Writes the current ImGui state fields back to {@link DataStore} and persists to disk. */
    private void save() {
        logger.info("Saving settings...");
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();
        DataStore.Settings.Device device = settings.getDevice();

        try {
            StartupManager.setEnabled(optionStartWithOS.get());
        } catch (Exception e) {
            logger.error("Failed to update startup registration: " + e.getMessage());
            optionStartWithOS.set(false);
        }
        generic.setStartHidden(optionStartHidden.get());
        generic.setAutoConnect(optionAutoConnect.get());

        event.setRunFirstOnly(optionRunFirstEventOnly.get());
        event.setConditionEventsFirst(optionConditionEventsFirst.get());
        event.setSkipEmptyConditions(optionSkipEmptyConditions.get());

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

    private static int findIntIndex(String[] array, int value) {
        return IntStream.range(0, array.length)
                .filter(i -> { try { return Integer.parseInt(array[i]) == value; } catch (NumberFormatException e) { return false; } })
                .findFirst().orElse(0);
    }

    private static <T extends Enum<T>> int findEnumIndex(T[] values, String name) {
        if (name == null) return 0;
        return IntStream.range(0, values.length)
                .filter(i -> values[i].name().equals(name))
                .findFirst().orElse(0);
    }

    private static String getLongestString(String[] array) {
        return Arrays.stream(array)
                .max(Comparator.comparingInt(String::length))
                .orElse("");
    }
}