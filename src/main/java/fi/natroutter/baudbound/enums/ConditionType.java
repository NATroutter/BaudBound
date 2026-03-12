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
    REGEX("Regex Match");

    String friendlyName;


    public static String[] asArray() {
        return Arrays.stream(ConditionType.values()).map(ConditionType::name).toArray(String[]::new);
    }

    public static String[] asFriendlyArray() {
        return Arrays.stream(ConditionType.values()).map(ConditionType::getFriendlyName).toArray(String[]::new);
    }

    public static ConditionType getByName(String name) {
        for (ConditionType event : ConditionType.values()) {
            if (event.name().equalsIgnoreCase(name)) {
                return event;
            }
        }
        return null;
    }

    public static int findIndex(String name) {
        ConditionType[] event = ConditionType.values();
        for (int i = 0; i < event.length; i++) {
            if (event[i].name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }
}
