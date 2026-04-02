# Node Editor Event System — Design Spec
**Date:** 2026-04-02

## Overview

Replace the flat conditions/actions table editor with a Blueprint-style node graph editor embedded in the event editor modal dialog. Users build events by wiring together Trigger, Condition, Value, and Action nodes on a pannable/zoomable canvas. A collapsible sidebar provides all available node types.

---

## 1. Data Model

### `DataStore.Event` changes

Add two new fields; mark the old ones `@Deprecated` for migration:

```java
List<Node> nodes = new ArrayList<>();
List<Connection> connections = new ArrayList<>();

@Deprecated List<Condition> conditions = new ArrayList<>();   // migration only
@Deprecated List<Action> actions = new ArrayList<>();         // migration only
@Deprecated List<String> triggerSources = new ArrayList<>();  // migration only
```

### `DataStore.Node`

```java
@Data @AllArgsConstructor @NoArgsConstructor
class Node {
    String id;                        // UUID, stable across saves
    String type;                      // NodeType enum name
    float x, y;                       // canvas position (unscaled)
    Map<String, String> params;       // inline values for unconnected pins
}
```

### `DataStore.Connection`

```java
@Data @AllArgsConstructor @NoArgsConstructor
class Connection {
    String fromNodeId;
    String fromPin;   // e.g. "exec_out", "pass", "fail", "data_input"
    String toNodeId;
    String toPin;     // e.g. "exec_in", "data_a"
}
```

---

## 2. NodeType Enum

Replaces `ConditionType`, `ActionType`, and `TriggerSource`. Old enums are deleted.

### Trigger nodes (no exec_in)

| NodeType | Output exec pins | Output data pins |
|---|---|---|
| `SERIAL_TRIGGER` | `exec_out` | `data_input` (string), `data_device` (string) |
| `WEBSOCKET_TRIGGER` | `exec_out` | `data_input` (string), `data_channel` (string) |
| `DEVICE_CONNECTED` | `exec_out` | `data_device` (string) |
| `DEVICE_DISCONNECTED` | `exec_out` | `data_device` (string) |

### Condition nodes (exec_in → pass / fail)

| NodeType | Data input pins | Params |
|---|---|---|
| `EQUALS` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `NOT_EQUALS` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `CONTAINS` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `NOT_CONTAINS` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `STARTS_WITH` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `NOT_STARTS_WITH` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `ENDS_WITH` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `NOT_ENDS_WITH` | `data_a`, `data_b` | `caseSensitive` (bool) |
| `REGEX` | `data_value`, `data_pattern` | — |
| `GREATER_THAN` | `data_a`, `data_b` | — |
| `LESS_THAN` | `data_a`, `data_b` | — |
| `BETWEEN` | `data_value`, `data_min`, `data_max` | — |
| `IS_NUMERIC` | `data_value` | — |
| `IS_EMPTY` | `data_value` | — |

### Value nodes (no exec — pure data sources)

| NodeType | Output data pin | Params |
|---|---|---|
| `GET_STATE` | `data_value` (string) | `stateName` |
| `LITERAL` | `data_value` (string) | `value` |

### Action nodes (exec_in → exec_out)

| NodeType | Data input pins | Params |
|---|---|---|
| `WEBHOOK` | — | `webhookName` |
| `OPEN_PROGRAM` | — | `programName` |
| `OPEN_URL` | `data_url` | — |
| `TYPE_TEXT` | `data_text` | — |
| `SET_STATE` | `data_name`, `data_value` | — |
| `CLEAR_STATE` | `data_name` | — |
| `SEND_TO_DEVICE` | `data_device`, `data_text` | — |
| `SEND_WEBSOCKET` | `data_channel`, `data_message` | — |
| `RUN_COMMAND` | `data_command` | — |
| `COPY_TO_CLIPBOARD` | `data_text` | — |
| `SHOW_NOTIFICATION` | `data_title`, `data_message` | — |
| `WRITE_TO_FILE` | `data_path`, `data_content` | — |
| `APPEND_TO_FILE` | `data_path`, `data_content` | — |
| `PLAY_SOUND` | `data_path` | — |

**Pin kinds:**
- `EXEC` — white `▶`, controls execution flow
- `STRING` — pink `●`, carries string data

**Unconnected data input pins** show an inline `ImGui.inputText` field for a literal value. `{input}`, `{channel}`, `{timestamp}` substitution tokens work in these inline fields.

---

## 3. Node Editor Renderer

### Layout

`EventEditorDialog` is restructured to contain a `NodeEditorCanvas` instance. The dialog uses a two-column layout:

