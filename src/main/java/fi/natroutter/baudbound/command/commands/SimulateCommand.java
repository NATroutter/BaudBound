package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.foxlib.FoxLib;

import java.util.Arrays;

/**
 * Console command for injecting a simulated serial input line through the event system.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code simulate <input>} — process the input with no device context</li>
 *   <li>{@code simulate <device> <input>} — process the input as if it arrived from the named device</li>
 * </ul>
 * Device names that contain spaces must be wrapped in double quotes,
 * e.g. {@code simulate "My Device" hello}. Useful for testing event mappings without
 * physical hardware.
 */
public class SimulateCommand extends Command {

    public SimulateCommand() {
        super("simulate", "Inject a simulated serial line through the event system  (usage: simulate [<device>] <input>)");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            FoxLib.println("  {BRIGHT_RED}Usage: simulate [<device>] <input>{RESET}");
            return;
        }

        DataStore.Device device = null;
        String input;

        if (args.length >= 2) {
            // First token might be a device name — check before treating everything as input
            DataStore.Device candidate = findDevice(args[0]);
            if (candidate != null) {
                device = candidate;
                input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            } else {
                input = String.join(" ", args);
            }
        } else {
            input = args[0];
        }

        String deviceTag = device != null ? " as device \"" + device.getName() + "\"" : "";
        FoxLib.println("  {CYAN}Simulating input: \"" + input + "\"" + deviceTag + "{RESET}");

        final DataStore.Device finalDevice = device;
        final String finalInput = input;
        Thread.ofVirtual().start(() -> BaudBound.getEventHandler().process(finalInput, finalDevice));
    }

    private static DataStore.Device findDevice(String name) {
        return BaudBound.getStorageProvider().getData().getDevices().stream()
                .filter(d -> name.equalsIgnoreCase(d.getName()))
                .findFirst().orElse(null);
    }
}