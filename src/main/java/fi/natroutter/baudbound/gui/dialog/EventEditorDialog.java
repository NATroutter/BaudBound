package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.enums.NodeType;
import fi.natroutter.baudbound.gui.NodeEditorCanvas;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/**
 * Near-fullscreen modal editor for creating and modifying {@link DataStore.Event} entries
 * using a Blueprint-style node graph.
 *
 * <p>The dialog is divided into two regions:
 * <ul>
 *   <li>A 160 px fixed sidebar on the left with collapsing category headers (Triggers,
 *       Conditions, Actions, Values) and a spawn button per {@link NodeType}.</li>
 *   <li>A canvas area on the right backed by {@link NodeEditorCanvas}, with a header
 *       strip containing the event name field, Save, and Cancel buttons.</li>
 * </ul>
 *
 * <p>Supports both {@link DialogMode#CREATE} and {@link DialogMode#EDIT} modes, selected
 * via {@link #show(DialogMode, DataStore.Event)}.
 *
 * <p>This class bypasses {@link BaseDialog#beginModal} because it needs a fixed
 * near-fullscreen size; it manages the popup lifecycle directly.
 */
public class EventEditorDialog extends BaseDialog {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final NodeEditorCanvas canvas = new NodeEditorCanvas();

    private DialogMode mode = DialogMode.CREATE;
    /** Working copy of the event being edited. */
    private DataStore.Event editingEvent = null;
    /** Original event reference used for in-place replacement in EDIT mode. */
    private DataStore.Event sourceEvent = null;

