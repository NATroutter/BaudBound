package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.enums.NodeType;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An ImGui draw-list canvas that renders the node graph for a {@link DataStore.Event}.
 *
 * <p>Handles all visual rendering (nodes, wires, grid) and interactions (pan, zoom,
 * drag, connect, delete). Modifies the event's {@code nodes} and {@code connections}
 * lists in-place and calls {@link StorageProvider#save()} after structural changes.
 *
 * <p>Must be called from the GLFW main thread each frame via {@link #render}.
 */
public class NodeEditorCanvas {

    // Pan/zoom
    private float panX = 0f, panY = 0f;
    private float zoom = 1.0f;

    // Interaction
    private String dragNodeId = null;
    private float  dragStartMouseX, dragStartMouseY;
    private float  dragStartNodeX,  dragStartNodeY;

    private String selectedNodeId = null;

    // Pending wire: started from an output pin
    private String pendingFromNodeId = null;
    private String pendingFromPin    = null;
    private float  pendingFromScreenX, pendingFromScreenY;

    // Per-frame maps rebuilt each render()
    // key: "nodeId:pinId"  value: [screenX, screenY]
    private final Map<String, float[]> pinScreenPos = new HashMap<>();
    // key: nodeId  value: [screenX, screenY, w, h]
    private final Map<String, float[]> nodeScreenBounds = new HashMap<>();
    // key: nodeId  value: [headerScreenX, headerScreenY, w, headerH]
    private final Map<String, float[]> nodeHeaderBounds = new HashMap<>();

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final float NODE_WIDTH   = 180f;
    private static final float HEADER_H     = 24f;
    private static final float PIN_ROW_H    = 20f;
    private static final float PIN_RADIUS   = 5f;
    private static final float PIN_MARGIN_X = 10f;   // distance from node edge to pin centre

    // Colors (ABGR packed ints)
    private static final int COL_NODE_BG      = 0xFF1E2A30;
    private static final int COL_NODE_BORDER  = 0xFF3A4A52;
    private static final int COL_NODE_SEL     = 0xFF60A0F0;
    private static final int COL_HDR_TRIGGER  = 0xFF6045E9;
    private static final int COL_HDR_COND     = 0xFF80DE4A;
    private static final int COL_HDR_ACTION   = 0xFFF0A560;
    private static final int COL_HDR_VALUE    = 0xFF15CCFA;
    private static final int COL_PIN_EXEC     = 0xFFFFFFFF;
    private static final int COL_PIN_STRING   = 0xFFB060FF;
    private static final int COL_WIRE_EXEC    = 0xFFFFFFFF;
    private static final int COL_WIRE_STRING  = 0xFFB060FF;
    private static final int COL_TEXT         = 0xFFDDDDDD;
    private static final int COL_GRID         = 0x22FFFFFF;
    private static final int COL_GRID_MAJOR   = 0x33FFFFFF;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Renders the node canvas for the given event. Must be called every frame from the GLFW thread.
     * Modifies {@code event.nodes} and {@code event.connections} in-place.
     *
     * @param event   the event being edited
     * @param storage used to call save() after structural changes
     */
    public void render(DataStore.Event event, StorageProvider storage) {
        pinScreenPos.clear();
        nodeScreenBounds.clear();
        nodeHeaderBounds.clear();

        ImVec2 canvasPos  = ImGui.getCursorScreenPos();
        ImVec2 canvasSize = ImGui.getContentRegionAvail();
        float  cx = canvasPos.x, cy = canvasPos.y;
        float  cw = canvasSize.x, ch = canvasSize.y;

        // Check canvas hover manually so text field widgets rendered inside are not blocked.
        ImVec2 mouse = ImGui.getMousePos();
        boolean canvasHovered = mouse.x >= cx && mouse.x <= cx + cw
                             && mouse.y >= cy && mouse.y <= cy + ch;

        ImDrawList dl = ImGui.getWindowDrawList();

        // Clip to canvas region
        dl.pushClipRect(cx, cy, cx + cw, cy + ch, true);

        // Background
        dl.addRectFilled(cx, cy, cx + cw, cy + ch, 0xFF111820);

        // Grid
        drawGrid(dl, cx, cy, cw, ch);

        // Nodes
        if (event.getNodes() != null) {
            for (DataStore.Event.Node node : event.getNodes()) {
                renderNode(dl, event, node, cx, cy);
            }
        }

        // Wires
        if (event.getConnections() != null) {
            renderWires(dl, event);
        }

        // Pending wire being dragged
        if (pendingFromNodeId != null) {
            ImVec2 mouse = ImGui.getMousePos();
            drawBezier(dl, pendingFromScreenX, pendingFromScreenY,
                       mouse.x, mouse.y, COL_WIRE_EXEC, 2f);
        }

        dl.popClipRect();

        // Handle interactions
        handleInteractions(event, storage, cx, cy, cw, ch, canvasHovered);
    }

    /**
     * Spawns a new node of the given type at the current canvas center.
     *
     * @param event    the event to add the node to
     * @param storage  used to persist after adding
     * @param nodeType the {@link NodeType} name (e.g. {@code "SERIAL_TRIGGER"})
     */
    public void spawnNode(DataStore.Event event, StorageProvider storage, String nodeType) {
        DataStore.Event.Node node = new DataStore.Event.Node();
        node.setId(UUID.randomUUID().toString());
        node.setType(nodeType);
        // Place near canvas center (rough estimate using current pan/zoom)
        node.setX((-panX + 300f) / zoom);
        node.setY((-panY + 200f) / zoom);
        node.setParams(new HashMap<>());
        if (event.getNodes() == null || !(event.getNodes() instanceof ArrayList)) {
            event.setNodes(event.getNodes() == null ? new ArrayList<>() : new ArrayList<>(event.getNodes()));
        }
        event.getNodes().add(node);
        storage.save();
    }

    /**
     * Resets pan and zoom to their default values (pan = 0,0 ; zoom = 1.0).
     */
    public void resetView() {
        panX = 0f; panY = 0f; zoom = 1.0f;
    }

    // -------------------------------------------------------------------------
    // Rendering helpers
    // -------------------------------------------------------------------------

    private void drawGrid(ImDrawList dl, float cx, float cy, float cw, float ch) {
        float spacing = 20f * zoom;
        float offsetX = (panX % spacing + spacing) % spacing;
        float offsetY = (panY % spacing + spacing) % spacing;

        float majorSpacing = spacing * 5f;
        float majorOffsetX = (panX % majorSpacing + majorSpacing) % majorSpacing;
        float majorOffsetY = (panY % majorSpacing + majorSpacing) % majorSpacing;

        for (float x = cx + offsetX; x < cx + cw; x += spacing) {
            int col = (Math.abs(x - cx - majorOffsetX) < 1f) ? COL_GRID_MAJOR : COL_GRID;
            dl.addLine(x, cy, x, cy + ch, col, 1f);
        }
        for (float y = cy + offsetY; y < cy + ch; y += spacing) {
            int col = (Math.abs(y - cy - majorOffsetY) < 1f) ? COL_GRID_MAJOR : COL_GRID;
            dl.addLine(cx, y, cx + cw, y, col, 1f);
        }
    }

    private void renderNode(ImDrawList dl, DataStore.Event event,
                             DataStore.Event.Node node, float cx, float cy) {
        NodeType nt = NodeType.getByName(node.getType());
        if (nt == null) return;

        List<NodeType.PinDef> inputs  = nt.inputPins();
        List<NodeType.PinDef> outputs = nt.outputPins();
        int pinRows = Math.max(inputs.size(), outputs.size());
        float nodeH = HEADER_H + pinRows * PIN_ROW_H + 6f;

        float sx = cx + node.getX() * zoom + panX;
        float sy = cy + node.getY() * zoom + panY;
        float sw = NODE_WIDTH * zoom;
        float sh = nodeH * zoom;

        nodeScreenBounds.put(node.getId(), new float[]{sx, sy, sw, sh});
        nodeHeaderBounds.put(node.getId(), new float[]{sx, sy, sw, HEADER_H * zoom});

        boolean selected = node.getId().equals(selectedNodeId);

        // Body
        dl.addRectFilled(sx, sy, sx + sw, sy + sh, COL_NODE_BG, 4f);

        // Header
        int hdrColor = switch (nt.getCategory()) {
            case TRIGGER   -> COL_HDR_TRIGGER;
            case CONDITION -> COL_HDR_COND;
            case ACTION    -> COL_HDR_ACTION;
            case VALUE     -> COL_HDR_VALUE;
        };
        dl.addRectFilled(sx, sy, sx + sw, sy + HEADER_H * zoom, hdrColor, 4f);
        dl.addText(sx + 6f * zoom, sy + 5f * zoom, COL_TEXT, nt.getFriendlyName());

        // Border — (x1,y1,x2,y2,color,rounding,thickness)
        int borderCol = selected ? COL_NODE_SEL : COL_NODE_BORDER;
        dl.addRect(sx, sy, sx + sw, sy + sh, borderCol, 4f, 1.5f);

        float pinAreaY = sy + HEADER_H * zoom + 3f * zoom;

        // Input pins (left side)
        for (int i = 0; i < inputs.size(); i++) {
            NodeType.PinDef pin = inputs.get(i);
            float pinY = pinAreaY + i * PIN_ROW_H * zoom + PIN_ROW_H * zoom * 0.5f;
            float pinX = sx + PIN_MARGIN_X * zoom;
            int pinCol = pin.kind() == NodeType.PinKind.EXEC ? COL_PIN_EXEC : COL_PIN_STRING;
            dl.addCircleFilled(pinX, pinY, PIN_RADIUS * zoom, pinCol);
            dl.addText(pinX + (PIN_RADIUS + 3f) * zoom, pinY - 6f * zoom, COL_TEXT, pin.id());
            pinScreenPos.put(node.getId() + ":" + pin.id(), new float[]{pinX, pinY});

            // Inline text field if no wire connected to this input pin
            if (pin.kind() == NodeType.PinKind.STRING && !isConnectedInput(event, node.getId(), pin.id())) {
                float fieldX = pinX + (PIN_RADIUS + 3f) * zoom;
                float fieldW = (sw - (PIN_MARGIN_X + PIN_RADIUS + 6f) * zoom);
                if (fieldW > 40f) {
                    String current = node.getParams() != null ? node.getParams().getOrDefault(pin.id(), "") : "";
                    ImString imStr = new ImString(current, 256);
                    ImGui.setCursorScreenPos(fieldX, pinY - 9f * zoom);
                    ImGui.setNextItemWidth(fieldW);
                    if (ImGui.inputText("##pin_" + node.getId() + "_" + pin.id(), imStr)) {
                        if (node.getParams() == null) node.setParams(new HashMap<>());
                        node.getParams().put(pin.id(), imStr.get());
                    }
                }
            }
        }

        // Output pins (right side)
        for (int i = 0; i < outputs.size(); i++) {
            NodeType.PinDef pin = outputs.get(i);
            float pinY = pinAreaY + i * PIN_ROW_H * zoom + PIN_ROW_H * zoom * 0.5f;
            float pinX = sx + sw - PIN_MARGIN_X * zoom;
            int pinCol = pin.kind() == NodeType.PinKind.EXEC ? COL_PIN_EXEC : COL_PIN_STRING;
            dl.addCircleFilled(pinX, pinY, PIN_RADIUS * zoom, pinCol);
            // label left of pin circle
            float labelW = pin.id().length() * 6f * zoom;
            dl.addText(pinX - labelW - (PIN_RADIUS + 2f) * zoom, pinY - 6f * zoom, COL_TEXT, pin.id());
            pinScreenPos.put(node.getId() + ":" + pin.id(), new float[]{pinX, pinY});
        }
    }

    private boolean isConnectedInput(DataStore.Event event, String nodeId, String pinId) {
        if (event.getConnections() == null) return false;
        return event.getConnections().stream()
                .anyMatch(c -> c.getToNodeId().equals(nodeId) && c.getToPin().equals(pinId));
    }

    private void renderWires(ImDrawList dl, DataStore.Event event) {
        for (DataStore.Event.Connection conn : event.getConnections()) {
            float[] from = pinScreenPos.get(conn.getFromNodeId() + ":" + conn.getFromPin());
            float[] to   = pinScreenPos.get(conn.getToNodeId()   + ":" + conn.getToPin());
            if (from == null || to == null) continue;
            boolean isExec = conn.getFromPin().startsWith("exec")
                    || conn.getFromPin().equals("pass")
                    || conn.getFromPin().equals("fail");
            int col = isExec ? COL_WIRE_EXEC : COL_WIRE_STRING;
            drawBezier(dl, from[0], from[1], to[0], to[1], col, 2f);
        }
    }

    private void drawBezier(ImDrawList dl, float x1, float y1, float x2, float y2,
                             int color, float thickness) {
        float dx = Math.abs(x2 - x1) * 0.6f + 50f * zoom;
        float cp1x = x1 + dx, cp1y = y1;
        float cp2x = x2 - dx, cp2y = y2;
        dl.addBezierCubic(x1, y1, cp1x, cp1y, cp2x, cp2y, x2, y2, color, thickness);
    }

    // -------------------------------------------------------------------------
    // Interaction handling
    // -------------------------------------------------------------------------

    private void handleInteractions(DataStore.Event event, StorageProvider storage,
                                     float cx, float cy, float cw, float ch,
                                     boolean canvasHovered) {
        ImVec2 mouse = ImGui.getMousePos();
        float  mx = mouse.x, my = mouse.y;

        // --- Zoom (scroll wheel on canvas) ---
        if (canvasHovered && ImGui.getIO().getMouseWheel() != 0) {
            float delta = ImGui.getIO().getMouseWheel();
            float newZoom = Math.max(0.3f, Math.min(2.0f, zoom * (1f + delta * 0.1f)));
            // Zoom toward mouse
            float worldX = (mx - cx - panX) / zoom;
            float worldY = (my - cy - panY) / zoom;
            zoom  = newZoom;
            panX  = mx - cx - worldX * zoom;
            panY  = my - cy - worldY * zoom;
        }

        // --- Pan (middle mouse drag) ---
        if (canvasHovered && ImGui.isMouseDragging(ImGuiMouseButton.Middle)) {
            ImVec2 delta = ImGui.getMouseDragDelta(ImGuiMouseButton.Middle);
            panX += delta.x;
            panY += delta.y;
            ImGui.resetMouseDragDelta(ImGuiMouseButton.Middle);
        }

        // --- Node drag (left mouse on header) ---
        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            if (dragNodeId == null) {
                // Check if clicking a node header
                for (Map.Entry<String, float[]> e : nodeHeaderBounds.entrySet()) {
                    float[] b = e.getValue();
                    if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                        dragNodeId = e.getKey();
                        selectedNodeId = dragNodeId;
                        dragStartMouseX = mx; dragStartMouseY = my;
                        DataStore.Event.Node n = findNode(event, dragNodeId);
                        if (n != null) { dragStartNodeX = n.getX(); dragStartNodeY = n.getY(); }
                        break;
                    }
                }
            } else {
                DataStore.Event.Node n = findNode(event, dragNodeId);
                if (n != null) {
                    n.setX(dragStartNodeX + (mx - dragStartMouseX) / zoom);
                    n.setY(dragStartNodeY + (my - dragStartMouseY) / zoom);
                }
            }
        } else {
            if (dragNodeId != null) {
                storage.save();
                dragNodeId = null;
            }
        }

        // --- Pin connection (left click on output pin) ---
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            boolean hitPin = false;
            for (Map.Entry<String, float[]> e : pinScreenPos.entrySet()) {
                String key = e.getKey();
                float[] pos = e.getValue();
                if (dist(mx, my, pos[0], pos[1]) <= PIN_RADIUS * zoom + 4f) {
                    String[] parts = key.split(":", 2);
                    String   nId   = parts[0];
                    String   pId   = parts[1];
                    DataStore.Event.Node node = findNode(event, nId);
                    if (node == null) continue;
                    NodeType nt = NodeType.getByName(node.getType());
                    if (nt == null) continue;
                    boolean isOutput = nt.outputPins().stream().anyMatch(p -> p.id().equals(pId));

                    if (pendingFromNodeId != null) {
                        // Complete wire if compatible and clicking an input pin
                        if (!isOutput && isCompatible(event, pendingFromNodeId, pendingFromPin, nId, pId)) {
                            // Remove existing connection to this input if any
                            event.getConnections().removeIf(c ->
                                    c.getToNodeId().equals(nId) && c.getToPin().equals(pId));
                            event.getConnections().add(new DataStore.Event.Connection(
                                    pendingFromNodeId, pendingFromPin, nId, pId));
                            storage.save();
                        }
                        pendingFromNodeId = null;
                        hitPin = true;
                        break;
                    } else if (isOutput) {
                        pendingFromNodeId  = nId;
                        pendingFromPin     = pId;
                        pendingFromScreenX = pos[0];
                        pendingFromScreenY = pos[1];
                        hitPin = true;
                        break;
                    }
                }
            }
            if (!hitPin && pendingFromNodeId != null) {
                // Clicked elsewhere — cancel wire
                pendingFromNodeId = null;
            }
        }

        // --- Right-click context menu ---
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            // Check node right-click
            for (Map.Entry<String, float[]> e : nodeScreenBounds.entrySet()) {
                float[] b = e.getValue();
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    ImGui.openPopup("##nodeCtx_" + e.getKey());
                    break;
                }
            }
            // Check wire right-click
            if (event.getConnections() != null) {
                for (DataStore.Event.Connection conn : new ArrayList<>(event.getConnections())) {
                    float[] from = pinScreenPos.get(conn.getFromNodeId() + ":" + conn.getFromPin());
                    float[] to   = pinScreenPos.get(conn.getToNodeId()   + ":" + conn.getToPin());
                    if (from != null && to != null
                            && isNearBezier(mx, my, from[0], from[1], to[0], to[1], 6f)) {
                        ImGui.openPopup("##wireCtx_" + conn.getFromNodeId() + "_" + conn.getFromPin());
                        break;
                    }
                }
            }
        }

        // Render node context menus
        if (event.getNodes() != null) {
            for (DataStore.Event.Node node : new ArrayList<>(event.getNodes())) {
                if (ImGui.beginPopup("##nodeCtx_" + node.getId())) {
                    if (ImGui.menuItem("Delete Node")) {
                        String nId = node.getId();
                        event.getNodes().removeIf(n -> n.getId().equals(nId));
                        if (event.getConnections() != null) {
                            event.getConnections().removeIf(c ->
                                    c.getFromNodeId().equals(nId) || c.getToNodeId().equals(nId));
                        }
                        if (nId.equals(selectedNodeId)) selectedNodeId = null;
                        storage.save();
                    }
                    ImGui.endPopup();
                }
            }
        }

        // Render wire context menus
        if (event.getConnections() != null) {
            for (DataStore.Event.Connection conn : new ArrayList<>(event.getConnections())) {
                String popupId = "##wireCtx_" + conn.getFromNodeId() + "_" + conn.getFromPin();
                if (ImGui.beginPopup(popupId)) {
                    if (ImGui.menuItem("Delete Connection")) {
                        event.getConnections().remove(conn);
                        storage.save();
                    }
                    ImGui.endPopup();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private utilities
    // -------------------------------------------------------------------------

    private DataStore.Event.Node findNode(DataStore.Event event, String nodeId) {
        if (event.getNodes() == null) return null;
        return event.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst().orElse(null);
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Samples the bezier curve at 20 points and returns {@code true} if {@code (mx, my)}
     * is within {@code tolerance} pixels of any sample point.
     *
     * @param mx        mouse X in screen coordinates
     * @param my        mouse Y in screen coordinates
     * @param x1        start point X
     * @param y1        start point Y
     * @param x2        end point X
     * @param y2        end point Y
     * @param tolerance hit-test radius in pixels
     */
    private boolean isNearBezier(float mx, float my,
                                  float x1, float y1, float x2, float y2, float tolerance) {
        float dx = Math.abs(x2 - x1) * 0.6f + 50f * zoom;
        float cp1x = x1 + dx, cp1y = y1;
        float cp2x = x2 - dx, cp2y = y2;
        for (int i = 0; i <= 20; i++) {
            float t  = i / 20f;
            float it = 1f - t;
            float bx = it*it*it*x1 + 3*it*it*t*cp1x + 3*it*t*t*cp2x + t*t*t*x2;
            float by = it*it*it*y1 + 3*it*it*t*cp1y + 3*it*t*t*cp2y + t*t*t*y2;
            if (dist(mx, my, bx, by) <= tolerance) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if a wire from {@code fromPin} to {@code toPin} is type-compatible
     * (EXEC-to-EXEC or STRING-to-STRING) and does not connect a node to itself.
     *
     * @param event      the event providing the node list
     * @param fromNodeId source node id
     * @param fromPin    source pin id (output)
     * @param toNodeId   target node id
     * @param toPin      target pin id (input)
     */
    private boolean isCompatible(DataStore.Event event,
                                  String fromNodeId, String fromPin,
                                  String toNodeId,   String toPin) {
        if (fromNodeId.equals(toNodeId)) return false;
        DataStore.Event.Node fromNode = findNode(event, fromNodeId);
        DataStore.Event.Node toNode   = findNode(event, toNodeId);
        if (fromNode == null || toNode == null) return false;
        NodeType fromNt = NodeType.getByName(fromNode.getType());
        NodeType toNt   = NodeType.getByName(toNode.getType());
        if (fromNt == null || toNt == null) return false;
        NodeType.PinKind fromKind = fromNt.outputPins().stream()
                .filter(p -> p.id().equals(fromPin))
                .findFirst().map(NodeType.PinDef::kind).orElse(null);
        NodeType.PinKind toKind = toNt.inputPins().stream()
                .filter(p -> p.id().equals(toPin))
                .findFirst().map(NodeType.PinDef::kind).orElse(null);
        return fromKind != null && fromKind == toKind;
    }
}
