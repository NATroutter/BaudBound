package fi.natroutter.baudbound.gui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * Base class for all ImGui modal dialogs.
 * <p>
 * Subclasses call {@link #beginModal} each frame; if it returns {@code true} they render
 * their content and must call {@link #endModal} before returning. To open the dialog call
 * {@link #show()} (or {@link #requestOpen()} from a subclass), which sets a flag consumed
 * on the next frame so that {@code ImGui.openPopup} is called at the right point in the
 * ImGui frame cycle.
 * <p>
 * X-button dismissal is detected by watching the {@code modalOpen} flag transition from
 * {@code true} to {@code false} while the popup was open; this triggers {@link #onClose()},
 * which editor dialogs override to navigate back to their parent list dialog.
 */
public abstract class BaseDialog {

    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);

    /** Schedules the dialog to open on the next rendered frame. */
    protected void requestOpen() {
        this.open = true;
    }

    /**
     * Opens and begins rendering the modal at 90% display width with auto height.
     *
     * @param title the popup title and ImGui ID
     * @return {@code true} if the modal is open and content should be rendered
     */
    protected boolean beginModal(String title) {
        return beginModal(title, 0, Float.MAX_VALUE);
    }

    /**
     * Opens and begins rendering the modal at 90% display width with a fixed height.
     *
     * @param title  the popup title and ImGui ID
     * @param fixedH the exact height in pixels
     * @return {@code true} if the modal is open and content should be rendered
     */
    protected boolean beginModal(String title, float fixedH) {
        return beginModal(title, fixedH, fixedH);
    }

    protected boolean beginModal(String title, float minH, float maxH) {
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();
        ImGui.setNextWindowPos(displayW / 2, displayH / 2, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowSizeConstraints(displayW * 0.9f, minH, displayW * 0.9f, maxH);

        if (open) {
            ImGui.openPopup(title);
            modalOpen.set(true);
            open = false;
        }
        boolean wasOpen = ImGui.isPopupOpen(title);
        if (ImGui.beginPopupModal(title, modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {
            return true;
        }
        if (wasOpen && !modalOpen.get()) {
            modalOpen.set(true);
            onClose();
        }
        return false;
    }

    /** Ends the modal popup. Must be called if {@link #beginModal} returned {@code true}. */
    protected void endModal() {
        ImGui.endPopup();
    }

    /** Called when the dialog is dismissed via the X button. Override to navigate back to a parent dialog. */
    protected void onClose() {}

    /**
     * Renders the dialog content each frame. Must be called from the GLFW main thread.
     * Subclasses should call {@link #beginModal} / {@link #endModal} inside this method.
     */
    public abstract void render();

    public void show() {
        requestOpen();
    }
}