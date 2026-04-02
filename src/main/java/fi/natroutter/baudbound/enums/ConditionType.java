package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * All condition types that can be applied to incoming input before an event fires.
 * <p>
 * String-matching conditions respect the per-condition case-sensitivity flag.
 * Numeric conditions ({@code GREATER_THAN}, {@code LESS_THAN}, {@code BETWEEN},
 * {@code IS_NUMERIC}) do not use case sensitivity and require the input to be parseable
 * as a {@code double}.
 * <p>
 * State conditions check named entries in the internal state map set by
 * {@code SET_STATE} / {@code CLEAR_STATE} actions.
 * The value field holds an optional state name; if left blank the {@code "default"} state
 * is used. {@code STATE_EQUALS} / {@code STATE_NOT_EQUALS} additionally take a
 * {@code |}-separated expected value (format: {@code stateName|expectedValue} or just
 * {@code expectedValue} for the default state). Numeric state conditions
 * ({@code STATE_IS_NUMERIC}, {@code STATE_LESS_THAN}, {@code STATE_GREATER_THAN},
 * {@code STATE_BETWEEN}) use the same {@code stateName|threshold} format, where the
 * threshold for {@code STATE_BETWEEN} is {@code min,max}.
 * <p>
 * WebSocket parameter conditions ({@code WEBSOCKET_HAS_PARAM}, {@code WEBSOCKET_PARAM_EQUALS},
 * {@code WEBSOCKET_PARAM_NOT_EQUALS}, {@code WEBSOCKET_PARAM_CONTAINS}) parse the input as a
 * URL-style query string ({@code key=value&key2=value2}) and match against named parameters.
 * Value format: {@code paramName} for {@code HAS_PARAM}, or {@code paramName|expectedValue}
 * for the others.
 */
@AllArgsConstructor
@Getter
public enum ConditionType {

    /** Input must start with the given value. */
    INPUT_STARTS_WITH("Input Starts with"),
    /** Input must end with the given value. */
    INPUT_ENDS_WITH("Input Ends with"),
    /** Input must contain the given value. */
    INPUT_CONTAINS("Input Contains"),
    /** Input must not contain the given value. */
    INPUT_NOT_CONTAINS("Input Not Contains"),
    /** Input must not start with the given value. */
    INPUT_NOT_STARTS_WITH("Input Not Starts With"),
    /** Input must exactly equal the given value. */
    INPUT_EQUALS("Input Equals"),
    /** Input must match the given regular expression. */
    INPUT_REGEX("Input Regex Match"),
    /** Input must be parseable as a number. No value field is used. */
    INPUT_IS_NUMERIC("Input Is Numeric"),
    /** Input (as a number) must be greater than the given value. */
    INPUT_GREATER_THAN("Input Greater Than"),
    /** Input (as a number) must be less than the given value. */
    INPUT_LESS_THAN("Input Less Than"),
    /** Input (as a number) must fall within the given {@code min,max} range (inclusive). */
    INPUT_BETWEEN("Input Between (min,max)"),
    /** Input length must equal the given integer. */
    INPUT_LENGTH_EQUALS("Input Length Equals"),
    /**
     * The named state must equal the expected value.
     * Value format: {@code expectedValue} (checks the default state) or
     * {@code stateName|expectedValue} (checks a named state).
     */
    STATE_EQUALS("State Equals"),
    /**
     * The named state must NOT equal the expected value.
     * Value format: {@code expectedValue} (checks the default state) or
     * {@code stateName|expectedValue} (checks a named state).
     */
    STATE_NOT_EQUALS("State Not Equals"),
    /**
     * The named state must be unset or blank.
     * Value format: blank (checks the default state) or {@code stateName} (checks a named state).
     */
    STATE_IS_EMPTY("State Is Empty"),
    /**
     * The named state value must be parseable as a number.
     * Value format: blank (checks the default state) or {@code stateName}.
     */
    STATE_IS_NUMERIC("State Is Numeric"),
    /**
     * The named state value (as a number) must be less than the given threshold.
     * Value format: {@code threshold} (default state) or {@code stateName|threshold}.
     */
    STATE_LESS_THAN("State Less Than"),
    /**
     * The named state value (as a number) must be greater than the given threshold.
     * Value format: {@code threshold} (default state) or {@code stateName|threshold}.
     */
    STATE_GREATER_THAN("State Greater Than"),
    /**
     * The named state value (as a number) must fall within the given {@code min,max} range (inclusive).
     * Value format: {@code min,max} (default state) or {@code stateName|min,max}.
     */
    STATE_BETWEEN("State Between (min,max)"),
    /**
     * The input must have originated from the device whose custom name matches the stored value.
     * Value: comma-separated device custom names as configured in the Devices dialog.
     */
    DEVICE_EQUALS("Device Equals"),
    /**
     * The input must NOT have originated from any of the listed devices.
     * Value: comma-separated device custom names as configured in the Devices dialog.
     */
    DEVICE_NOT_EQUALS("Device Not Equals"),
    /**
     * The WebSocket message must contain a parameter with the given name.
     * Message format: {@code key=value&key2=value2}.
     * Value: the parameter name to look for.
     */
    WEBSOCKET_HAS_PARAM("WebSocket Has Parameter"),
    /**
     * The named WebSocket parameter must equal the expected value.
     * Value format: {@code paramName|expectedValue}.
     */
    WEBSOCKET_PARAM_EQUALS("WebSocket Parameter Equals"),
    /**
     * The named WebSocket parameter must NOT equal the expected value.
     * Value format: {@code paramName|expectedValue}.
     */
    WEBSOCKET_PARAM_NOT_EQUALS("WebSocket Parameter Not Equals"),
    /**
     * The named WebSocket parameter must contain the expected value.
     * Value format: {@code paramName|expectedValue}.
     */
    WEBSOCKET_PARAM_CONTAINS("WebSocket Parameter Contains"),
    /**
     * The named WebSocket parameter must start with the expected value.
     * Value format: {@code paramName|expectedValue}.
     */
    WEBSOCKET_PARAM_STARTS_WITH("WebSocket Parameter Starts With"),
    /**
     * The named WebSocket parameter must end with the expected value.
     * Value format: {@code paramName|expectedValue}.
     */
    WEBSOCKET_PARAM_ENDS_WITH("WebSocket Parameter Ends With"),
    /**
     * The WebSocket channel path must equal the expected value.
     * Value: the expected channel path (e.g. {@code /sensors/temp}).
     */
    WEBSOCKET_CHANNEL_EQUALS("WebSocket Channel Equals"),
    /**
     * The WebSocket channel path must start with the expected value.
     * Value: the channel prefix (e.g. {@code /sensors}).
     */
    WEBSOCKET_CHANNEL_STARTS_WITH("WebSocket Channel Starts With"),
    /**
     * The WebSocket channel path must contain the expected value.
     * Value: a substring of the channel path (e.g. {@code /sensors}).
     */
    WEBSOCKET_CHANNEL_CONTAINS("WebSocket Channel Contains"),
    /**
     * The WebSocket channel path must NOT equal the expected value.
     * Value: the expected channel path (e.g. {@code /sensors/temp}).
     */
    WEBSOCKET_CHANNEL_NOT_EQUALS("WebSocket Channel Not Equals");

