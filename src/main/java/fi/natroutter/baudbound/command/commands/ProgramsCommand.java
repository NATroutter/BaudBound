package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.foxlib.FoxLib;

import java.util.List;

/**
 * Console command for listing configured programs.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code programs} — list all configured programs with their paths</li>
 * </ul>
 */
public class ProgramsCommand extends Command {

    public ProgramsCommand() {
        super("programs", "List all configured programs");
    }

    @Override
    public void execute(String[] args) {
        List<DataStore.Actions.Program> programs = BaudBound.getStorageProvider().getData().getActions().getPrograms();
        if (programs.isEmpty()) {
            FoxLib.println("  {YELLOW}No programs configured.{RESET}");
            return;
        }
        log("Console: programs listed (" + programs.size() + ")");
        int nameWidth = programs.stream().mapToInt(p -> p.getName().length()).max().orElse(0);
        List<String> rows = programs.stream().map(p -> {
            String name  = p.getName() + " ".repeat(nameWidth - p.getName().length());
            String admin = p.isRunAsAdmin() ? "  {BRIGHT_RED}[admin]{RESET}" : "";
            return "{BLUE}" + name + "{RESET}  {WHITE}" + p.getPath() + admin;
        }).toList();
        ConsoleUI.printBox("Programs  (" + programs.size() + ")", rows);
    }
}