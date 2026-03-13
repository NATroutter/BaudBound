package fi.natroutter.baudbound.gui.dialog.components;

import lombok.Data;

/**
 * Descriptor for a button rendered inside {@link fi.natroutter.baudbound.gui.dialog.MessageDialog}.
 * The {@link #action} is invoked when the button is clicked; it may be {@code null} if
 * the button should only dismiss the dialog without performing any side effect.
 */
@Data
public class DialogButton {
    /** The label shown on the button. */
    public String label;
    /** Optional callback invoked before the dialog closes. May be {@code null}. */
    public Runnable action;

    /**
     * Creates a button with a label and a click callback.
     *
     * @param label  the button text
     * @param action the action to run on click, or {@code null} for dismiss-only
     */
    public DialogButton(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    /**
     * Creates a dismiss-only button with no action.
     *
     * @param label the button text
     */
    public DialogButton(String label) {
        this(label, null);
    }
}