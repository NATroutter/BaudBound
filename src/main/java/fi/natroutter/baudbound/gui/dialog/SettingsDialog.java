package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.DebugOverlay;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.system.ShortcutManager;
import fi.natroutter.baudbound.system.StartupManager;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import javax.swing.*;


/**
 * Modal settings dialog covering General and Event configuration sections,
 * plus the Utilities shortcut-creator.
 * <p>
 * Device configuration has moved to the Devices dialog ({@code DevicesDialog}).
 * <p>
 * ImGui state fields (prefixed {@code option}) shadow the persisted settings and are
 * populated by {@link #load()} when the dialog opens. {@link #save()} writes them back
 * to {@link DataStore} and persists to disk.
 */
public class SettingsDialog extends BaseDialog {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImBoolean optionStartWithOS          = new ImBoolean(false);
    private final ImBoolean optionStartHidden          = new ImBoolean(false);
    private final ImBoolean optionRunFirstEventOnly    = new ImBoolean(false);
    private final ImBoolean optionConditionEventsFirst = new ImBoolean(false);
    private final ImBoolean optionSkipEmptyConditions  = new ImBoolean(false);
    private final ImBoolean optionVsync                = new ImBoolean(true);
    private final ImInt     optionFpsLimit             = new ImInt(60);
    private final ImBoolean optionDebugOverlay         = new ImBoolean(false);
    private final ImInt     optionDebugSampleInterval  = new ImInt(2); // default 100 ms

    private volatile boolean creatingShortcut = false;

    public SettingsDialog() {
        load();
    }

    public void render() {
        if (beginModal("Settings")) {

            ImGui.separatorText("General Settings");

            ImGui.checkbox("Start with the OS", optionStartWithOS);
            GuiHelper.toolTip("Automatically launch BaudBound when operating system starts.");

            ImGui.checkbox("Start hidden", optionStartHidden);
            GuiHelper.toolTip("Start minimized to the system tray instead of showing the window.");

            ImGui.separatorText("Event Settings");

            ImGui.checkbox("Run first only", optionRunFirstEventOnly);
            GuiHelper.toolTip("Enable to trigger only the first matching event.\n" +
                    "Disable to run all events whose conditions match.");

            ImGui.beginDisabled(optionSkipEmptyConditions.get());
            ImGui.checkbox("Process conditional events first", optionConditionEventsFirst);
            GuiHelper.toolTip(optionSkipEmptyConditions.get()
                    ? "Not needed — all unconditioned events are already being skipped."
                    : "When enabled, events with conditions are always evaluated before\nevents without conditions, regardless of their order in the list.");
            ImGui.endDisabled();

            ImGui.checkbox("Skip events without conditions", optionSkipEmptyConditions);
            GuiHelper.toolTip("When enabled, events that have no conditions set are ignored entirely.");

            ImGui.separatorText("Graphics");

            ImGui.checkbox("Vertical Sync (VSync)", optionVsync);
            GuiHelper.toolTip("Locks the frame rate to your monitor's refresh rate.\n" +
                    "Disabling VSync allows setting a custom FPS limit.");

            ImGui.beginDisabled(optionVsync.get());
            ImGui.text("FPS Limit");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            int[] fpsCapArr = {optionFpsLimit.get()};
            if (ImGui.sliderInt("##fpscap", fpsCapArr, 1, 240)) {
                optionFpsLimit.set(fpsCapArr[0]);
            }
            GuiHelper.toolTip(optionVsync.get()
                    ? "Enable a custom FPS limit by disabling VSync first."
                    : "Maximum frames per second when VSync is disabled.");
            ImGui.endDisabled();

            ImGui.separatorText("Debug");

            ImGui.checkbox("Debug Overlay", optionDebugOverlay);
            GuiHelper.toolTip("Shows a real-time overlay with FPS, frame time, memory usage, and device status.");

            ImGui.text("Graph Sample Interval");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##debugsampleinterval", optionDebugSampleInterval, DebugOverlay.INTERVAL_LABELS);
            GuiHelper.toolTip("How often the performance graphs in the debug overlay are sampled.\n" +
                    "Lower intervals give smoother graphs; higher intervals make spikes easier to see.");

            ImGui.separatorText("Utilities");

            ImGui.beginDisabled(creatingShortcut);
            if (ImGui.button(creatingShortcut ? "Selecting folder..." : "Create Shortcut...",
                    new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                openShortcutDialog();
            }
            ImGui.endDisabled();
            GuiHelper.toolTip("Create a shortcut to BaudBound in a folder of your choice.\n" +
                    "Opens in the startup folder by default.");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                save();
            }

            endModal();
        }
    }

