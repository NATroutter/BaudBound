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
    TYPE_TEXT("Type Text"),
    COPY_TO_CLIPBOARD("Copy to Clipboard"),
    SHOW_NOTIFICATION("Show Notification"),
    WRITE_TO_FILE("Write to File"),
    APPEND_TO_FILE("Append to File"),
    PLAY_SOUND("Play Sound");

    private final String friendlyName;

    public static String[] asFriendlyArray() {
        return Arrays.stream(ActionType.values()).map(ActionType::getFriendlyName).toArray(String[]::new);
    }

    public static ActionType getByName(String name) {
        return EnumUtil.getByName(ActionType.class, name);
    }

}
