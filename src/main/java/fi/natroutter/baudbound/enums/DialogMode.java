package fi.natroutter.baudbound.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Controls whether an editor dialog is opened for creating a new item or editing an existing one.
 * The {@code type} string is used as the dialog window title prefix (e.g. "Create Event").
 */
@AllArgsConstructor
@Getter
public enum DialogMode {
    /** Dialog is creating a new item. */
    CREATE("Create"),
    /** Dialog is modifying an existing item. */
    EDIT("Edit");
    private String type;
}