    /**
     * Opens a Swing {@link javax.swing.JFileChooser} on a virtual thread (to avoid blocking
     * the GLFW main thread) and creates the shortcut in the selected folder.
     * <p>
     * An invisible always-on-top {@link javax.swing.JFrame} is used as the parent so the
     * chooser appears in front of the GLFW window.
     */
    private void openShortcutDialog() {
        creatingShortcut = true;
        Thread.ofVirtual().start(() -> {
            try {
                String[] selectedFolder = {null};
                SwingUtilities.invokeAndWait(() -> {
                    JFrame parent = new JFrame();
                    parent.setUndecorated(true);
                    parent.setAlwaysOnTop(true);
                    parent.setVisible(true);
                    parent.setLocationRelativeTo(null);

                    JFileChooser chooser = new JFileChooser(ShortcutManager.defaultFolderPath());
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle("Select Shortcut Location");
                    if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                        selectedFolder[0] = chooser.getSelectedFile().getAbsolutePath();
                    }
                    parent.dispose();
                });

                if (selectedFolder[0] == null) {
                    creatingShortcut = false;
                    return;
                }

                ShortcutManager.createShortcut(selectedFolder[0]);
                creatingShortcut = false;
                BaudBound.getMessageDialog().show("Shortcut Created",
                        "Shortcut created successfully in:\n" + selectedFolder[0],
                        new DialogButton("OK", () -> {}));
            } catch (Exception e) {
                creatingShortcut = false;
                logger.error("Failed to create shortcut: " + e.getMessage());
                BaudBound.getMessageDialog().show("Error",
                        "Failed to create shortcut:\n" + e.getMessage(),
                        new DialogButton("OK", () -> {}));
            }
        });
    }

    /** Copies current {@link DataStore} values into the ImGui state fields. */
    private void load() {
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();
        DataStore.Settings.Graphics graphics = settings.getGraphics();
        DataStore.Settings.Debug debug = settings.getDebug();

        optionStartWithOS.set(StartupManager.isEnabled());
        optionStartHidden.set(generic.isStartHidden());

        optionRunFirstEventOnly.set(event.isRunFirstOnly());
        optionConditionEventsFirst.set(event.isConditionEventsFirst());
        optionSkipEmptyConditions.set(event.isSkipEmptyConditions());

        // Treat both fields unset (old config) as vsync-on for backwards compatibility
        boolean vsync = graphics.isVsync() || graphics.getFpsLimit() == 0;
        optionVsync.set(vsync);
        optionFpsLimit.set(graphics.getFpsLimit() > 0 ? graphics.getFpsLimit() : 60);

        optionDebugOverlay.set(debug.isOverlay());
        int sampleIdx = debug.getSampleIntervalIdx();
        optionDebugSampleInterval.set(sampleIdx > 0 ? sampleIdx : 2); // default 100 ms
    }

    /** Writes the current ImGui state fields back to {@link DataStore} and persists to disk. */
    private void save() {
        logger.info("Saving settings...");
        DataStore.Settings settings = storage.getData().getSettings();
        DataStore.Settings.Generic generic = settings.getGeneric();
        DataStore.Settings.Event event = settings.getEvent();

        try {
            StartupManager.setEnabled(optionStartWithOS.get());
        } catch (Exception e) {
            logger.error("Failed to update startup registration: " + e.getMessage());
            optionStartWithOS.set(false);
        }
        generic.setStartHidden(optionStartHidden.get());

        event.setRunFirstOnly(optionRunFirstEventOnly.get());
        event.setConditionEventsFirst(optionConditionEventsFirst.get());
        event.setSkipEmptyConditions(optionSkipEmptyConditions.get());

        DataStore.Settings.Graphics graphics = settings.getGraphics();
        graphics.setVsync(optionVsync.get());
        graphics.setFpsLimit(optionVsync.get() ? 0 : optionFpsLimit.get());

        DataStore.Settings.Debug debug = settings.getDebug();
        debug.setOverlay(optionDebugOverlay.get());
        debug.setSampleIntervalIdx(optionDebugSampleInterval.get());

        storage.save();
        BaudBound.applyVsync(); // safe — save() is called on the GLFW render thread
        BaudBound.getMessageDialog().show("Saved", "Settings saved successfully.", new DialogButton("OK", this::requestOpen));
    }
}