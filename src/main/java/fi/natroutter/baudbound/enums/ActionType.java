package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@AllArgsConstructor
@Getter
public enum ActionType {

    CALL_WEBHOOK("Call Webhook"),
    OPEN_PROGRAM("Open Program"),
    OPEN_URL("Open URL"),
    TYPE_TEXT("Type Text");

    private String friendlyName;

    public static String[] asArray() {
        return Arrays.stream(ActionType.values()).map(ActionType::name).toArray(String[]::new);
    }

    public static String[] asFriendlyArray() {
        return Arrays.stream(ActionType.values()).map(ActionType::getFriendlyName).toArray(String[]::new);
    }

    public static ActionType getByName(String name) {
        for (ActionType event : ActionType.values()) {
            if (event.name().equalsIgnoreCase(name)) {
                return event;
            }
        }
        return null;
    }

    public static int findIndex(String name) {
        ActionType[] event = ActionType.values();
        for (int i = 0; i < event.length; i++) {
            if (event[i].name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }

}