```
┌──────────────────────────────────────────────────────────┐
│ [Sidebar 160px fixed]  │  [Canvas — fills remaining]     │
│                        │  ┌──────────────────────────┐   │
│ ▼ Triggers             │  │ Name:[_______] [Save] [X] │   │
│   Serial               │  ├──────────────────────────┤   │
│   WebSocket            │  │                          │   │
│   Device Connected     │  │   node graph canvas      │   │
│   Device Disconnected  │  │                          │   │
│ ▼ Conditions           │  └──────────────────────────┘   │
│   Equals               │                                  │
│   Contains ...         │                                  │
│ ▼ Actions              │                                  │
│   Webhook ...          │                                  │
│ ▼ Values               │                                  │
│   Get State            │                                  │
│   Literal              │                                  │
└──────────────────────────────────────────────────────────┘
```

### Sidebar — `ImGui.beginChild("##sidebar", 160, 0)`

- `ImGui.collapsingHeader()` per category (Triggers, Conditions, Actions, Values)
- Each item is an `ImGui.button()`; click spawns the node at canvas center
- Scrollable to handle long lists

### Canvas — `NodeEditorCanvas` class

**State fields:**
```java
Vector2f canvasOffset = new Vector2f(0, 0);  // pan offset in pixels
float zoom = 1.0f;                            // 0.5 – 2.0
String draggedNodeId = null;
PendingWire pendingWire = null;               // in-progress connection drag
String selectedNodeId = null;
```

**Canvas header strip** (drawn as child region above the graph):
- `ImString eventName` field, Save button, Cancel button

**Background grid:**
- `ImDrawList.addLine()` calls for a dot/line grid, scrolls with `canvasOffset`

**Screen-space conversion:**
```java
Vector2f toScreen(float nx, float ny) {
    return canvasOrigin + new Vector2f(nx * zoom + canvasOffset.x,
                                       ny * zoom + canvasOffset.y);
}
```

### Node rendering (per node each frame)

1. `addRectFilled(pos, pos+size, bodyColor)` — dark background
2. `addRectFilled(pos, pos+headerBottom, headerColor)` — colored header by category
3. `addText(...)` — node title
4. Per **output pin** (right side):
   - `addCircleFilled(pinPos, 5, pinColor)`
   - `addText(label)`
   - `invisibleButton` for hit detection → tracks `hoveredPin`
5. Per **input pin** (left side):
   - `addCircleFilled(pinPos, 5, pinColor)`
   - If **no wire connected**: `setCursorScreenPos` + `setNextItemWidth(80)` + `inputText` → writes to `node.params`
   - If **wire connected**: label only
6. `addRect(pos, pos+size, borderColor)` — border, brightened if hovered/selected

**Node header color by category:**
- Trigger: `#e94560`
- Condition: `#4ade80`
- Action: `#60a5fa`
- Value: `#facc15`

### Node dragging

- Each frame: hit-test mouse against all node header rects → set `hoveredNodeId`
- `isMouseClicked(0)` on header → `draggedNodeId = nodeId`
- While dragging: `node.x += delta.x / zoom`, `node.y += delta.y / zoom`
- On release: clear `draggedNodeId`, call `storage.save()`

### Pin connection

- Each frame: hit-test mouse against all pin circles → set `hoveredPin`
- `isMouseClicked(0)` on **output pin** → set `pendingWire = {fromNodeId, fromPin, screenPos}`
- While `pendingWire != null`: draw bezier to `ImGui.getMousePos()`
- Release over **compatible input pin** (EXEC→EXEC or STRING→STRING): add `Connection`, clear `pendingWire`
- Release elsewhere: cancel `pendingWire`
- **Right-click on wire**: delete that connection. Hit tolerance: 6px from the bezier curve (sampled at 20 points along the curve each frame for hover detection)
- **Right-click on node**: context menu with "Delete node" (also deletes its connections)

### Wire rendering (per connection)

```java
Vector2f p1 = outputPinScreenPos(conn.fromNodeId, conn.fromPin);
Vector2f p2 = inputPinScreenPos(conn.toNodeId, conn.toPin);
Vector2f cp1 = p1.add(150 * zoom, 0);
Vector2f cp2 = p2.sub(150 * zoom, 0);
int color = isExecPin(conn.fromPin) ? WHITE : PINK;
drawList.addBezierCubic(p1, cp1, cp2, p2, color, 2.0f);
```

### Canvas panning and zoom

- Middle-mouse drag or Space+left-drag: `canvasOffset += mouseDelta`
- Scroll wheel: `zoom = clamp(zoom * (1 + delta * 0.1), 0.5, 2.0)`, zoom toward mouse position

---

## 4. Graph Execution Engine

`EventHandler.process(TriggerContext ctx)` is rewritten:

### Entry point

