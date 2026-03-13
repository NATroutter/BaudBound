package fi.natroutter.baudbound.gui.dialog;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

public abstract class BaseDialog {

    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);

    protected void requestOpen() {
        this.open = true;
    }

    /** 90% width, auto height */
    protected boolean beginModal(String title) {
        return beginModal(title, 0, Float.MAX_VALUE);
    }

    /** 90% width, fixed height (minH == maxH) */
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

    protected void endModal() {
        ImGui.endPopup();
    }

    /** Called when the dialog is dismissed via the X button. Override to navigate back to a parent dialog. */
    protected void onClose() {}

    public abstract void render();

    public void show() {
        requestOpen();
    }
}