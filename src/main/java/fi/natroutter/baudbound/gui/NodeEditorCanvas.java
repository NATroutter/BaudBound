package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.enums.NodeType;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.nodeditor.NodeEditor;
import imgui.extension.nodeditor.NodeEditorContext;
import imgui.extension.nodeditor.flag.NodeEditorPinKind;
import imgui.extension.nodeditor.flag.NodeEditorStyleColor;
import imgui.type.ImLong;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Node graph editor canvas backed by {@code imgui.extension.nodeditor.NodeEditor}.
 *
 * <p>Must be called from the GLFW main thread each frame via {@link #render}.
 * Call {@link #dispose()} when the owning dialog is permanently torn down.
 */
public class NodeEditorCanvas {

    private NodeEditorContext context;

    private final Map<String, Long>    idMap      = new HashMap<>();
    private final Map<Long, String[]>  pinLookup  = new HashMap<>();
    private final Map<Long, String>    linkLookup = new HashMap<>();
    private final Map<Long, String>    nodeLookup = new HashMap<>();

    private long nextId = 1L;

    private final Set<Long> initializedPositions = new HashSet<>();
    private boolean pendingNavigate = false;
    private float spawnX = 0f, spawnY = 0f;

    // ---- Layout constants ----
    private static final float ICON_SIZE     = 24f;
    private static final float ICON_RADIUS   = 9f;
    private static final float HEADER_HEIGHT = 30f;
    private static final float NODE_MIN_W    = 200f;

    // ---- Pin colors (ABGR for ImDrawList) ----
    private static final int COL_PIN_EXEC   = 0xFFFFFFFF;
    private static final int COL_PIN_STRING = 0xFF6633CC;
    private static final int COL_ICON_INNER = 0xFF1A1A1A;

    // ---- Per-frame state ----
    private final Set<Long> connectedPins = new HashSet<>();

    private record NodeHeader(long nodeId, int color, String title,
                              float minX, float minY, float maxX) {}
    private final List<NodeHeader> pendingHeaders = new ArrayList<>();

    private NodeType.PinKind activeDragKind = null;

    // =========================================================================
    // Public API
    // =========================================================================

    public void render(DataStore.Event event, StorageProvider storage) {
        ensureContext();
        NodeEditor.setCurrentEditor(context);

        ImVec2 editorTopLeft = ImGui.getCursorScreenPos();
        NodeEditor.begin("##ne");

        ImVec2 editorSize = NodeEditor.getScreenSize();
        ImVec2 center = NodeEditor.screenToCanvas(
                editorTopLeft.x + editorSize.x * 0.5f,
                editorTopLeft.y + editorSize.y * 0.5f);
        spawnX = center.x;
        spawnY = center.y;

        // Pre-compute which pins are connected so icons render filled vs outline.
        connectedPins.clear();
        if (event.getConnections() != null) {
            for (DataStore.Event.Connection c : event.getConnections()) {
                connectedPins.add(pinId(c.getFromNodeId(), c.getFromPin()));
                connectedPins.add(pinId(c.getToNodeId(),   c.getToPin()));
            }
        }

        if (event.getNodes() != null) {
            for (DataStore.Event.Node node : event.getNodes()) {
                renderNode(event, node);
            }
        }

        // Draw headers after all endNode() calls — node bounds are finalised then.
        for (NodeHeader h : pendingHeaders) drawHeader(h);
        pendingHeaders.clear();

        if (event.getConnections() != null) {
            for (DataStore.Event.Connection conn : event.getConnections()) {
                long lid  = linkId(conn);
                long from = pinId(conn.getFromNodeId(), conn.getFromPin());
                long to   = pinId(conn.getToNodeId(),   conn.getToPin());
                NodeType.PinKind kind = resolvePinKind(event, conn.getFromNodeId(), conn.getFromPin());
                int col = (kind == NodeType.PinKind.EXEC) ? COL_PIN_EXEC : COL_PIN_STRING;
                NodeEditor.link(lid, from, to,
                        ((col)       & 0xFF) / 255f,
                        ((col >>  8) & 0xFF) / 255f,
                        ((col >> 16) & 0xFF) / 255f,
                        ((col >> 24) & 0xFF) / 255f,
                        2.0f);
            }
        }

        handleCreate(event, storage);
        handleDelete(event, storage);

        if (pendingNavigate) {
            NodeEditor.navigateToContent();
            pendingNavigate = false;
        }

        NodeEditor.end();
        syncPositions(event);
    }

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

    public void resetView() {
        initializedPositions.clear();
        pendingNavigate = true;
    }

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

        if (!initializedPositions.contains(nid)) {
            NodeEditor.setNodePosition(nid, node.getX(), node.getY());
            initializedPositions.add(nid);
        }

        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg,     0.12f, 0.12f, 0.12f, 0.97f);
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBorder, 0.28f, 0.28f, 0.28f, 1.00f);

        NodeEditor.beginNode(nid);
        ImGui.pushID(node.getId());

        // Reserve space for the colored header bar; capture its screen rect for deferred drawing.
        ImGui.dummy(NODE_MIN_W, HEADER_HEIGHT);
        float headerMinX = ImGui.getItemRectMin().x;
        float headerMinY = ImGui.getItemRectMin().y;
        float headerMaxX = ImGui.getItemRectMax().x;

        ImGui.dummy(0f, 4f); // gap between header and first pin row

        // Input pins — stacked vertically on the left side.
        for (NodeType.PinDef pin : nt.inputPins()) {
            renderInputPin(event, node, pin);
        }

        // Output pins — stacked vertically on the right side.
        for (NodeType.PinDef pin : nt.outputPins()) {
            renderOutputPin(node, pin);
        }

        ImGui.dummy(0f, 4f); // bottom padding

        ImGui.popID();
        NodeEditor.endNode();

        pendingHeaders.add(new NodeHeader(nid, categoryHeaderColor(nt.getCategory()),
                nt.getFriendlyName(), headerMinX, headerMinY, headerMaxX));

        NodeEditor.popStyleColor(2);
    }

    /**
     * Draws the colored header bar using the node's background draw list.
     * Screen coordinates were captured during {@link #renderNode} before {@code endNode()}.
     */
    private void drawHeader(NodeHeader h) {
        ImDrawList dl = NodeEditor.getNodeBackgroundDrawList(h.nodeId());
        float x1 = h.minX(), y1 = h.minY(), x2 = h.maxX(), y2 = y1 + HEADER_HEIGHT;

        // Rounded on top two corners only (ImDrawFlags_RoundCornersTop = 0x30).
        dl.addRectFilled(x1, y1, x2, y2, h.color(), 5f, 0x30);
        // Subtle separator line at header bottom.
        dl.addLine(x1, y2, x2, y2, rgba(255, 255, 255, 35), 1f);
        // Title text centred vertically in the header band.
        float textH = ImGui.getTextLineHeight();
        dl.addText(x1 + 8f, y1 + (HEADER_HEIGHT - textH) * 0.5f, 0xFFFFFFFF, h.title());
    }

    // =========================================================================
    // Pin rendering — stacked layout (inputs left, outputs right)
    // =========================================================================

    private void renderInputPin(DataStore.Event event, DataStore.Event.Node node, NodeType.PinDef pin) {
        long    pid       = pinId(node.getId(), pin.id());
        boolean isExec    = pin.kind() == NodeType.PinKind.EXEC;
        boolean connected = connectedPins.contains(pid);
        float   alpha     = (activeDragKind != null && activeDragKind != pin.kind()) ? 48f / 255f : 1f;
        int     color     = withAlpha(isExec ? COL_PIN_EXEC : COL_PIN_STRING, alpha);

        // NodeEditorPinKind.Output = 1 = C++ Input (LEFT side).
        NodeEditor.beginPin(pid, NodeEditorPinKind.Output);
        ImVec2 cp = ImGui.getCursorScreenPos();
        NodeEditor.pinPivotRect(cp.x, cp.y, cp.x + ICON_SIZE, cp.y + ICON_SIZE);
        ImGui.dummy(ICON_SIZE, ICON_SIZE);
        drawPinIcon(ImGui.getWindowDrawList(), cp.x + ICON_SIZE * 0.5f, cp.y + ICON_SIZE * 0.5f,
                isExec, connected, color);
        NodeEditor.endPin();

        if (!isExec) {
            ImGui.sameLine(0, 6f);
            if (connected) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + (ICON_SIZE - ImGui.getTextLineHeight()) * 0.5f);
                ImGui.text(friendlyPinName(pin.id()));
            } else {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + (ICON_SIZE - ImGui.getFrameHeight()) * 0.5f);
                ImGui.text(friendlyPinName(pin.id()) + ":");
                ImGui.sameLine(0, 4f);
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
        long    pid       = pinId(node.getId(), pin.id());
        boolean isExec    = pin.kind() == NodeType.PinKind.EXEC;
        boolean connected = connectedPins.contains(pid);
        float   alpha     = (activeDragKind != null && activeDragKind != pin.kind()) ? 48f / 255f : 1f;
        int     color     = withAlpha(isExec ? COL_PIN_EXEC : COL_PIN_STRING, alpha);

        // Push the icon to the right edge using a leading spacer dummy.
        if (!isExec) {
            String label  = friendlyPinName(pin.id());
            float  labelW = ImGui.calcTextSize(label).x;
            float  spacer = Math.max(1f, NODE_MIN_W - labelW - ICON_SIZE - 6f);
            ImGui.dummy(spacer, ICON_SIZE);
            ImGui.sameLine(0, 0);
            ImGui.setCursorPosY(ImGui.getCursorPosY() + (ICON_SIZE - ImGui.getTextLineHeight()) * 0.5f);
            ImGui.text(label);
            ImGui.sameLine(0, 6f);
            ImGui.setCursorPosY(ImGui.getCursorPosY() - (ICON_SIZE - ImGui.getTextLineHeight()) * 0.5f);
        } else {
            ImGui.dummy(Math.max(1f, NODE_MIN_W - ICON_SIZE), ICON_SIZE);
            ImGui.sameLine(0, 0);
        }

        // NodeEditorPinKind.Input = 0 = C++ Output (RIGHT side).
        NodeEditor.beginPin(pid, NodeEditorPinKind.Input);
        ImVec2 cp = ImGui.getCursorScreenPos();
        NodeEditor.pinPivotRect(cp.x, cp.y, cp.x + ICON_SIZE, cp.y + ICON_SIZE);
        ImGui.dummy(ICON_SIZE, ICON_SIZE);
        drawPinIcon(ImGui.getWindowDrawList(), cp.x + ICON_SIZE * 0.5f, cp.y + ICON_SIZE * 0.5f,
                isExec, connected, color);
        NodeEditor.endPin();
    }

    // =========================================================================
    // Pin icon drawing
    // =========================================================================

    private void drawPinIcon(ImDrawList dl, float cx, float cy,
                              boolean isExec, boolean filled, int color) {
        if (isExec) drawFlowIcon(dl, cx, cy, filled, color);
        else        drawCircleIcon(dl, cx, cy, filled, color);
    }

    private void drawCircleIcon(ImDrawList dl, float cx, float cy, boolean filled, int color) {
        if (filled) {
            dl.addCircleFilled(cx, cy, ICON_RADIUS, color, 12);
        } else {
            dl.addCircleFilled(cx, cy, ICON_RADIUS, COL_ICON_INNER, 12);
            dl.addCircle(cx, cy, ICON_RADIUS, color, 12, 1.5f);
        }
    }

    private void drawFlowIcon(ImDrawList dl, float cx, float cy, boolean filled, int color) {
        float s = ICON_RADIUS;
        float[][] pts = {
            {cx - s * 0.5f, cy - s},
            {cx + s * 0.5f, cy - s},
            {cx + s,        cy    },
            {cx + s * 0.5f, cy + s},
            {cx - s * 0.5f, cy + s},
            {cx,            cy    },
        };
        dl.pathClear();
        for (float[] p : pts) dl.pathLineTo(p[0], p[1]);
        if (filled) {
            dl.pathFillConvex(color);
        } else {
            dl.pathFillConvex(COL_ICON_INNER);
            dl.pathClear();
            for (float[] p : pts) dl.pathLineTo(p[0], p[1]);
            dl.pathStroke(color, 1 /* ImDrawFlags_Closed */, 1.5f);
        }
    }

    // =========================================================================
    // Create / Delete handling
    // =========================================================================

    private void handleCreate(DataStore.Event event, StorageProvider storage) {
        activeDragKind = null;

        if (!NodeEditor.beginCreate()) return;

        ImLong startPin = new ImLong();
        ImLong endPin   = new ImLong();
        if (NodeEditor.queryNewLink(startPin, endPin)) {
            String[] dragInfo = pinLookup.get(startPin.get());
            if (dragInfo == null) dragInfo = pinLookup.get(endPin.get());
            if (dragInfo != null) {
                activeDragKind = resolvePinKind(event, dragInfo[0], dragInfo[1]);
            }

            String[][] fromTo = resolveFromTo(event, startPin.get(), endPin.get());
            if (fromTo != null && pinKindMatch(event, fromTo[0], fromTo[1])) {
                int col = (activeDragKind == NodeType.PinKind.EXEC) ? COL_PIN_EXEC : COL_PIN_STRING;
                if (NodeEditor.acceptNewItem(
                        ((col) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f,
                        ((col >> 16) & 0xFF) / 255f, 1f, 2f)) {
                    event.getConnections().removeIf(c ->
                            c.getToNodeId().equals(fromTo[1][0]) && c.getToPin().equals(fromTo[1][1]));
                    DataStore.Event.Connection conn = new DataStore.Event.Connection(
                            fromTo[0][0], fromTo[0][1], fromTo[1][0], fromTo[1][1]);
                    event.getConnections().add(conn);
                    linkId(conn);
                    storage.save();
                }
            } else {
                NodeEditor.rejectNewItem(1f, 0.2f, 0.2f, 1f);
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
        if (id == null) { id = nextId++; idMap.put(key, id); nodeLookup.put(id, uuid); }
        return id;
    }

    private long pinId(String nodeUuid, String pinName) {
        String key = "p:" + nodeUuid + ":" + pinName;
        Long   id  = idMap.get(key);
        if (id == null) { id = nextId++; idMap.put(key, id); pinLookup.put(id, new String[]{nodeUuid, pinName}); }
        return id;
    }

    private long linkId(DataStore.Event.Connection conn) {
        String key = "l:" + conn.getFromNodeId() + ":" + conn.getFromPin()
                       + ":" + conn.getToNodeId() + ":" + conn.getToPin();
        Long id = idMap.get(key);
        if (id == null) {
            id = nextId++; idMap.put(key, id);
            linkLookup.put(id, conn.getFromNodeId() + ":" + conn.getFromPin()
                              + ":" + conn.getToNodeId() + ":" + conn.getToPin());
        }
        return id;
    }

    // =========================================================================
    // Graph helpers
    // =========================================================================

    private String[][] resolveFromTo(DataStore.Event event, long pinA, long pinB) {
        String[] a = pinLookup.get(pinA);
        String[] b = pinLookup.get(pinB);
        if (a == null || b == null || a[0].equals(b[0])) return null;
        boolean aIsOut = isOutputPin(event, a[0], a[1]);
        boolean bIsOut = isOutputPin(event, b[0], b[1]);
        if (aIsOut && !bIsOut) return new String[][]{a, b};
        if (!aIsOut && bIsOut) return new String[][]{b, a};
        return null;
    }

    private boolean isOutputPin(DataStore.Event event, String nodeUuid, String pinId) {
        DataStore.Event.Node node = findNode(event, nodeUuid);
        if (node == null) return false;
        NodeType nt = NodeType.getByName(node.getType());
        if (nt == null) return false;
        return nt.outputPins().stream().anyMatch(p -> p.id().equals(pinId));
    }

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

    private NodeType.PinKind resolvePinKind(DataStore.Event event, String nodeUuid, String pinId) {
        DataStore.Event.Node node = findNode(event, nodeUuid);
        if (node == null) return null;
        NodeType nt = NodeType.getByName(node.getType());
        if (nt == null) return null;
        return nt.outputPins().stream()
                .filter(p -> p.id().equals(pinId)).map(NodeType.PinDef::kind).findFirst()
                .orElseGet(() -> nt.inputPins().stream()
                        .filter(p -> p.id().equals(pinId)).map(NodeType.PinDef::kind)
                        .findFirst().orElse(null));
    }

    private DataStore.Event.Node findNode(DataStore.Event event, String nodeId) {
        if (event.getNodes() == null) return null;
        return event.getNodes().stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null);
    }

    // =========================================================================
    // Style helpers
    // =========================================================================

    private static String friendlyPinName(String pinId) {
        String s = pinId.startsWith("data_") ? pinId.substring(5) : pinId;
        s = s.replace('_', ' ');
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int categoryHeaderColor(NodeType.Category cat) {
        return switch (cat) {
            case TRIGGER   -> rgba(55,  35, 130, 255);
            case CONDITION -> rgba(25,  95,  25, 255);
            case ACTION    -> rgba(130, 55,  10, 255);
            case VALUE     -> rgba(10,  85, 120, 255);
        };
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int withAlpha(int abgr, float fraction) {
        int a = Math.round(((abgr >>> 24) & 0xFF) * fraction);
        return (abgr & 0x00FFFFFF) | (a << 24);
    }

    private void ensureContext() {
        if (context == null) context = NodeEditor.createEditor();
    }
}
