package fi.natroutter.baudbound.storage;

import fi.natroutter.foxlib.files.FileManager;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Manages loading and saving the application's {@link DataStore} to {@code storage.json}
 * in the platform-appropriate user config directory.
 * <p>
 * The config directory is resolved via {@link #getConfigDir()} so the path is stable
 * regardless of the working directory at launch (e.g. autostart on Linux). On construction
 * the file is read and parsed; if absent a default-populated file is written via the embedded
 * resource template. The bundled {@code icon.png} is also exported to the config directory
 * so external tools (e.g. Linux autostart {@code .desktop} files) can reference it by path.
 * <p>
 * After deserialization, {@link #migrateIfNeeded(DataStore)} is called to convert any events
 * still using the old {@code conditions}/{@code actions}/{@code triggerSources} model into the
 * new node-graph format ({@code nodes} + {@code connections}). If any events are migrated the
 * result is written back to disk automatically.
 * <p>
 * Call {@link #save()} after any mutation to {@code DataStore} to persist the change.
 */
public class StorageProvider {

    private final FoxLogger logger = BaudBound.getLogger();
    private final FileManager manager;

    private DataStore data;

    public StorageProvider() {
        File configDir = getConfigDir();
        if (!configDir.exists() && !configDir.mkdirs()) {
            logger.warn("Could not create config directory: " + configDir.getAbsolutePath());
        }

        exportIcon(configDir);

        File storageFile = new File(configDir, "storage.json");
        manager = new FileManager.Builder(storageFile)
                .setExportResource(true)
                .setLogger(logger)
                .setResourceFile("storage.json")
                .onFileCreation(() -> logger.info("Storage file initialized"))
                .onInitialized(e -> {
                    if (e.success() && !e.content().isEmpty()) {
                        data = DataStore.fromJson(e.content());
                    }
                })
                .onReload(e -> {
                    if (e.success() && !e.content().isEmpty()) {
                        data = DataStore.fromJson(e.content());
                        migrateIfNeeded(data);
                        logger.info("Configuration reloaded from disk");
                    } else {
                        logger.error("Failed to reload configuration");
                    }
                })
                .build();
        // Migrate after build() so that manager is non-null when save() is called
        if (data != null) migrateIfNeeded(data);
    }

    /**
     * Returns the loaded {@link DataStore}.
     *
     * @throws IllegalStateException if called before the store has been initialized
     */
    public DataStore getData() {
        if (data == null) {
            throw new IllegalStateException("DataStore accessed before initialization");
        }
        return data;
    }

    /**
     * Returns the platform-appropriate config directory for BaudBound, using
     * {@code user.home} so the path is stable regardless of the working directory.
     * <ul>
     *   <li>Windows — {@code %APPDATA%\BaudBound}</li>
     *   <li>macOS   — {@code ~/Library/Application Support/BaudBound}</li>
     *   <li>Linux   — {@code $XDG_CONFIG_HOME/BaudBound} or {@code ~/.config/BaudBound}</li>
     * </ul>
     */
    public static File getConfigDir() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        String base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = (appData != null && !appData.isEmpty()) ? appData : home;
        } else if (os.contains("mac")) {
            base = home + "/Library/Application Support";
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = (xdg != null && !xdg.isEmpty()) ? xdg : home + "/.config";
        }
        return new File(base, BaudBound.APP_NAME);
    }

    /**
     * Reloads the {@link DataStore} from disk, replacing the current in-memory state.
     * Any unsaved in-memory changes are discarded.
     */
    public void reload() {
        manager.reload();
    }

    /** Persists the current {@link DataStore} state to disk. No-op if data is null. */
    public void save() {
        if (data != null) {
            logger.info("Datastore saved");
            manager.save(data.toJson());
        }
    }

    /**
     * Copies {@code icon.png} from the JAR resources to {@code configDir/icon.png} if it
     * is not already present. This allows external tools (e.g. Linux {@code .desktop} files)
     * to reference the icon by an absolute path on disk.
     */
    private void exportIcon(File configDir) {
        File dest = new File(configDir, "icon.png");
        if (dest.exists()) return;
        try (InputStream is = BaudBound.class.getResourceAsStream("/icon.png")) {
            if (is == null) return;
            Files.copy(is, dest.toPath());
        } catch (Exception e) {
            logger.warn("Could not export icon.png to config dir: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Migration: old conditions/actions/triggerSources → nodes + connections
    // -------------------------------------------------------------------------

    /**
     * Iterates all events and migrates any that still use the old
     * {@code conditions}/{@code actions}/{@code triggerSources} model into the new
     * node-graph format. If any events are migrated, the result is saved to disk.
     *
     * @param data the {@link DataStore} to inspect and mutate in place
     */
    void migrateIfNeeded(DataStore data) {
        boolean dirty = false;
        for (DataStore.Event event : data.getEvents()) {
            if (migrateEvent(event)) dirty = true;
        }
        if (dirty) save();
    }

    /**
     * Migrates a single event from the old conditions/actions/triggerSources model to nodes+connections.
     * Returns true if migration was performed (event was modified).
     */
    private boolean migrateEvent(DataStore.Event event) {
        boolean hasOldConditions = event.getConditions() != null && !event.getConditions().isEmpty();
        boolean hasOldActions    = event.getActions()    != null && !event.getActions().isEmpty();
        if (!hasOldConditions && !hasOldActions) return false;
        if (event.getNodes() != null && !event.getNodes().isEmpty()) return false; // already migrated

        List<DataStore.Event.Node>       nodes       = new ArrayList<>();
        List<DataStore.Event.Connection> connections = new ArrayList<>();

        // --- Step 1: Trigger nodes (one per trigger source) ---
        List<String> sources = event.getEffectiveTriggerSources();
        List<String> triggerNodeIds = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            String sourceStr = sources.get(i);
            String nodeType  = mapTriggerSource(sourceStr);
            DataStore.Event.Node trigNode = new DataStore.Event.Node();
            trigNode.setId(UUID.randomUUID().toString());
            trigNode.setType(nodeType);
            trigNode.setX(0f);
            trigNode.setY(i * 80f);
            trigNode.setParams(new HashMap<>());
            nodes.add(trigNode);
            triggerNodeIds.add(trigNode.getId());
        }

        // --- Step 2: Condition nodes ---
        List<String> prevNodeIds  = new ArrayList<>(triggerNodeIds);
        String       prevExecPin  = "exec_out";

        List<DataStore.Event.Condition> conditions = event.getConditions();
        if (conditions == null) conditions = List.of();

        for (int i = 0; i < conditions.size(); i++) {
            DataStore.Event.Condition cond = conditions.get(i);
            MigratedCondition mc = migrateCondition(cond, i, nodes, connections, triggerNodeIds, prevNodeIds, prevExecPin);
            if (mc == null) continue; // unmappable — skip
            prevNodeIds  = mc.nodeIds;
            prevExecPin  = "pass";
            nodes.addAll(mc.newNodes);
            connections.addAll(mc.newConnections);
        }

        // --- Step 3: Action nodes ---
        List<DataStore.Event.Action> actions = event.getActions();
        if (actions == null) actions = List.of();

        for (int i = 0; i < actions.size(); i++) {
            DataStore.Event.Action action = actions.get(i);
            String nodeType = mapActionType(action.getType());
            if (nodeType == null) continue;

            DataStore.Event.Node actionNode = new DataStore.Event.Node();
            actionNode.setId(UUID.randomUUID().toString());
            actionNode.setType(nodeType);
            actionNode.setX(220f * (1 + (conditions == null ? 0 : conditions.size()) + i));
            actionNode.setY(0f);
            actionNode.setParams(new HashMap<>());
            setActionParams(actionNode, action);
            nodes.add(actionNode);

            for (String prevId : prevNodeIds) {
                connections.add(new DataStore.Event.Connection(prevId, prevExecPin, actionNode.getId(), "exec_in"));
            }
            prevNodeIds = List.of(actionNode.getId());
            prevExecPin = "exec_out";
        }

        event.setNodes(nodes);
        event.setConnections(connections);
        // Clear deprecated fields
        event.setConditions(new ArrayList<>());
        event.setActions(new ArrayList<>());
        event.setTriggerSources(new ArrayList<>());
        return true;
    }

    /**
     * Maps an old {@code TriggerSource} name string to the corresponding node type string.
     *
     * @param source the trigger source name (case-insensitive)
     * @return the node type string for the matching trigger node
     */
    private String mapTriggerSource(String source) {
        return switch (source.toUpperCase()) {
            case "WEBSOCKET"           -> "WEBSOCKET_TRIGGER";
            case "DEVICE_CONNECTED"    -> "DEVICE_CONNECTED";
            case "DEVICE_DISCONNECTED" -> "DEVICE_DISCONNECTED";
            default                    -> "SERIAL_TRIGGER";  // SERIAL + fallback
        };
    }

    /**
     * Maps an old {@code ActionType} name string to the corresponding node type string.
     * Returns {@code null} and logs a warning for unknown types.
     *
     * @param type the action type name (case-insensitive), may be {@code null}
     * @return the node type string, or {@code null} if the type is unknown or null
     */
    private String mapActionType(String type) {
        if (type == null) return null;
        return switch (type.toUpperCase()) {
            case "CALL_WEBHOOK"      -> "WEBHOOK";
            case "OPEN_PROGRAM"      -> "OPEN_PROGRAM";
            case "OPEN_URL"          -> "OPEN_URL";
            case "TYPE_TEXT"         -> "TYPE_TEXT";
            case "COPY_TO_CLIPBOARD" -> "COPY_TO_CLIPBOARD";
            case "SHOW_NOTIFICATION" -> "SHOW_NOTIFICATION";
            case "WRITE_TO_FILE"     -> "WRITE_TO_FILE";
            case "APPEND_TO_FILE"    -> "APPEND_TO_FILE";
            case "PLAY_SOUND"        -> "PLAY_SOUND";
            case "SET_STATE"         -> "SET_STATE";
            case "CLEAR_STATE"       -> "CLEAR_STATE";
            case "SEND_TO_DEVICE"    -> "SEND_TO_DEVICE";
            case "SEND_WEBSOCKET"    -> "SEND_WEBSOCKET";
            case "RUN_COMMAND"       -> "RUN_COMMAND";
            default -> { logger.warn("Migration: unknown ActionType: " + type); yield null; }
        };
    }

    /**
     * Populates inline {@code params} on a migrated action node from the old single-value
     * {@link DataStore.Event.Action#getValue() action value} field.
     * <p>
     * Several action types used a pipe-delimited compound value in the old format
     * (e.g. {@code "Title|Message"} for {@code SHOW_NOTIFICATION}); this method splits
     * those and places each part in the appropriate named param key.
     *
     * @param node   the newly created action node whose params will be populated
     * @param action the old action being migrated
     */
    private void setActionParams(DataStore.Event.Node node, DataStore.Event.Action action) {
        String val = action.getValue() != null ? action.getValue() : "";
        switch (node.getType()) {
            case "WEBHOOK"           -> node.getParams().put("webhookName",  val);
            case "OPEN_PROGRAM"      -> node.getParams().put("programName",  val);
            case "OPEN_URL"          -> node.getParams().put("data_url",     val);
            case "TYPE_TEXT"         -> node.getParams().put("data_text",    val);
            case "COPY_TO_CLIPBOARD" -> node.getParams().put("data_text",    val);
            case "SHOW_NOTIFICATION" -> {
                // Old format: "Title|Message"
                String[] parts = val.split("\\|", 2);
                node.getParams().put("data_title",   parts.length > 0 ? parts[0] : val);
                node.getParams().put("data_message", parts.length > 1 ? parts[1] : "");
            }
            case "WRITE_TO_FILE", "APPEND_TO_FILE" -> {
                // Old format: "path|content"
                String[] parts = val.split("\\|", 2);
                node.getParams().put("data_path",    parts.length > 0 ? parts[0] : val);
                node.getParams().put("data_content", parts.length > 1 ? parts[1] : "");
            }
            case "PLAY_SOUND"        -> node.getParams().put("data_path",    val);
            case "SET_STATE"         -> {
                // Old format: "name|value" or just "value" (uses default state)
                String[] parts = val.split("\\|", 2);
                node.getParams().put("data_name",  parts.length > 1 ? parts[0] : "default");
                node.getParams().put("data_value", parts.length > 1 ? parts[1] : val);
            }
            case "CLEAR_STATE"       -> node.getParams().put("data_name",    val.isEmpty() ? "default" : val);
            case "SEND_TO_DEVICE"    -> {
                // Old format: "deviceName|text"
                String[] parts = val.split("\\|", 2);
                node.getParams().put("data_device", parts.length > 0 ? parts[0] : val);
                node.getParams().put("data_text",   parts.length > 1 ? parts[1] : "");
            }
            case "SEND_WEBSOCKET"    -> {
                // Old format: "channel|message" or just message (reply to sender)
                String[] parts = val.split("\\|", 2);
                node.getParams().put("data_channel", parts.length > 1 ? parts[0] : "");
                node.getParams().put("data_message", parts.length > 1 ? parts[1] : val);
            }
            case "RUN_COMMAND"       -> node.getParams().put("data_command", val);
        }
    }

    /** Carries the result of migrating a single old condition into one or more nodes and connections. */
    private record MigratedCondition(List<String> nodeIds, List<DataStore.Event.Node> newNodes,
                                     List<DataStore.Event.Connection> newConnections) {}

    /**
     * Migrates a single old {@link DataStore.Event.Condition} into node-graph elements.
     * <p>
     * Creates a condition node and wires its exec input from all {@code prevNodeIds} via
     * {@code prevExecPin}. Data pins are wired by {@link #wireMigratedConditionData}.
     *
     * @param cond          the old condition to migrate
     * @param index         zero-based position of this condition in the event (used for X placement)
     * @param allNodes      the accumulated node list (read-only in this method; appended by caller)
     * @param allConnections the accumulated connection list (read-only in this method)
     * @param triggerNodeIds IDs of the trigger nodes created in Step 1
     * @param prevNodeIds   IDs of the nodes that should feed this condition's exec_in
     * @param prevExecPin   the exec output pin name on each previous node
     * @return the migration result, or {@code null} if the condition type is unrecognised
     */
    private MigratedCondition migrateCondition(
            DataStore.Event.Condition cond,
            int index,
            List<DataStore.Event.Node> allNodes,
            List<DataStore.Event.Connection> allConnections,
            List<String> triggerNodeIds,
            List<String> prevNodeIds,
            String prevExecPin) {

        String ct  = cond.getType()  != null ? cond.getType().toUpperCase()  : "";
        String val = cond.getValue() != null ? cond.getValue() : "";

        List<DataStore.Event.Node>       newNodes = new ArrayList<>();
        List<DataStore.Event.Connection> newConns = new ArrayList<>();

        String condNodeType = mapConditionType(ct);
        if (condNodeType == null) {
            logger.warn("Migration: unknown ConditionType: " + ct);
            return null;
        }

        float xPos = 220f * (1 + index);

        DataStore.Event.Node condNode = new DataStore.Event.Node();
        condNode.setId(UUID.randomUUID().toString());
        condNode.setType(condNodeType);
        condNode.setX(xPos);
        condNode.setY(0f);
        condNode.setParams(new HashMap<>());
        if (cond.isCaseSensitive()) {
            condNode.getParams().put("caseSensitive", "true");
        }
        newNodes.add(condNode);

        // Wire exec: all prev → condNode.exec_in
        for (String prevId : prevNodeIds) {
            newConns.add(new DataStore.Event.Connection(prevId, prevExecPin, condNode.getId(), "exec_in"));
        }

        // Wire data pins and set params based on condition type
        wireMigratedConditionData(ct, val, condNode, newNodes, newConns, triggerNodeIds, xPos, index);

        return new MigratedCondition(List.of(condNode.getId()), newNodes, newConns);
    }

    /**
     * Maps an old {@code ConditionType} name string to the corresponding node type string.
     * Returns {@code null} for unrecognised types.
     *
     * @param ct the condition type name (already upper-cased)
     * @return the node type string, or {@code null} if unrecognised
     */
    private String mapConditionType(String ct) {
        return switch (ct) {
            case "INPUT_EQUALS"                  -> "EQUALS";
            case "INPUT_NOT_EQUALS"              -> "NOT_EQUALS";
            case "INPUT_CONTAINS"                -> "CONTAINS";
            case "INPUT_NOT_CONTAINS"            -> "NOT_CONTAINS";
            case "INPUT_STARTS_WITH"             -> "STARTS_WITH";
            case "INPUT_NOT_STARTS_WITH"         -> "NOT_STARTS_WITH";
            case "INPUT_ENDS_WITH"               -> "ENDS_WITH";
            case "INPUT_REGEX"                   -> "REGEX";
            case "INPUT_IS_NUMERIC"              -> "IS_NUMERIC";
            case "INPUT_GREATER_THAN"            -> "GREATER_THAN";
            case "INPUT_LESS_THAN"               -> "LESS_THAN";
            case "INPUT_BETWEEN"                 -> "BETWEEN";
            case "INPUT_LENGTH_EQUALS"           -> "EQUALS";   // approximate: compare length string
            case "STATE_EQUALS"                  -> "EQUALS";
            case "STATE_NOT_EQUALS"              -> "NOT_EQUALS";
            case "STATE_IS_EMPTY"                -> "IS_EMPTY";
            case "STATE_IS_NUMERIC"              -> "IS_NUMERIC";
            case "STATE_LESS_THAN"               -> "LESS_THAN";
            case "STATE_GREATER_THAN"            -> "GREATER_THAN";
            case "STATE_BETWEEN"                 -> "BETWEEN";
            case "DEVICE_EQUALS"                 -> "EQUALS";
            case "DEVICE_NOT_EQUALS"             -> "NOT_EQUALS";
            case "WEBSOCKET_HAS_PARAM"           -> "IS_EMPTY";  // inverted: route fail path
            case "WEBSOCKET_PARAM_EQUALS"        -> "EQUALS";
            case "WEBSOCKET_PARAM_NOT_EQUALS"    -> "NOT_EQUALS";
            case "WEBSOCKET_PARAM_CONTAINS"      -> "CONTAINS";
            case "WEBSOCKET_PARAM_STARTS_WITH"   -> "STARTS_WITH";
            case "WEBSOCKET_PARAM_ENDS_WITH"     -> "ENDS_WITH";
            case "WEBSOCKET_CHANNEL_EQUALS"      -> "EQUALS";
            case "WEBSOCKET_CHANNEL_NOT_EQUALS"  -> "NOT_EQUALS";
            case "WEBSOCKET_CHANNEL_STARTS_WITH" -> "STARTS_WITH";
            case "WEBSOCKET_CHANNEL_CONTAINS"    -> "CONTAINS";
            default -> null;
        };
    }

    /**
     * Wires the data pins of a migrated condition node based on the old condition type.
     * <p>
     * <ul>
     *   <li>{@code INPUT_*} and {@code WEBSOCKET_PARAM_*} — wires the first trigger node's
     *       {@code data_input} to the condition's primary input pin.</li>
     *   <li>{@code STATE_*} — creates a {@code GET_STATE} helper node and wires its
     *       {@code data_value} output to the condition's primary input pin.</li>
     *   <li>{@code DEVICE_*} — wires the first trigger node's {@code data_device} output.</li>
     *   <li>{@code WEBSOCKET_CHANNEL_*} — wires the first trigger node's {@code data_channel} output.</li>
     *   <li>{@code WEBSOCKET_HAS_PARAM} — wires {@code data_input} to {@code IS_EMPTY.data_value}
     *       (best-effort; exec direction cannot be fully corrected here).</li>
     * </ul>
     * Comparison values are stored inline in the node's {@code params} map.
     *
     * @param ct             the old condition type name (already upper-cased)
     * @param val            the old condition value string
     * @param condNode       the newly created condition node to populate
     * @param newNodes       the mutable list of new nodes for this condition (may receive GET_STATE)
     * @param newConns       the mutable list of new connections for this condition
     * @param triggerNodeIds IDs of the trigger nodes created in Step 1
     * @param xPos           canvas X position for any helper nodes
     * @param index          zero-based condition index (unused; kept for future placement logic)
     */
    private void wireMigratedConditionData(
            String ct, String val,
            DataStore.Event.Node condNode,
            List<DataStore.Event.Node> newNodes,
            List<DataStore.Event.Connection> newConns,
            List<String> triggerNodeIds,
            float xPos, int index) {

        String trigId = triggerNodeIds.isEmpty() ? null : triggerNodeIds.get(0);

        if (ct.startsWith("INPUT_") || ct.startsWith("WEBSOCKET_PARAM_")) {
            // Wire trigger.data_input → condNode primary data_a or data_value
            if (trigId != null) {
                String toPin = switch (condNode.getType()) {
                    case "REGEX", "IS_NUMERIC", "IS_EMPTY", "BETWEEN" -> "data_value";
                    default -> "data_a";
                };
                newConns.add(new DataStore.Event.Connection(trigId, "data_input", condNode.getId(), toPin));
            }
            // Place the value in params (data_b or data_pattern etc.)
            if (condNode.getType().equals("REGEX")) {
                condNode.getParams().put("data_pattern", val);
            } else if (condNode.getType().equals("BETWEEN")) {
                String[] parts = val.split(",", 2);
                condNode.getParams().put("data_min", parts.length > 0 ? parts[0].trim() : "");
                condNode.getParams().put("data_max", parts.length > 1 ? parts[1].trim() : "");
            } else if (!condNode.getType().equals("IS_NUMERIC") && !condNode.getType().equals("IS_EMPTY")) {
                condNode.getParams().put("data_b", val);
            }

        } else if (ct.startsWith("STATE_")) {
            // Parse "stateName|expectedValue" or just "expectedValue"
            String stateName;
            String stateVal;
            if (val.contains("|")) {
                String[] parts = val.split("\\|", 2);
                stateName = parts[0];
                stateVal  = parts[1];
            } else {
                stateName = "default";
                stateVal  = val;
            }
            // Create GET_STATE node
            DataStore.Event.Node stateNode = new DataStore.Event.Node();
            stateNode.setId(UUID.randomUUID().toString());
            stateNode.setType("GET_STATE");
            stateNode.setX(xPos);
            stateNode.setY(60f);
            stateNode.setParams(new HashMap<>());
            stateNode.getParams().put("stateName", stateName);
            newNodes.add(stateNode);

            // Wire GET_STATE.data_value → condNode primary pin
            String toPin = switch (condNode.getType()) {
                case "IS_EMPTY", "IS_NUMERIC", "BETWEEN" -> "data_value";
                default -> "data_a";
            };
            newConns.add(new DataStore.Event.Connection(stateNode.getId(), "data_value", condNode.getId(), toPin));

            // Place comparison value in params
            if (!condNode.getType().equals("IS_EMPTY") && !condNode.getType().equals("IS_NUMERIC")) {
                if (condNode.getType().equals("BETWEEN")) {
                    String[] parts = stateVal.split(",", 2);
                    condNode.getParams().put("data_min", parts.length > 0 ? parts[0].trim() : "");
                    condNode.getParams().put("data_max", parts.length > 1 ? parts[1].trim() : "");
                } else {
                    condNode.getParams().put("data_b", stateVal);
                }
            }

        } else if (ct.startsWith("DEVICE_")) {
            if (trigId != null) {
                newConns.add(new DataStore.Event.Connection(trigId, "data_device", condNode.getId(), "data_a"));
            }
            condNode.getParams().put("data_b", val);

        } else if (ct.startsWith("WEBSOCKET_CHANNEL_")) {
            if (trigId != null) {
                newConns.add(new DataStore.Event.Connection(trigId, "data_channel", condNode.getId(), "data_a"));
            }
            condNode.getParams().put("data_b", val);

        } else if (ct.equals("WEBSOCKET_HAS_PARAM")) {
            // IS_EMPTY node — execution continues on fail (meaning value exists)
            // Wire trigger.data_input → IS_EMPTY.data_value
            if (trigId != null) {
                newConns.add(new DataStore.Event.Connection(trigId, "data_input", condNode.getId(), "data_value"));
            }
            // Note: caller chains on "pass" by default; this case should chain on "fail"
            // We can't fix the chain direction here without structural change — leave as best-effort migration
        }
    }
}
