package fi.natroutter.baudbound.command;

import fi.natroutter.foxlib.FoxLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Reads lines from {@code System.in} on a virtual thread and dispatches them
 * to registered {@link Command} instances by name.
 * <p>
 * The built-in {@code help} command lists all registered commands and their descriptions.
 * All output goes directly to {@code System.out} via {@link ConsoleUI} — the logger is not used.
 * <p>
 * Call {@link #register(Command)} for each command before {@link #startListening()}.
 */
public class CommandHandler {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /**
     * Registers a command. The command's name (lower-cased) is used as the dispatch key.
     * Registering a command with the same name as an existing one replaces it.
     */
    public void register(Command command) {
        commands.put(command.getName(), command);
    }

    /**
     * Starts a virtual thread that blocks on {@code System.in} and dispatches each
     * non-empty line to the matching command. The thread exits when stdin is closed.
     */
    public void startListening() {
        Thread.ofVirtual().start(() -> {
            Scanner scanner = new Scanner(System.in);
            try {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;
                    dispatch(line);
                }
            } catch (Exception ignored) {
                // stdin closed or interrupted — exit the loop silently
            }
        });
    }

    private void dispatch(String line) {
        String[] parts = line.split("\\s+");
        String name = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        if (name.equals("help")) {
            printHelp();
            return;
        }

        Command command = commands.get(name);
        if (command != null) {
            command.execute(args);
        } else {
            FoxLib.println("  {BRIGHT_RED}Unknown command: \"" + name + "\" —  type \"help\" to list available commands.");
        }
    }

    private void printHelp() {
        int nameWidth = "help".length();
        for (Command cmd : commands.values()) {
            nameWidth = Math.max(nameWidth, cmd.getName().length());
        }

        List<String> rows = new ArrayList<>();
        rows.add(colorRow("help", nameWidth, "List all available commands"));
        for (Command cmd : commands.values()) {
            rows.add(colorRow(cmd.getName(), nameWidth, cmd.getDescription()));
        }

        ConsoleUI.printBox("Available Commands", rows);
    }

    private static String colorRow(String name, int nameWidth, String description) {
        String paddedName = name + " ".repeat(nameWidth - name.length());
        return "{BLUE}" + paddedName + "{RESET}  {WHITE}—{RESET}  {CYAN}" + description;
    }
}