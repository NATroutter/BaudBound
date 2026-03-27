package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * All condition types that can be applied to incoming serial input before an event fires.
 * <p>
 * String-matching conditions respect the per-condition case-sensitivity flag.
 * Numeric conditions ({@code GREATER_THAN}, {@code LESS_THAN}, {@code BETWEEN},
 * {@code IS_NUMERIC}) do not use case sensitivity and require the input to be parseable
 * as a {@code double}.
 * <p>
 * State conditions ({@code STATE_EQUALS}, {@code STATE_IS_EMPTY}) check named entries
 * in the internal state map set by {@code SET_STATE} / {@code CLEAR_STATE} actions.
 * The value field holds an optional state name; if left blank the {@code "default"} state
 * is used. {@code STATE_EQUALS} additionally takes a {@code |}-separated expected value
 * (format: {@code stateName|expectedValue} or just {@code expectedValue} for the default state).
 */
@AllArgsConstructor
@Getter
public enum ConditionType {

    /** Input must start with the given value. */
    STARTS_WITH("Starts with"),
    /** Input must end with the given value. */
    ENDS_WITH("Ends with"),
    /** Input must contain the given value. */
    CONTAINS("Contains"),
    /** Input must not contain the given value. */
    NOT_CONTAINS("Not Contains"),
    /** Input must not start with the given value. */
    NOT_STARTS_WITH("Not Starts With"),
    /** Input must exactly equal the given value. */
    EQUALS("Equals"),
    /** Input must match the given regular expression. */
    REGEX("Regex Match"),
    /** Input must be parseable as a number. No value field is used. */
    IS_NUMERIC("Is Numeric"),
    /** Input (as a number) must be greater than the given value. */
    GREATER_THAN("Greater Than"),
    /** Input (as a number) must be less than the given value. */
    LESS_THAN("Less Than"),
    /** Input (as a number) must fall within the given {@code min,max} range (inclusive). */
    BETWEEN("Between (min,max)"),
    /** Input length must equal the given integer. */
    LENGTH_EQUALS("Length Equals"),
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
     * The input must have originated from the device whose custom name matches the stored value.
     * Value: comma-separated device custom names as configured in the Devices dialog.
     */
    DEVICE_EQUALS("Device Equals"),
    /**
     * The input must NOT have originated from any of the listed devices.
     * Value: comma-separated device custom names as configured in the Devices dialog.
     */
    DEVICE_NOT_EQUALS("Device Not Equals");

    private final String friendlyName;

    /** Returns true if this condition type uses a value field. */
    public boolean requiresValue() {
        return this != IS_NUMERIC;
    }

    /**
     * Returns true if this condition type supports the case-sensitivity toggle.
     * Numeric, regex, and state types handle their own case or don't operate on text.
     */
    public boolean supportsCaseSensitivity() {
        return requiresValue()
                && this != REGEX
                && this != GREATER_THAN
                && this != LESS_THAN
                && this != BETWEEN
                && this != LENGTH_EQUALS
                && this != STATE_EQUALS
                && this != STATE_NOT_EQUALS
                && this != STATE_IS_EMPTY
                && this != DEVICE_EQUALS
                && this != DEVICE_NOT_EQUALS;
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
