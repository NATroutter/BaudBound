package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.serial.DeviceConnectionManager;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.foxlib.FoxLib;

import java.util.ArrayList;
import java.util.List;

/**
 * Console command for listing and managing serial device connections.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code devices} — list all configured devices with their connection status</li>
 *   <li>{@code devices connect <name>} — connect the named device</li>
 *   <li>{@code devices disconnect <name>} — disconnect the named device</li>
 * </ul>
 * Device names that contain spaces must be wrapped in double quotes,
 * e.g. {@code devices connect "My Device"}.
 */
public class DevicesCommand extends Command {

    public DevicesCommand() {
        super("devices", "List or connect/disconnect serial devices  (usage: devices [connect|disconnect <name>])");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            showAll();
            return;
        }
        switch (args[0].toLowerCase()) {
            case "connect" -> {
                if (args.length < 2) { FoxLib.println("  {BRIGHT_RED}Usage: devices connect <name>{RESET}"); return; }
                handleConnect(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            case "disconnect" -> {
                if (args.length < 2) { FoxLib.println("  {BRIGHT_RED}Usage: devices disconnect <name>{RESET}"); return; }
                handleDisconnect(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            default -> FoxLib.println("  {BRIGHT_RED}Unknown subcommand: \"" + args[0] + "\"{RESET}  —  usage: {BRIGHT_YELLOW}devices [connect|disconnect <name>]{RESET}");
        }
    }

    private void showAll() {
        log("Console: devices listed");
        List<DataStore.Device> devices = BaudBound.getStorageProvider().getData().getDevices();
        if (devices.isEmpty()) {
            FoxLib.println("  {YELLOW}No devices configured.{RESET}");
            return;
        }
        DeviceConnectionManager dcm = BaudBound.getDeviceConnectionManager();
        int nameWidth = devices.stream().mapToInt(d -> d.getName().length()).max().orElse(0);
        List<String> rows = new ArrayList<>();
        for (DataStore.Device device : devices) {
            String name   = device.getName() + " ".repeat(nameWidth - device.getName().length());
            String status = statusLabel(dcm.getStatus(device));
            rows.add("{BLUE}" + name + "{RESET}  " + status + "  {WHITE}" + device.getPort() + "  {CYAN}" + device.getBaudRate() + " baud");
        }
        ConsoleUI.printBox("Devices  (" + devices.size() + ")", rows);
    }

    private void handleConnect(String name) {
        DataStore.Device device = findDevice(name);
        if (device == null) {
            logWarn("Console: connect failed — unknown device \"" + name + "\"");
            FoxLib.println("  {BRIGHT_RED}Unknown device: \"" + name + "\"{RESET}");
            return;
        }
        BaudBound.getDeviceConnectionManager().connect(device);
        log("Console: connecting device \"" + name + "\"");
        FoxLib.println("  {BRIGHT_GREEN}Connecting to \"" + name + "\"...{RESET}");
    }

    private void handleDisconnect(String name) {
        DataStore.Device device = findDevice(name);
        if (device == null) {
            logWarn("Console: disconnect failed — unknown device \"" + name + "\"");
            FoxLib.println("  {BRIGHT_RED}Unknown device: \"" + name + "\"{RESET}");
            return;
        }
        BaudBound.getDeviceConnectionManager().disconnect(device);
        log("Console: disconnected device \"" + name + "\"");
        FoxLib.println("  {BRIGHT_GREEN}Disconnected \"" + name + "\".{RESET}");
    }

    private static DataStore.Device findDevice(String name) {
        return BaudBound.getStorageProvider().getData().getDevices().stream()
                .filter(d -> name.equalsIgnoreCase(d.getName()))
                .findFirst().orElse(null);
    }

    private static String statusLabel(ConnectionStatus status) {
        return switch (status) {
            case CONNECTED         -> "{BRIGHT_GREEN}connected       {RESET}";
            case DISCONNECTED      -> "{YELLOW}disconnected    {RESET}";
            case NO_DEVICE         -> "{YELLOW}device not found{RESET}";
            case FAILED_TO_CONNECT -> "{BRIGHT_RED}connect failed  {RESET}";
        };
    }
}