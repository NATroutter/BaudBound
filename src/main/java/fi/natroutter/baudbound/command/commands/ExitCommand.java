package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.foxlib.FoxLib;

/**
 * Console command for cleanly shutting down the application.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code exit} — save configuration, disconnect all devices, and exit</li>
 * </ul>
 * In GUI mode the GLFW window close is requested on the next frame so that
 * {@link BaudBound#dispose()} runs normally. In headless mode {@code System.exit(0)}
 * is called directly and the registered shutdown hook handles cleanup.
 */
public class ExitCommand extends Command {

    public ExitCommand() {
        super("exit", "Save configuration and exit the application");
    }

    @Override
    public void execute(String[] args) {
        FoxLib.println("  {CYAN}Shutting down...{RESET}");
        BaudBound.requestExit();
    }
}