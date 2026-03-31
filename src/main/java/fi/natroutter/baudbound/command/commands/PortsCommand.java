package fi.natroutter.baudbound.command.commands;

import com.fazecast.jSerialComm.SerialPort;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.serial.SerialHandler;
import fi.natroutter.foxlib.FoxLib;

import java.util.ArrayList;
import java.util.List;

/**
 * Console command for listing serial ports currently visible to the operating system.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code ports} — list all available serial ports with their descriptive names</li>
 * </ul>
 */
public class PortsCommand extends Command {

    public PortsCommand() {
        super("ports", "List all serial ports currently available on this system");
    }

    @Override
    public void execute(String[] args) {
        log("Console: ports listed");
        List<SerialPort> ports = SerialHandler.getAvailablePorts();
        if (ports.isEmpty()) {
            FoxLib.println("  {YELLOW}No serial ports found.{RESET}");
            return;
        }

        int portWidth = ports.stream().mapToInt(p -> p.getSystemPortName().length()).max().orElse(0);
        List<String> rows = new ArrayList<>();
        for (SerialPort port : ports) {
            String name = port.getSystemPortName() + " ".repeat(portWidth - port.getSystemPortName().length());
            rows.add("{BLUE}" + name + "{RESET}  {WHITE}" + port.getDescriptivePortName());
        }
        ConsoleUI.printBox("Available Ports  (" + ports.size() + ")", rows);
    }
}