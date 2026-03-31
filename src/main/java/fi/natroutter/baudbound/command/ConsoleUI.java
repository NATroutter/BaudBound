package fi.natroutter.baudbound.command;

import fi.natroutter.foxlib.FoxLib;

import java.util.List;

/**
 * Utility for rendering colored box-drawing UI in the system console via {@link FoxLib#println}.
 * <p>
 * Produces output like:
 * <pre>
 * ╔══════════════════════════╗
 * ║  Title                   ║
 * ╟──────────────────────────╢
 * ║  row one                 ║
 * ║  row two                 ║
 * ╚══════════════════════════╝
 * </pre>
 * Box borders are rendered in YELLOW. The title is rendered in bright white.
 * Row strings may contain {@code {COLOR}} tags — visual width is calculated
 * by stripping them so alignment stays correct.
 */
public final class ConsoleUI {

    private ConsoleUI() {}

    /**
     * Prints a titled box with content rows to the console.
     *
     * @param title plain-text header line (no color tags)
     * @param rows  content lines, may contain {@code {COLOR}} tags
     */
    public static void printBox(String title, List<String> rows) {
        int maxContent = title.length();
        for (String row : rows) maxContent = Math.max(maxContent, visualLength(row));
        // inner = 2 leading spaces + content + 2 trailing spaces
        int inner = maxContent + 4;

        String hLine = "═".repeat(inner);
        String sLine = "─".repeat(inner);

        FoxLib.println("{YELLOW}╔" + hLine + "╗{RESET}");
        FoxLib.println("{YELLOW}║  " + title + spaces(inner - 2 - title.length()) + "║{RESET}");
        FoxLib.println("{YELLOW}╟" + sLine + "╢{RESET}");
        for (String row : rows) {
            FoxLib.println("{YELLOW}║{RESET}  " + row + "{RESET}" + spaces(inner - 2 - visualLength(row)) + "{YELLOW}║{RESET}");
        }
        FoxLib.println("{YELLOW}╚" + hLine + "╝{RESET}");
    }

    /** Returns {@code n} spaces, or an empty string when {@code n} is zero or negative. */
    private static String spaces(int n) {
        return n > 0 ? " ".repeat(n) : "";
    }

    /** Returns the visible character length of {@code s} by stripping all {@code {TAG}} color tokens. */
    private static int visualLength(String s) {
        return s.replaceAll("\\{[A-Z_]+}", "").length();
    }
}