    private final ImString fieldName = new ImString(128);
    /** Set to {@code true} on the frame when the modal should be opened. */
    private boolean pendingOpen = false;
    private final ImBoolean pendingModalOpen = new ImBoolean(false);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Opens the dialog in {@link DialogMode#CREATE} mode with an empty event.
     */
    @Override
    public void show() {
        show(DialogMode.CREATE, null);
    }

    /**
     * Opens the dialog in the specified mode.
     *
     * @param dialogMode {@link DialogMode#CREATE} to start empty, or
     *                   {@link DialogMode#EDIT} to edit an existing event
     * @param event      the event to edit (required for EDIT mode; ignored for CREATE)
     */
    public void show(DialogMode dialogMode, DataStore.Event event) {
        this.mode = dialogMode;
        this.sourceEvent = event;

        // Make a working copy so the user can cancel without mutating live data
        if (dialogMode == DialogMode.EDIT && event != null) {
            editingEvent = event.deepCopy();
            fieldName.set(event.getName() != null ? event.getName() : "");
        } else {
            editingEvent = new DataStore.Event();
            editingEvent.setNodes(new ArrayList<>());
            editingEvent.setConnections(new ArrayList<>());
            fieldName.set("");
        }
        canvas.resetView();
        pendingOpen = true;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    /**
     * Renders the dialog each frame. Must be called from the GLFW main thread.
     *
     * <p>Bypasses {@link BaseDialog#beginModal} to achieve a fixed near-fullscreen
     * size; manages the ImGui popup lifecycle directly.
     */
    @Override
    public void render() {
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();

        if (pendingOpen) {
            ImGui.openPopup("Event Editor##evtedit");
            pendingModalOpen.set(true);
            pendingOpen = false;
        }

        float w = displayW * 0.95f;
        float h = displayH * 0.90f;
        ImGui.setNextWindowPos(displayW / 2f, displayH / 2f, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowSize(w, h, ImGuiCond.Always);

        boolean wasOpen = ImGui.isPopupOpen("Event Editor##evtedit");
        if (!ImGui.beginPopupModal("Event Editor##evtedit", pendingModalOpen,
                ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove)) {
            if (wasOpen && !pendingModalOpen.get()) {
                pendingModalOpen.set(true);
                onClose();
            }
            return;
        }

        renderContent(w, h);
        ImGui.endPopup();
    }

    // -------------------------------------------------------------------------
    // Private rendering
    // -------------------------------------------------------------------------

    /**
     * Renders the two-column layout: sidebar on the left, canvas area on the right.
     *
     * @param totalW total modal width in pixels
     * @param totalH total modal height in pixels
     */
    private void renderContent(float totalW, float totalH) {
        float sidebarW = 160f;
        float canvasW  = totalW - sidebarW - 16f; // 16 for padding/border

        // --- Sidebar ---
        ImGui.beginChild("##sidebar", sidebarW, totalH - 40f, true);
        renderSidebar();
        ImGui.endChild();

        ImGui.sameLine();

        // --- Canvas area ---
        ImGui.beginChild("##canvasArea", canvasW, totalH - 40f, false);

        // Header strip: event name field + Save + Cancel
        ImGui.spacing();
        ImGui.setNextItemWidth(canvasW - 160f);
        ImGui.inputText("##evtname", fieldName);
        ImGui.sameLine();
        if (ImGui.button("Save")) {
            saveEvent();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }

        // Node editor canvas
        canvas.render(editingEvent, storage);

        ImGui.endChild();
    }

    /**
     * Renders the node-type palette sidebar with collapsing category headers.
     * Clicking a button spawns a node of that type onto the canvas.
     */
    private void renderSidebar() {
        // Triggers
        if (ImGui.collapsingHeader("Triggers")) {
            spawnButton(NodeType.SERIAL_TRIGGER);
            spawnButton(NodeType.WEBSOCKET_TRIGGER);
            spawnButton(NodeType.DEVICE_CONNECTED);
            spawnButton(NodeType.DEVICE_DISCONNECTED);
        }
        // Conditions
        if (ImGui.collapsingHeader("Conditions")) {
            spawnButton(NodeType.EQUALS);
            spawnButton(NodeType.NOT_EQUALS);
            spawnButton(NodeType.CONTAINS);
            spawnButton(NodeType.NOT_CONTAINS);
            spawnButton(NodeType.STARTS_WITH);
            spawnButton(NodeType.NOT_STARTS_WITH);
            spawnButton(NodeType.ENDS_WITH);
            spawnButton(NodeType.NOT_ENDS_WITH);
            spawnButton(NodeType.REGEX);
            spawnButton(NodeType.GREATER_THAN);
            spawnButton(NodeType.LESS_THAN);
            spawnButton(NodeType.BETWEEN);
            spawnButton(NodeType.IS_NUMERIC);
            spawnButton(NodeType.IS_EMPTY);
        }
        // Actions
        if (ImGui.collapsingHeader("Actions")) {
            spawnButton(NodeType.WEBHOOK);
            spawnButton(NodeType.OPEN_PROGRAM);
            spawnButton(NodeType.OPEN_URL);
            spawnButton(NodeType.TYPE_TEXT);
            spawnButton(NodeType.SET_STATE);
            spawnButton(NodeType.CLEAR_STATE);
            spawnButton(NodeType.SEND_TO_DEVICE);
            spawnButton(NodeType.SEND_WEBSOCKET);
            spawnButton(NodeType.RUN_COMMAND);
            spawnButton(NodeType.COPY_TO_CLIPBOARD);
            spawnButton(NodeType.SHOW_NOTIFICATION);
            spawnButton(NodeType.WRITE_TO_FILE);
            spawnButton(NodeType.APPEND_TO_FILE);
            spawnButton(NodeType.PLAY_SOUND);
        }
        // Values
        if (ImGui.collapsingHeader("Values")) {
            spawnButton(NodeType.GET_STATE);
            spawnButton(NodeType.LITERAL);
        }
    }

    /**
     * Renders a full-width button for the given node type and spawns a node when clicked.
     *
     * @param nt the node type to spawn
     */
    private void spawnButton(NodeType nt) {
        if (ImGui.button(nt.getFriendlyName() + "##spawn_" + nt.name())) {
            canvas.spawnNode(editingEvent, storage, nt.name());
        }
    }

    // -------------------------------------------------------------------------
    // Save / close
    // -------------------------------------------------------------------------

    /**
     * Persists the working copy to storage. In EDIT mode the original event is replaced
     * in-place; in CREATE mode the new event is appended to the events list.
     */
    private void saveEvent() {
        String name = fieldName.get().trim();
        if (name.isEmpty()) name = "Unnamed Event";
        editingEvent.setName(name);

        if (mode == DialogMode.EDIT && sourceEvent != null) {
            List<DataStore.Event> events = storage.getData().getEvents();
            int idx = events.indexOf(sourceEvent);
            if (idx >= 0) {
                events.set(idx, editingEvent);
            } else {
                events.add(editingEvent);
            }
        } else {
            storage.getData().getEvents().add(editingEvent);
        }
        storage.save();
        BaudBound.getEventHandler().invalidateSortCache();
    }

    /**
     * Called when the dialog is dismissed via the X button.
     * Navigates back to the {@link fi.natroutter.baudbound.gui.windows.EventsWindow}.
     */
    @Override
    protected void onClose() {
        BaudBound.getEventsWindow().show();
    }
}
