package fi.natroutter.baudbound.gui.dialog.components;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DialogMode {
    CREATE("Create"),
    EDIT("Edit");
    private String type;
}
