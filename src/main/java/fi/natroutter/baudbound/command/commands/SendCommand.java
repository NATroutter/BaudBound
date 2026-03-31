package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.foxlib.FoxLib;

import java.util.Arrays;

/**
 * Console command for writing raw data to a connected serial device.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code send <device> <text>} — write {@code text} to the named device's serial port</li>
 * </ul>
 * Device names that contain spaces must be wrapped in double quotes,
 * e.g. {@code send "My Device" hello}.
 */
public class SendCommand extends Command {

    public SendCommand() {
        super("send", "Send raw text to a connected serial device  (usage: send <device> <text>)");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            FoxLib.println("  {BRIGHT_RED}Usage: send <device> <text>{RESET}");
            return;
        }
        String deviceName = args[0];
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean sent = BaudBound.getDeviceConnectionManager().sendToDevice(deviceName, text);
        if (sent) {
            log("Console: sent to device \"" + deviceName + "\": " + text);
            FoxLib.println("  {BRIGHT_GREEN}Sent to \"" + deviceName + "\": {WHITE}" + text + "{RESET}");
        } else {
            logError("Console: send failed — device \"" + deviceName + "\" is not connected or not found");
            FoxLib.println("  {BRIGHT_RED}Failed: device \"" + deviceName + "\" is not connected or not found.{RESET}");
        }
    }
}