```
For each Event:
  triggerNodes = nodes where NodeType.category == TRIGGER
                 && NodeType.matchesSource(node.type, ctx.source())
  For each triggerNode:
    if triggerMatchesContext(triggerNode, ctx):
      executeFrom(triggerNode.id, "exec_out", ctx, new HashMap<>())
```

**`triggerMatchesContext`**: checks optional device/channel filter params on the trigger node against the context.

### `executeFrom(nodeId, execPin, ctx, dataCache)`

```
Follow exec connection from (nodeId, execPin) → (nextNodeId, nextPin)
If no connection: stop

nextNode = find node by nextNodeId
switch nextNode.type.category:
  CONDITION:
    result = evaluateCondition(nextNode, ctx, dataCache)
    if result: executeFrom(nextNode.id, "pass", ctx, dataCache)
    else:      executeFrom(nextNode.id, "fail", ctx, dataCache)
  ACTION:
    fireAction(nextNode, ctx, dataCache)
    executeFrom(nextNode.id, "exec_out", ctx, dataCache)
```

Cycle guard: depth counter, max 256 hops, logs warning and halts.

### `resolvePin(nodeId, pinId, ctx, dataCache)`

1. Return cached value if present in `dataCache`
2. Find wire whose `toNodeId == nodeId && toPin == pinId`
3. If wire found: resolve `(wire.fromNodeId, wire.fromPin)` recursively
4. For trigger nodes: map pin name to context field (`data_input→ctx.input()`, etc.)
5. For `GET_STATE` nodes: `states.get(node.params.get("stateName"))`
6. For `LITERAL` nodes: `node.params.get("value")`
7. No wire and no param: return `""` (empty string, never null)
8. Cache result, return

### Condition evaluation

| NodeType | Logic |
|---|---|
| `EQUALS` | `a.equals(b)` or `a.equalsIgnoreCase(b)` |
| `NOT_EQUALS` | `!EQUALS` |
| `CONTAINS` | `a.contains(b)` |
| `STARTS_WITH` | `a.startsWith(b)` |
| `ENDS_WITH` | `a.endsWith(b)` |
| `REGEX` | `cachedPattern(pattern).matcher(value).find()` |
| `GREATER_THAN` | `parseDouble(a) > parseDouble(b)` |
| `LESS_THAN` | `parseDouble(a) < parseDouble(b)` |
| `BETWEEN` | `parseDouble(min) <= parseDouble(value) <= parseDouble(max)` |
| `IS_NUMERIC` | value parses as double |
| `IS_EMPTY` | `value.isEmpty()` |

Numeric parse failures → condition evaluates false, logs a warning.

**Regex pattern cache:** `ConcurrentHashMap<String, Pattern>` keyed by pattern string, same as today.

**State store:** `ConcurrentHashMap<String, String> states` unchanged.

---

## 5. Migration

Runs in `StorageProvider.load()` before returning the data:

```
For each event where conditions.size() > 0 || actions.size() > 0:
  1. Create Trigger nodes — one per triggerSources entry (default: SERIAL_TRIGGER)
     Position: x=0, y=0
  2. Create Condition nodes — one per old Condition, left-to-right
     Position: x = 220 * (1 + index), y = 0
     Map ConditionType → NodeType (see table below)
     Parse pipe-delimited value into params and optional GET_STATE value node
  3. Chain exec: trigger.exec_out → cond[0].exec_in,
                 cond[n].pass → cond[n+1].exec_in, ...
  4. Create Action nodes — one per old Action
     Position: x = 220 * (1 + conditionCount + index), y = 0
  5. Chain exec: lastCondition.pass → action[0].exec_in,
                 action[n].exec_out → action[n+1].exec_in
  6. Clear deprecated fields: conditions, actions, triggerSources

Assign fresh UUIDs to all created nodes.
```

**ConditionType → NodeType mapping:**

