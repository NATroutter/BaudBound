package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@AllArgsConstructor
@Getter
public enum ConditionType {

    STARTS_WITH("Starts with"),
    ENDS_WITH("Ends with"),
    CONTAINS("Contains"),
    NOT_CONTAINS("Not Contains"),
    NOT_STARTS_WITH("Not Starts With"),
    EQUALS("Equals"),
    REGEX("Regex Match"),
    IS_NUMERIC("Is Numeric"),
    GREATER_THAN("Greater Than"),
    LESS_THAN("Less Than"),
    BETWEEN("Between (min,max)"),
    LENGTH_EQUALS("Length Equals");

    private final String friendlyName;

    /** Returns true if this condition type uses a value field. */
    public boolean requiresValue() {
        return this != IS_NUMERIC;
    }

    /**
     * Returns true if this condition type supports the case-sensitivity toggle.
     * Numeric and regex types handle their own case or don't operate on text.
     */
    public boolean supportsCaseSensitivity() {
        return requiresValue()
                && this != REGEX
                && this != GREATER_THAN
                && this != LESS_THAN
                && this != BETWEEN
                && this != LENGTH_EQUALS;
    }

    public static String[] asFriendlyArray() {
        return Arrays.stream(ConditionType.values()).map(ConditionType::getFriendlyName).toArray(String[]::new);
    }

    public static ConditionType getByName(String name) {
        return EnumUtil.getByName(ConditionType.class, name);
    }

    public static int findIndex(String name) {
        return EnumUtil.findIndex(ConditionType.class, name);
    }
}
