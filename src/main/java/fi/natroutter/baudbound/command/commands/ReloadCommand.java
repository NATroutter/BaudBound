package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.foxlib.FoxLib;

/**
 * Console command for reloading the application configuration from disk.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code reload} — discard the in-memory state and re-read {@code storage.json} from disk</li>
 * </ul>
 * Useful after hand-editing the config file without restarting.
 * The event sort cache is invalidated automatically after reload.
 */
public class ReloadCommand extends Command {

    public ReloadCommand() {
        super("reload", "Reload configuration from storage.json without restarting");
    }

    @Override
    public void execute(String[] args) {
        log("Console: reload requested");
        FoxLib.println("  {CYAN}Reloading configuration...{RESET}");
        BaudBound.getStorageProvider().reload();
        BaudBound.getEventHandler().invalidateSortCache();
        log("Console: configuration reloaded");
        FoxLib.println("  {BRIGHT_GREEN}Configuration reloaded.{RESET}");
    }
}