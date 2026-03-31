package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.foxlib.FoxLib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Console command for inspecting and clearing the runtime event state map.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code states} — list all active state entries</li>
 *   <li>{@code states clear <name>} — remove a specific state entry</li>
 *   <li>{@code states clear all} — remove all state entries</li>
 * </ul>
 */
public class StatesCommand extends Command {

    public StatesCommand() {
        super("states", "List or clear runtime event states  (usage: states [clear <name>|all])");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            showAll();
            return;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            if (args.length < 2) {
                FoxLib.println("  {BRIGHT_RED}Usage: states clear <name>|all{RESET}");
                return;
            }
            String target = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            if (target.equalsIgnoreCase("all")) {
                handleClearAll();
            } else {
                handleClear(target);
            }
        } else {
            FoxLib.println("  {BRIGHT_RED}Unknown subcommand: \"" + args[0] + "\"{RESET}  —  usage: {BRIGHT_YELLOW}states [clear <name>|all]{RESET}");
        }
    }

    private void showAll() {
        log("Console: states listed");
        Map<String, String> states = BaudBound.getEventHandler().getStates();
        if (states.isEmpty()) {
            FoxLib.println("  {YELLOW}No active states.{RESET}");
            return;
        }
        int nameWidth = states.keySet().stream().mapToInt(String::length).max().orElse(0);
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : states.entrySet()) {
            String name = entry.getKey() + " ".repeat(nameWidth - entry.getKey().length());
            rows.add("{BLUE}" + name + "{RESET}  {CYAN}" + entry.getValue());
        }
        ConsoleUI.printBox("Active States  (" + states.size() + ")", rows);
    }

    private void handleClear(String name) {
        Map<String, String> states = BaudBound.getEventHandler().getStates();
        if (!states.containsKey(name)) {
            logWarn("Console: states clear failed — unknown state \"" + name + "\"");
            FoxLib.println("  {BRIGHT_RED}Unknown state: \"" + name + "\"{RESET}");
            return;
        }
        BaudBound.getEventHandler().clearState(name);
        log("Console: cleared state \"" + name + "\"");
        FoxLib.println("  {BRIGHT_GREEN}Cleared state \"" + name + "\".{RESET}");
    }

    private void handleClearAll() {
        BaudBound.getEventHandler().clearAllStates();
        log("Console: all states cleared");
        FoxLib.println("  {BRIGHT_GREEN}All states cleared.{RESET}");
    }
}