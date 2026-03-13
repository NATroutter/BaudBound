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

    final String friendlyName;


    public static String[] asArray() {
        return Arrays.stream(ConditionType.values()).map(ConditionType::name).toArray(String[]::new);
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
