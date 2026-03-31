package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.command.StatusRegistry;
import fi.natroutter.foxlib.FoxLib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Console command for reading and writing named boolean statuses.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code status} — list all statuses and their current values</li>
 *   <li>{@code status get <name>} — print the value of a specific status</li>
 *   <li>{@code status set <name> <true|false>} — change the value of a status</li>
 * </ul>
 */
public class StatusCommand extends Command {

    private final StatusRegistry registry;

    public StatusCommand(StatusRegistry registry) {
        super("status", "Get or set a named status  (usage: status [get|set <name> [true|false]])");
        this.registry = registry;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            showAll();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "get" -> {
                if (args.length < 2) {
                    FoxLib.println("  {BRIGHT_RED}Usage: status get <name>{RESET}");
                    return;
                }
                handleGet(args[1]);
            }
            case "set" -> {
                if (args.length < 3) {
                    FoxLib.println("  {BRIGHT_RED}Usage: status set <name> <true|false>{RESET}");
                    return;
                }
                handleSet(args[1], args[2]);
            }
            default -> FoxLib.println("  {BRIGHT_RED}Unknown subcommand: \"" + args[0] + "\"{RESET}  —  usage: {BRIGHT_YELLOW}status [get|set <name> [true|false]]{RESET}");
        }
    }

    private void showAll() {
        Collection<StatusRegistry.Entry> all = registry.getAll();
        if (all.isEmpty()) {
            FoxLib.println("  {YELLOW}No statuses registered.{RESET}");
            return;
        }

        int nameWidth = all.stream().mapToInt(e -> e.name().length()).max().orElse(0);

        List<String> rows = new ArrayList<>();
        for (StatusRegistry.Entry entry : all) {
            String name = entry.name() + " ".repeat(nameWidth - entry.name().length());
            String state = entry.getter().getAsBoolean()
                    ? "{BRIGHT_GREEN}enabled {RESET}"
                    : "{BRIGHT_RED}disabled{RESET}";
            rows.add("{BLUE}" + name + "{RESET}  " + state + "  {WHITE}" + entry.description());
        }

        ConsoleUI.printBox("Status Overview", rows);
    }

    private void handleGet(String name) {
        registry.find(name).ifPresentOrElse(entry -> {
            String state = entry.getter().getAsBoolean()
                    ? "{BRIGHT_GREEN}enabled{RESET}"
                    : "{BRIGHT_RED}disabled{RESET}";
            ConsoleUI.printBox(entry.name(), List.of(
                    "{BLUE}State      :{RESET}  " + state,
                    "{BLUE}Description:{RESET}  {WHITE}" + entry.description()
            ));
        }, () -> FoxLib.println("  {BRIGHT_RED}Unknown status: \"" + name + "\"{RESET}"));
    }

    private void handleSet(String name, String valueStr) {
        Boolean value = parseBoolean(valueStr);
        if (value == null) {
            FoxLib.println("  {BRIGHT_RED}Invalid value: \"" + valueStr + "\"{RESET}  —  expected {BRIGHT_YELLOW}true{RESET} or {BRIGHT_YELLOW}false{RESET}");
            return;
        }
        registry.find(name).ifPresentOrElse(
                entry -> entry.setter().accept(value),
                () -> FoxLib.println("  {BRIGHT_RED}Unknown status: \"" + name + "\"{RESET}")
        );
    }

    /** Returns {@code true}/{@code false} for recognised tokens, or {@code null} for invalid input. */
    private static Boolean parseBoolean(String s) {
        return switch (s.toLowerCase()) {
            case "true",  "on",  "enable"  -> true;
            case "false", "off", "disable" -> false;
            default                        -> null;
        };
    }
}