| Old ConditionType | New NodeType | Notes |
|---|---|---|
| `INPUT_EQUALS` | `EQUALS` | wire trigger `data_input` → pin `data_a` |
| `INPUT_NOT_EQUALS` | `NOT_EQUALS` | wire trigger `data_input` → pin `data_a` |
| `INPUT_CONTAINS` | `CONTAINS` | wire trigger `data_input` → pin `data_a` |
| `INPUT_NOT_CONTAINS` | `NOT_CONTAINS` | wire trigger `data_input` → pin `data_a` |
| `INPUT_STARTS_WITH` | `STARTS_WITH` | wire trigger `data_input` → pin `data_a` |
| `INPUT_ENDS_WITH` | `ENDS_WITH` | wire trigger `data_input` → pin `data_a` |
| `INPUT_REGEX` | `REGEX` | wire trigger `data_input` → pin `data_value` |
| `INPUT_IS_NUMERIC` | `IS_NUMERIC` | wire trigger `data_input` → pin `data_value` |
| `INPUT_GREATER_THAN` | `GREATER_THAN` | wire trigger `data_input` → pin `data_a` |
| `INPUT_LESS_THAN` | `LESS_THAN` | wire trigger `data_input` → pin `data_a` |
| `INPUT_BETWEEN` | `BETWEEN` | wire trigger `data_input` → pin `data_value`; split value on `\|` for min/max |
| `STATE_EQUALS` | `EQUALS` + `GET_STATE` | GET_STATE(stateName) → pin `data_a`; value → pin `data_b` |
| `STATE_NOT_EQUALS` | `NOT_EQUALS` + `GET_STATE` | same pattern |
| `STATE_IS_EMPTY` | `IS_EMPTY` + `GET_STATE` | GET_STATE → pin `data_value` |
| `STATE_IS_NUMERIC` | `IS_NUMERIC` + `GET_STATE` | GET_STATE → pin `data_value` |
| `STATE_LESS_THAN` | `LESS_THAN` + `GET_STATE` | GET_STATE → pin `data_a` |
| `STATE_GREATER_THAN` | `GREATER_THAN` + `GET_STATE` | GET_STATE → pin `data_a` |
| `STATE_BETWEEN` | `BETWEEN` + `GET_STATE` | GET_STATE → pin `data_value` |
| `DEVICE_EQUALS` | `EQUALS` | wire trigger `data_device` → pin `data_a` |
| `DEVICE_NOT_EQUALS` | `NOT_EQUALS` | wire trigger `data_device` → pin `data_a` |
| `WEBSOCKET_CHANNEL_EQUALS` | `EQUALS` | wire trigger `data_channel` → pin `data_a` |
| `WEBSOCKET_HAS_PARAM` | `NOT_EMPTY` (alias `IS_EMPTY` + fail path) | wire trigger `data_input` → pin `data_value`; use fail→pass inversion via routing |
| `WEBSOCKET_PARAM_EQUALS` | `EQUALS` | wire trigger `data_input` → pin `data_a` |
| `WEBSOCKET_PARAM_NOT_EQUALS` | `NOT_EQUALS` | wire trigger `data_input` → pin `data_a` |
| `WEBSOCKET_PARAM_CONTAINS` | `CONTAINS` | wire trigger `data_input` → pin `data_a` |
| `WEBSOCKET_PARAM_STARTS_WITH` | `STARTS_WITH` | wire trigger `data_input` → pin `data_a` |
| `WEBSOCKET_PARAM_ENDS_WITH` | `ENDS_WITH` | wire trigger `data_input` → pin `data_a` |
| `WEBSOCKET_CHANNEL_EQUALS` | `EQUALS` | wire trigger `data_channel` → pin `data_a` |
| `WEBSOCKET_CHANNEL_NOT_EQUALS` | `NOT_EQUALS` | wire trigger `data_channel` → pin `data_a` |
| `WEBSOCKET_CHANNEL_STARTS_WITH` | `STARTS_WITH` | wire trigger `data_channel` → pin `data_a` |
| `WEBSOCKET_CHANNEL_CONTAINS` | `CONTAINS` | wire trigger `data_channel` → pin `data_a` |

**ActionType → NodeType mapping:** direct 1:1 (names align after stripping prefix).

---

## 6. Ripple Changes Summary

| File | Change |
|---|---|
| `enums/ConditionType.java` | Deleted |
| `enums/ActionType.java` | Deleted |
| `enums/TriggerSource.java` | Deleted |
| `enums/NodeType.java` | New — replaces all three |
| `event/EventHandler.java` | Rewritten around graph execution |
| `event/TriggerContext.java` | Unchanged |
| `gui/dialog/EventEditorDialog.java` | Gutted; delegates to `NodeEditorCanvas` |
| `gui/NodeEditorCanvas.java` | New class — full canvas renderer |
| `gui/windows/EventsWindow.java` | Condition/Action count columns → node counts by category |
| `command/commands/EventsCommand.java` | Show trigger/condition/action node counts |
| `storage/DataStore.java` | Add `nodes`, `connections`; deprecate old fields |
| `storage/StorageProvider.java` | Add migration pass on load |
| `gui/dialog/SimulateDialog.java` | Unchanged |
| `gui/windows/StatesWindow.java` | Unchanged |
| `websocket/WebSocketHandler.java` | Unchanged |

---

## Out of Scope

- Undo/redo history for node edits
- Copy/paste nodes
- Node groups / subgraphs
- Auto-layout button (nodes positioned at creation time only)
- Non-string pin types (all data is string, matching current model)
