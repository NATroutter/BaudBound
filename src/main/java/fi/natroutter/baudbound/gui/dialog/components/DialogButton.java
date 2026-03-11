package fi.natroutter.baudbound.gui.dialog.components;

import lombok.Data;

@Data
public class DialogButton {
    public String label;
    public Runnable action;

    public DialogButton(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public DialogButton(String label) {
        this(label, null);
    }
}