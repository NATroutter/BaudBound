package fi.natroutter.baudbound.gui;

import imgui.type.ImBoolean;

/**
 * Base class for floating, movable ImGui panel windows.
 * <p>
 * Unlike {@link fi.natroutter.baudbound.gui.dialog.BaseDialog} (which renders modal popups),
 * subclasses use {@code ImGui.begin(title, open)} to produce resizable, draggable windows
 * that can coexist on screen simultaneously.
 * <p>
 * Subclasses must call {@code ImGui.end()} unconditionally after {@code ImGui.begin()},
 * even when the window is collapsed or hidden.
 */
public abstract class BaseWindow {

    protected final ImBoolean open = new ImBoolean(false);

    /** Shows the window (sets open flag). */
    public void show() {
        open.set(true);
    }

    /** Hides the window. */
    public void close() {
        open.set(false);
    }

    /** Toggles the window visibility. Calls {@link #show()} when opening so subclass overrides that load state are honoured. */
    public void toggle() {
        if (open.get()) {
            close();
        } else {
            show();
        }
    }

    /** Returns {@code true} if the window is currently open. */
    public boolean isOpen() {
        return open.get();
    }

    /**
     * Renders the window each frame. Must be called every frame from the GLFW main thread.
     * Implementations should return early if {@code !open.get()} to avoid unnecessary work.
     */
    public abstract void render();
}
