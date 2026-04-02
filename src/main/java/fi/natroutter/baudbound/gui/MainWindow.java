package fi.natroutter.baudbound.gui;

/**
 * Top-level render coordinator for the GLFW frame.
 * <p>
 * Renders the standalone main menu bar via {@link MenuBar}. All content panels
 * (Events, Logs, States, etc.) are independent floating windows managed by their
 * own {@link BaseWindow} subclasses and rendered directly from
 * {@link fi.natroutter.baudbound.BaudBound#process()}.
 */
public class MainWindow {

    private final MenuBar menuBar = new MenuBar();

    /** Renders the main menu bar. Must be called from the GLFW main thread each frame. */
    public void render() {
        menuBar.render();
    }
}
