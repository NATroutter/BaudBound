package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * All action types that an event can trigger when its conditions are satisfied.
 * <p>
 * Each constant carries a {@code friendlyName} used in the GUI combo-boxes and
 * display strings. The {@code name()} value is what gets persisted to JSON.
 */
@AllArgsConstructor
@Getter
public enum ActionType {

    /** Fire an HTTP request to a saved webhook definition. */
    CALL_WEBHOOK("Call Webhook"),
    /** Launch a saved program entry (path + optional arguments). */
    OPEN_PROGRAM("Open Program"),
    /** Open a URL in the default system browser. */
    OPEN_URL("Open URL"),
    /** Simulate keyboard input by pasting text via the clipboard + Ctrl+V. */
    TYPE_TEXT("Type Text"),
    /** Place text on the system clipboard without pasting. */
    COPY_TO_CLIPBOARD("Copy to Clipboard"),
    /** Display a system-tray balloon notification. */
    SHOW_NOTIFICATION("Show Notification"),
    /** Overwrite a file with new content on each trigger. */
    WRITE_TO_FILE("Write to File"),
    /** Append a line to a file on each trigger. */
    APPEND_TO_FILE("Append to File"),
    /** Play a {@code .wav} file, or the system beep if no path is given. */
    PLAY_SOUND("Play Sound"),
    /** Set the internal pending-state variable to the given value. */
    SET_STATE("Set State"),
    /** Clear the internal pending-state variable. No value field is used. */
    CLEAR_STATE("Clear State");

    private final String friendlyName;

    /**
     * Returns all friendly names as a plain array, suitable for ImGui combo-boxes.
     */
    public static String[] asFriendlyArray() {
        return Arrays.stream(ActionType.values()).map(ActionType::getFriendlyName).toArray(String[]::new);
    }

    /**
     * Returns the constant whose {@code name()} matches {@code name} (case-insensitive),
     * or {@code null} if no match is found.
     */
    public static ActionType getByName(String name) {
        return EnumUtil.getByName(ActionType.class, name);
    }

}