    private final String friendlyName;

    /**
     * Returns true if this condition type uses the secondary value field (field[1]).
     * {@code IS_NUMERIC} and {@code WEBSOCKET_HAS_PARAM} only need the primary/name field.
     */
    public boolean requiresValue() {
        return this != INPUT_IS_NUMERIC && this != WEBSOCKET_HAS_PARAM;
    }

    /**
     * Returns true if this condition type supports the case-sensitivity toggle.
     * Numeric, regex, state, and device types handle their own case or don't operate on text.
     */
    public boolean supportsCaseSensitivity() {
        return requiresValue()
                && this != INPUT_REGEX
                && this != INPUT_GREATER_THAN
                && this != INPUT_LESS_THAN
                && this != INPUT_BETWEEN
                && this != INPUT_LENGTH_EQUALS
                && this != STATE_EQUALS
                && this != STATE_NOT_EQUALS
                && this != STATE_IS_EMPTY
                && this != STATE_IS_NUMERIC
                && this != STATE_LESS_THAN
                && this != STATE_GREATER_THAN
                && this != STATE_BETWEEN
                && this != DEVICE_EQUALS
                && this != DEVICE_NOT_EQUALS
                && this != WEBSOCKET_HAS_PARAM;
    }

    /** Returns all friendly names as a plain array, suitable for ImGui combo-boxes. */
    public static String[] asFriendlyArray() {
        return Arrays.stream(ConditionType.values()).map(ConditionType::getFriendlyName).toArray(String[]::new);
    }

    /**
     * Returns the constant whose {@code name()} matches {@code name} (case-insensitive),
     * or {@code null} if no match is found.
     */
    public static ConditionType getByName(String name) {
        return EnumUtil.getByName(ConditionType.class, name);
    }

    /**
     * Returns the ordinal index of the constant whose {@code name()} matches {@code name}
     * (case-insensitive), or {@code 0} if no match is found.
     */
    public static int findIndex(String name) {
        return EnumUtil.findIndex(ConditionType.class, name);
    }
}
