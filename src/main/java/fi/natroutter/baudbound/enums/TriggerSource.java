package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Enumerates all supported trigger sources that can cause an event to fire.
 * <p>
 * Each {@link fi.natroutter.baudbound.storage.DataStore.Event} stores a list of
 * {@link #name()} strings; an event fires only when the incoming
 * {@link fi.natroutter.baudbound.event.TriggerContext} source is in that list.
 * Events with a null or empty source list default to {@link #SERIAL} only
 * (backward compatibility with configs written before this field existed).
 */
@AllArgsConstructor
@Getter
public enum TriggerSource {

    /** Input read from a serial port. */
    SERIAL("Serial Input"),

    /** Message received by the built-in WebSocket server. */
    WEBSOCKET("WebSocket"),

    /** A configured serial device successfully connected. */
    DEVICE_CONNECTED("Device Connected"),

    /** A configured serial device disconnected (unexpected or explicit). */
    DEVICE_DISCONNECTED("Device Disconnected");

    private final String friendlyName;

    /**
     * Short label used in the event table "Trigger Sources" column.
     * Kept brief so multiple sources fit in the column width.
     */
    public String shortLabel() {
        return switch (this) {
            case SERIAL              -> "Serial";
            case WEBSOCKET           -> "WS";
            case DEVICE_CONNECTED    -> "Connected";
            case DEVICE_DISCONNECTED -> "Disconnected";
        };
    }

    /** Returns all friendly names as a plain array, suitable for ImGui combo-boxes. */
    public static String[] asFriendlyArray() {
        return Arrays.stream(values()).map(TriggerSource::getFriendlyName).toArray(String[]::new);
    }

    /**
     * Returns the constant whose {@code name()} matches {@code name} (case-insensitive),
     * or {@code null} if no match is found.
     */
    public static TriggerSource getByName(String name) {
        return EnumUtil.getByName(TriggerSource.class, name);
    }

    /**
     * Returns the ordinal index of the constant whose {@code name()} matches {@code name}
     * (case-insensitive), or {@code 0} if no match is found.
     */
    public static int findIndex(String name) {
        return EnumUtil.findIndex(TriggerSource.class, name);
    }
}
