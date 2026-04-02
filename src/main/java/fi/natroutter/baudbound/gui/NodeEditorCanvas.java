package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.enums.NodeType;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.nodeditor.NodeEditor;
import imgui.extension.nodeditor.NodeEditorContext;
import imgui.extension.nodeditor.flag.NodeEditorPinKind;
import imgui.extension.nodeditor.flag.NodeEditorStyleColor;
import imgui.flag.ImGuiCol;
import imgui.type.ImLong;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Node graph editor canvas backed by {@code imgui.extension.nodeditor.NodeEditor}
 * (imgui-node-editor by thedmd).
 *
 * <p>The extension handles pan, zoom, grid, bezier wires, drag-to-connect, and
 * selection/deletion. This class is responsible only for placing node and pin
 * content using standard ImGui widgets inside the extension's begin/end calls.
 *
 * <p>ID mapping: because NodeEditor uses {@code long} IDs we maintain a stable
 * {@link #idMap} from string keys to sequentially-assigned longs.
 * Keys use the format {@code "n:<uuid>"} for nodes, {@code "p:<uuid>:<pinId>"}
 * for pins, and {@code "l:<fromNodeId>:<fromPin>:<toNodeId>:<toPin>"} for links.
 *
 * <p>Must be called from the GLFW main thread each frame via {@link #render}.
 * Call {@link #dispose()} when the owning dialog is permanently torn down.
 */
public class NodeEditorCanvas {

    private NodeEditorContext context;

    /**
     * Stable string-key → long ID registry used by NodeEditor.
     * Key formats: {@code "n:<uuid>"}, {@code "p:<uuid>:<pinId>"},
     * {@code "l:<fromNodeId>:<fromPin>:<toNodeId>:<toPin>"}
     */
    private final Map<String, Long> idMap = new HashMap<>();
    /** Reverse lookup: pin long ID → {@code [nodeUuid, pinId]}. */
    private final Map<Long, String[]> pinLookup = new HashMap<>();
    /** Reverse lookup: link long ID → {@code "fromNodeId:fromPin:toNodeId:toPin"}. */
    private final Map<Long, String> linkLookup = new HashMap<>();
    /** Reverse lookup: node long ID → nodeUuid. */
    private final Map<Long, String> nodeLookup = new HashMap<>();

    private long nextId = 1L;

    /** Node IDs that have already had {@link NodeEditor#setNodePosition} applied. */
    private final Set<Long> initializedPositions = new HashSet<>();

    /** When {@code true}, {@link NodeEditor#navigateToContent()} is called at end of next frame. */
    private boolean pendingNavigate = false;

    /** Canvas-space coordinates of the visible center — updated every frame for spawn placement. */
    private float spawnX = 0f, spawnY = 0f;

    // ---- Layout constants ----
    /** Side length of the invisible dummy widget used as a pin hit-target. */
    private static final float PIN_SIZE   = 12f;
    /** Radius of the drawn pin circle. */
    private static final float PIN_RADIUS = 5f;
    /**
     * Minimum content width used as a floor for {@link ImGui#getContentRegionAvail()}.x
     * when right-aligning output pins on the first frame before node size is known.
     */
    private static final float NODE_MIN_W = 180f;

    // ---- Pin / icon colors (ABGR packed ints for ImDrawList) ----
    private static final int COL_PIN_EXEC   = 0xFFFFFFFF;
    private static final int COL_PIN_STRING = 0xFFB060FF;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Renders the node canvas for the given event. Must be called every frame from the GLFW thread.
     *
     * @param event   the event being edited (working copy; modified in-place)
     * @param storage used to persist structural changes (link/node create/delete)
     */
    public void render(DataStore.Event event, StorageProvider storage) {
        ensureContext();
        NodeEditor.setCurrentEditor(context);

        // Capture the screen position of the top-left corner of the editor widget before entering
        // the NodeEditor context, so we can compute the visible canvas center for spawn placement.
        ImVec2 editorTopLeft = ImGui.getCursorScreenPos();

        NodeEditor.begin("##ne");

        // Update spawn position to the canvas-space coordinates of the visible center.
        ImVec2 editorSize = NodeEditor.getScreenSize();
        ImVec2 center = NodeEditor.screenToCanvas(
                editorTopLeft.x + editorSize.x * 0.5f,
                editorTopLeft.y + editorSize.y * 0.5f);
        spawnX = center.x;
        spawnY = center.y;

        if (event.getNodes() != null) {
            for (DataStore.Event.Node node : event.getNodes()) {
                renderNode(event, node);
            }
        }

        if (event.getConnections() != null) {
            for (DataStore.Event.Connection conn : event.getConnections()) {
                long lid  = linkId(conn);
                long from = pinId(conn.getFromNodeId(), conn.getFromPin());
                long to   = pinId(conn.getToNodeId(),   conn.getToPin());
                NodeEditor.link(lid, from, to);
            }
        }

        handleCreate(event, storage);
        handleDelete(event, storage);

        if (pendingNavigate) {
            NodeEditor.navigateToContent();
            pendingNavigate = false;
        }

        NodeEditor.end();

        // Sync canvas positions back into the DataStore working copy each frame.
        // Actual persistence to disk happens when the event editor saves the event.
        syncPositions(event);
    }

    /**
     * Spawns a new node of the given type at a staggered position near the canvas origin.
     *
     * @param event    the event to add the node to
     * @param storage  used to persist after adding
     * @param nodeType the {@link NodeType} name (e.g. {@code "SERIAL_TRIGGER"})
     */
    public void spawnNode(DataStore.Event event, StorageProvider storage, String nodeType) {
        DataStore.Event.Node node = new DataStore.Event.Node();
        node.setId(UUID.randomUUID().toString());
        node.setType(nodeType);
        node.setX(spawnX);
        node.setY(spawnY);
        node.setParams(new HashMap<>());
        if (event.getNodes() == null || !(event.getNodes() instanceof ArrayList)) {
            event.setNodes(event.getNodes() == null ? new ArrayList<>() : new ArrayList<>(event.getNodes()));
        }
        event.getNodes().add(node);
        storage.save();
    }

    /**
     * Clears the initialized-position set so that stored positions are re-applied to NodeEditor
     * on the next render, then requests a {@link NodeEditor#navigateToContent()} so the view
     * is centered on the graph.
     */
    public void resetView() {
        initializedPositions.clear();
        pendingNavigate = true;
    }

    /**
     * Destroys the underlying NodeEditor context. Should be called when the owning dialog is
     * permanently torn down (e.g. application shutdown).
     */
    public void dispose() {
        if (context != null) {
            NodeEditor.destroyEditor(context);
            context = null;
        }
    }

    // =========================================================================
    // Node rendering
    // =========================================================================

    private void renderNode(DataStore.Event event, DataStore.Event.Node node) {
        NodeType nt = NodeType.getByName(node.getType());
        if (nt == null) return;

        long nid = nodeId(node.getId());

        // Apply stored position once per session (NodeEditor tracks subsequent drags itself).
        if (!initializedPositions.contains(nid)) {
            NodeEditor.setNodePosition(nid, node.getX(), node.getY());
            initializedPositions.add(nid);
        }

        // Push per-category background and border colors.
        float[] bg     = categoryBg(nt.getCategory());
        float[] border = categoryBorder(nt.getCategory());
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg,     bg[0],     bg[1],     bg[2],     bg[3]);
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBorder, border[0], border[1], border[2], border[3]);

        NodeEditor.beginNode(nid);
        ImGui.pushID(node.getId());

        // Title row: colored per category
        float[] tc = categoryTitleColor(nt.getCategory());
        ImGui.pushStyleColor(ImGuiCol.Text, tc[0], tc[1], tc[2], tc[3]);
        ImGui.text(nt.getFriendlyName());
        ImGui.popStyleColor();

        // Guarantee a minimum node width so output pins have room to right-align.
        if (ImGui.getItemRectSize().x < NODE_MIN_W) {
            ImGui.sameLine();
            ImGui.dummy(NODE_MIN_W - ImGui.getItemRectSize().x, 1f);
        }
        ImGui.separator();

        // Input pins (left side: circle + label / text field)
        for (NodeType.PinDef pin : nt.inputPins()) {
            renderInputPin(event, node, pin);
        }

        // Output pins (right side: label + circle)
        for (NodeType.PinDef pin : nt.outputPins()) {
            renderOutputPin(node, pin);
        }

        ImGui.popID();
        NodeEditor.endNode();
        NodeEditor.popStyleColor(2);
    }

    private void renderInputPin(DataStore.Event event, DataStore.Event.Node node, NodeType.PinDef pin) {
        long    pid       = pinId(node.getId(), pin.id());
        boolean isExec    = pin.kind() == NodeType.PinKind.EXEC;
        boolean connected = !isExec && isConnectedInput(event, node.getId(), pin.id());

        NodeEditor.beginPin(pid, NodeEditorPinKind.Input);
        ImVec2 cp = ImGui.getCursorScreenPos();
        ImGui.dummy(PIN_SIZE, PIN_SIZE);
        ImGui.getWindowDrawList().addCircleFilled(
                cp.x + PIN_SIZE * 0.5f, cp.y + PIN_SIZE * 0.5f, PIN_RADIUS,
                isExec ? COL_PIN_EXEC : COL_PIN_STRING);
        NodeEditor.endPin();

        if (!isExec) {
            ImGui.sameLine();
            if (connected) {
                ImGui.text(friendlyPinName(pin.id()));
            } else {
                // Inline label + text field for unconnected data pins
                ImGui.text(friendlyPinName(pin.id()) + ":");
                ImGui.sameLine();
                String cur = node.getParams() != null
                        ? node.getParams().getOrDefault(pin.id(), "") : "";
                ImString val = new ImString(cur, 256);
                ImGui.setNextItemWidth(100f);
                if (ImGui.inputText("##" + pin.id(), val)) {
                    if (node.getParams() == null) node.setParams(new HashMap<>());
                    node.getParams().put(pin.id(), val.get());
                }
            }
        }
    }

    private void renderOutputPin(DataStore.Event.Node node, NodeType.PinDef pin) {
        long    pid    = pinId(node.getId(), pin.id());
        boolean isExec = pin.kind() == NodeType.PinKind.EXEC;
        int color = isExec ? COL_PIN_EXEC : COL_PIN_STRING;

        // Output pins: label left, circle right so wires exit from the right side
        if (!isExec) {
            ImGui.text(friendlyPinName(pin.id()));
            ImGui.sameLine();
        }

        NodeEditor.beginPin(pid, NodeEditorPinKind.Output);
        ImVec2 cp = ImGui.getCursorScreenPos();
        ImGui.dummy(PIN_SIZE, PIN_SIZE);
        ImGui.getWindowDrawList().addCircleFilled(
                cp.x + PIN_SIZE * 0.5f, cp.y + PIN_SIZE * 0.5f, PIN_RADIUS, color);
        NodeEditor.endPin();
    }

    // =========================================================================
    // Create / Delete handling
    // =========================================================================

    private void handleCreate(DataStore.Event event, StorageProvider storage) {
        if (!NodeEditor.beginCreate()) return;

        ImLong startPin = new ImLong();
        ImLong endPin   = new ImLong();
        if (NodeEditor.queryNewLink(startPin, endPin)) {
            String[][] fromTo = resolveFromTo(event, startPin.get(), endPin.get());
            if (fromTo != null && pinKindMatch(event, fromTo[0], fromTo[1])) {
                if (NodeEditor.acceptNewItem()) {
                    // Remove any existing wire into the target input pin first.
                    event.getConnections().removeIf(c ->
                            c.getToNodeId().equals(fromTo[1][0]) && c.getToPin().equals(fromTo[1][1]));
                    DataStore.Event.Connection conn = new DataStore.Event.Connection(
                            fromTo[0][0], fromTo[0][1], fromTo[1][0], fromTo[1][1]);
                    event.getConnections().add(conn);
                    linkId(conn); // register the new link ID in our map
                    storage.save();
                }
            } else {
                NodeEditor.rejectNewItem();
            }
        }

        NodeEditor.endCreate();
    }

    private void handleDelete(DataStore.Event event, StorageProvider storage) {
        if (!NodeEditor.beginDelete()) return;
        boolean changed = false;

        ImLong deletedNode = new ImLong();
        while (NodeEditor.queryDeletedNode(deletedNode)) {
            if (NodeEditor.acceptDeletedItem()) {
                long   nid  = deletedNode.get();
                String uuid = nodeLookup.get(nid);
                if (uuid != null) {
                    event.getNodes().removeIf(n -> n.getId().equals(uuid));
                    event.getConnections().removeIf(c ->
                            c.getFromNodeId().equals(uuid) || c.getToNodeId().equals(uuid));
                    initializedPositions.remove(nid);
                    changed = true;
                }
            }
        }

        ImLong deletedLink = new ImLong();
        while (NodeEditor.queryDeletedLink(deletedLink)) {
            if (NodeEditor.acceptDeletedItem()) {
                String key = linkLookup.get(deletedLink.get());
                if (key != null) {
                    String[] p = key.split(":", 4);
                    if (p.length == 4) {
                        event.getConnections().removeIf(c ->
                                c.getFromNodeId().equals(p[0]) && c.getFromPin().equals(p[1]) &&
                                c.getToNodeId().equals(p[2])   && c.getToPin().equals(p[3]));
                    }
                    changed = true;
                }
            }
        }

        NodeEditor.endDelete();
        if (changed) storage.save();
    }

    // =========================================================================
    // Position sync
    // =========================================================================

    /**
     * Copies the current NodeEditor canvas positions back into the DataStore working copy.
     * Called every frame so that positions are captured when the event is explicitly saved.
     */
    private void syncPositions(DataStore.Event event) {
        if (event.getNodes() == null) return;
        for (DataStore.Event.Node node : event.getNodes()) {
            long nid = idMap.getOrDefault("n:" + node.getId(), 0L);
            if (nid == 0L || !initializedPositions.contains(nid)) continue;
            node.setX(NodeEditor.getNodePositionX(nid));
            node.setY(NodeEditor.getNodePositionY(nid));
        }
    }

    // =========================================================================
    // ID registry
    // =========================================================================

    private long nodeId(String uuid) {
        String key = "n:" + uuid;
        Long   id  = idMap.get(key);
        if (id == null) {
            id = nextId++;
            idMap.put(key, id);
            nodeLookup.put(id, uuid);
        }
        return id;
    }

    private long pinId(String nodeUuid, String pinName) {
        String key = "p:" + nodeUuid + ":" + pinName;
        Long   id  = idMap.get(key);
        if (id == null) {
            id = nextId++;
            idMap.put(key, id);
            pinLookup.put(id, new String[]{nodeUuid, pinName});
        }
        return id;
    }

    private long linkId(DataStore.Event.Connection conn) {
        String key = "l:" + conn.getFromNodeId() + ":" + conn.getFromPin()
                       + ":" + conn.getToNodeId() + ":" + conn.getToPin();
        Long id = idMap.get(key);
        if (id == null) {
            id = nextId++;
            idMap.put(key, id);
            linkLookup.put(id, conn.getFromNodeId() + ":" + conn.getFromPin()
                              + ":" + conn.getToNodeId() + ":" + conn.getToPin());
        }
        return id;
    }

    // =========================================================================
    // Graph helpers
    // =========================================================================

    /**
     * Given two pin IDs returned by {@link NodeEditor#queryNewLink}, identifies which is
     * the output (from) pin and which is the input (to) pin.
     *
     * @return {@code String[][]{from, to}} where each element is {@code [nodeUuid, pinId]},
     *         or {@code null} if both pins are the same kind or belong to the same node.
     */
    private String[][] resolveFromTo(DataStore.Event event, long pinA, long pinB) {
        String[] a = pinLookup.get(pinA);
        String[] b = pinLookup.get(pinB);
        if (a == null || b == null || a[0].equals(b[0])) return null;
        boolean aIsOut = isOutputPin(event, a[0], a[1]);
        boolean bIsOut = isOutputPin(event, b[0], b[1]);
        if (aIsOut && !bIsOut) return new String[][]{a, b};
        if (!aIsOut && bIsOut) return new String[][]{b, a};
        return null; // both inputs or both outputs — reject
    }

    private boolean isOutputPin(DataStore.Event event, String nodeUuid, String pinId) {
        DataStore.Event.Node node = findNode(event, nodeUuid);
        if (node == null) return false;
        NodeType nt = NodeType.getByName(node.getType());
        if (nt == null) return false;
        return nt.outputPins().stream().anyMatch(p -> p.id().equals(pinId));
    }

    /**
     * Returns {@code true} if the output pin and input pin carry the same data kind
     * (EXEC↔EXEC or STRING↔STRING).
     *
     * @param from {@code [nodeUuid, pinId]} for the output side
     * @param to   {@code [nodeUuid, pinId]} for the input side
     */
    private boolean pinKindMatch(DataStore.Event event, String[] from, String[] to) {
        DataStore.Event.Node fn = findNode(event, from[0]);
        DataStore.Event.Node tn = findNode(event, to[0]);
        if (fn == null || tn == null) return false;
        NodeType fnt = NodeType.getByName(fn.getType());
        NodeType tnt = NodeType.getByName(tn.getType());
        if (fnt == null || tnt == null) return false;
        NodeType.PinKind fromKind = fnt.outputPins().stream()
                .filter(p -> p.id().equals(from[1])).findFirst().map(NodeType.PinDef::kind).orElse(null);
        NodeType.PinKind toKind = tnt.inputPins().stream()
                .filter(p -> p.id().equals(to[1])).findFirst().map(NodeType.PinDef::kind).orElse(null);
        return fromKind != null && toKind != null && fromKind == toKind;
    }

    private boolean isConnectedInput(DataStore.Event event, String nodeId, String pinId) {
        if (event.getConnections() == null) return false;
        return event.getConnections().stream()
                .anyMatch(c -> c.getToNodeId().equals(nodeId) && c.getToPin().equals(pinId));
    }

    private DataStore.Event.Node findNode(DataStore.Event event, String nodeId) {
        if (event.getNodes() == null) return null;
        return event.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst().orElse(null);
    }

    // =========================================================================
    // Style helpers
    // =========================================================================

    /** Converts a raw pin ID to a user-friendly label. Strips {@code data_} prefix and capitalizes. */
    private static String friendlyPinName(String pinId) {
        String s = pinId.startsWith("data_") ? pinId.substring(5) : pinId;
        s = s.replace('_', ' ');
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Node background color (RGBA, 0–1) for each category — a dark tint. */
    private static float[] categoryBg(NodeType.Category cat) {
        return switch (cat) {
            case TRIGGER   -> new float[]{0.12f, 0.09f, 0.22f, 1.0f};
            case CONDITION -> new float[]{0.09f, 0.18f, 0.09f, 1.0f};
            case ACTION    -> new float[]{0.22f, 0.13f, 0.06f, 1.0f};
            case VALUE     -> new float[]{0.06f, 0.18f, 0.22f, 1.0f};
        };
    }

    /** Node border color (RGBA, 0–1) for each category — a lighter accent. */
    private static float[] categoryBorder(NodeType.Category cat) {
        return switch (cat) {
            case TRIGGER   -> new float[]{0.38f, 0.27f, 0.91f, 0.9f};
            case CONDITION -> new float[]{0.29f, 0.87f, 0.29f, 0.9f};
            case ACTION    -> new float[]{0.94f, 0.65f, 0.38f, 0.9f};
            case VALUE     -> new float[]{0.08f, 0.80f, 0.98f, 0.9f};
        };
    }

    /** Title text color (RGBA, 0–1) for each category. */
    private static float[] categoryTitleColor(NodeType.Category cat) {
        return switch (cat) {
            case TRIGGER   -> new float[]{0.65f, 0.53f, 1.00f, 1.0f};
            case CONDITION -> new float[]{0.50f, 1.00f, 0.50f, 1.0f};
            case ACTION    -> new float[]{1.00f, 0.75f, 0.40f, 1.0f};
            case VALUE     -> new float[]{0.30f, 0.90f, 1.00f, 1.0f};
        };
    }

    private void ensureContext() {
        if (context == null) {
            context = NodeEditor.createEditor();
        }
    }
}
