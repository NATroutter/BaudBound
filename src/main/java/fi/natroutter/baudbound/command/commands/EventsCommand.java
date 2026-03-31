package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.foxlib.FoxLib;

import java.util.ArrayList;
import java.util.List;

/**
 * Console command for displaying the configured event list.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code events} — print all configured events with their condition and action counts</li>
 * </ul>
 */
public class EventsCommand extends Command {

    public EventsCommand() {
        super("events", "List all configured events with their condition and action counts");
    }

    @Override
    public void execute(String[] args) {
        List<DataStore.Event> events = BaudBound.getStorageProvider().getData().getEvents();
        if (events.isEmpty()) {
            FoxLib.println("  {YELLOW}No events configured.{RESET}");
            return;
        }

        int nameWidth = events.stream().mapToInt(e -> e.getName().length()).max().orElse(0);
        List<String> rows = new ArrayList<>();
        for (DataStore.Event event : events) {
            int conditions = event.getConditions() == null ? 0 : event.getConditions().size();
            int actions    = event.getActions()    == null ? 0 : event.getActions().size();
            String name = event.getName() + " ".repeat(nameWidth - event.getName().length());
            rows.add("{BLUE}" + name + "{RESET}"
                    + "  {CYAN}" + conditions + "{RESET} condition" + (conditions == 1 ? " " : "s")
                    + "  {CYAN}" + actions    + "{RESET} action"    + (actions    == 1 ? "" : "s"));
        }
        ConsoleUI.printBox("Events  (" + events.size() + ")", rows);
    }
}