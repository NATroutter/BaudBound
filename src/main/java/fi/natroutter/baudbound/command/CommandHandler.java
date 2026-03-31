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
 * Arguments are tokenized with basic double-quote support: text inside {@code "…"} is
 * treated as a single token regardless of spaces, allowing names that contain spaces
 * (e.g. {@code devices connect "My Device"}).
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
        String[] parts = tokenize(line);
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

    /**
     * Splits {@code line} into tokens, respecting double-quoted strings as single tokens.
     * Quotes are stripped from the result. An unclosed quote consumes the rest of the line.
     *
     * @param line the raw input line (already trimmed)
     * @return array of tokens, never empty (caller guarantees {@code line} is non-empty)
     */
    private static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens.toArray(String[]::new);
    }

    private static String colorRow(String name, int nameWidth, String description) {
        String paddedName = name + " ".repeat(nameWidth - name.length());
        return "{BLUE}" + paddedName + "{RESET}  {WHITE}—{RESET}  {CYAN}" + description;
    }
}