package fi.natroutter.baudbound.event;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.NodeType;
import fi.natroutter.baudbound.http.HttpHandler;
import fi.natroutter.baudbound.serial.DeviceConnectionManager;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.logger.FoxLogger;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

/**
 * Processes a {@link TriggerContext} through the configured node graph for each event and fires
 * the matching actions.
 * <p>
 * Each call to {@link #process(TriggerContext)} iterates all events, finds trigger nodes whose
 * type matches the incoming {@link fi.natroutter.baudbound.enums.TriggerSource}, and executes the
 * connected node graph starting from each matching trigger's {@code exec_out} pin.
 * <p>
 * Variable substitution ({@code {input}}, {@code {channel}}, {@code {timestamp}}, etc.) is
 * applied to data values immediately before execution via {@link #resolve(String, TriggerContext)}.
 */
public class EventHandler {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final DeviceConnectionManager deviceConnectionManager = BaudBound.getDeviceConnectionManager();

    /**
     * Named state map. Keys are state names; the special name {@value DEFAULT_STATE} is
     * used when no explicit name is provided.
     */
    private static final String DEFAULT_STATE = "default";
    private final Map<String, String> states = new ConcurrentHashMap<>();

    /** Cache of compiled regex patterns keyed by pattern string to avoid recompilation on every check. */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /** Tracks all currently playing {@link Clip} instances for bulk stop support. */
    private final Set<Clip> activeClips = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Returns an unmodifiable snapshot of the current state map for display purposes.
     * Keys are state names; values are the current state values.
     */
    public Map<String, String> getStates() {
        return Map.copyOf(states);
    }

    /**
     * Sets the named state to the given value. Safe to call from any thread.
     *
     * @param name  the state name; must not be blank
     * @param value the value to assign
     */
    public void setState(String name, String value) {
        states.put(name, value);
    }

    /**
     * Removes the named state entry. If {@code name} is blank, clears the default state.
     * Safe to call from any thread since {@link ConcurrentHashMap} handles concurrent access.
     *
     * @param name the state name to clear, or blank for the default state
     */
    public void clearState(String name) {
        states.remove(name == null || name.isBlank() ? DEFAULT_STATE : name.trim());
    }

    /** Removes all state entries. */
    public void clearAllStates() {
        states.clear();
    }

    /**
     * Stops and closes all currently playing sound clips. Safe to call from any thread.
     */
    public void stopAllSounds() {
        for (Clip clip : activeClips) {
            clip.stop();
        }
        logger.info("Stopped all playing sounds");
    }

    /**
     * No-op kept for API compatibility. Previously invalidated a sort cache; the node graph
     * execution engine does not maintain such a cache.
     */
    public void invalidateSortCache() {
        // No-op: the node graph executor does not sort events.
    }

    /**
     * Convenience overload for serial input — wraps the call in a {@link TriggerContext}.
     *
     * @param input  the trimmed line read from the serial port
     * @param device the device that produced this input
     */
    public void process(String input, DataStore.Device device) {
        process(TriggerContext.serial(input, device));
    }

    /**
     * Evaluates all configured events against the trigger context by executing their node graphs.
     * For each event, every trigger node whose type matches the incoming source and passes the optional
     * {@link #triggerMatchesContext device/channel filter} is used as a graph entry point. Each
     * matching trigger launches a virtual thread that owns its own {@code dataCache} and executes
     * the connected node graph synchronously from that point, calling action nodes inline.
     *
     * @param ctx the trigger context carrying the input payload, source, and optional device
     */
    public void process(TriggerContext ctx) {
        List<DataStore.Event> events = storage.getData().getEvents();

        for (DataStore.Event event : events) {
            if (event.getNodes() == null || event.getNodes().isEmpty()) continue;

            for (DataStore.Event.Node node : event.getNodes()) {
                NodeType nt = NodeType.getByName(node.getType());
                if (nt == null) continue;
                if (nt.getCategory() != NodeType.Category.TRIGGER) continue;
                if (!nt.matchesSource(ctx.source())) continue;
                if (!triggerMatchesContext(node, ctx)) continue;

                Thread.ofVirtual().start(() ->
                    executeFrom(event, node.getId(), "exec_out", ctx, new HashMap<>()));
            }
        }
    }

    /**
     * Returns {@code true} if the trigger node's optional filter params allow this context to fire.
     * <p>
     * If the trigger node has a non-blank {@code deviceFilter} param, only contexts whose device
     * name matches are accepted. If it has a non-blank {@code channelFilter} param, only WebSocket
     * contexts whose channel equals the filter are accepted.
     *
     * @param triggerNode the TRIGGER node being tested
     * @param ctx         the current trigger context
     * @return {@code true} if all filters pass or no filters are set
     */
    private boolean triggerMatchesContext(DataStore.Event.Node triggerNode, TriggerContext ctx) {
        if (triggerNode.getParams() == null) return true;
        String deviceFilter  = triggerNode.getParams().get("deviceFilter");
        String channelFilter = triggerNode.getParams().get("channelFilter");
        if (deviceFilter != null && !deviceFilter.isBlank()) {
            if (ctx.device() == null || !ctx.device().getName().equals(deviceFilter)) return false;
        }
        if (channelFilter != null && !channelFilter.isBlank()) {
            if (ctx.channel() == null || !ctx.channel().equals(channelFilter)) return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Graph execution
    // -------------------------------------------------------------------------

    /**
     * Follows the exec chain from the given node/pin, dispatching condition and action nodes.
     *
     * @param event     the event whose node graph is being executed
     * @param nodeId    the ID of the node whose exec output to follow
     * @param execPin   the exec output pin name to follow (e.g. {@code "exec_out"}, {@code "pass"}, {@code "fail"})
     * @param ctx       the trigger context
     * @param dataCache per-execution cache of already-resolved data pin values
     */
    private void executeFrom(DataStore.Event event, String nodeId, String execPin,
                              TriggerContext ctx, Map<String, String> dataCache) {
        executeFrom(event, nodeId, execPin, ctx, dataCache, 0);
    }

    /**
     * Internal recursive overload with cycle-detection depth limit.
     *
     * @param depth current recursion depth; execution halts at 256
     */
    private void executeFrom(DataStore.Event event, String nodeId, String execPin,
                              TriggerContext ctx, Map<String, String> dataCache, int depth) {
        if (depth > 256) {
            logger.warn("Node graph cycle detected in event '" + event.getName() + "' — halting");
            return;
        }

        // Find the connection that leaves from (nodeId, execPin)
        DataStore.Event.Connection conn = event.getConnections().stream()
                .filter(c -> c.getFromNodeId().equals(nodeId) && c.getFromPin().equals(execPin))
                .findFirst().orElse(null);
        if (conn == null) return;

        DataStore.Event.Node nextNode = findNode(event, conn.getToNodeId());
        if (nextNode == null) return;

        NodeType nt = NodeType.getByName(nextNode.getType());
        if (nt == null) {
            logger.warn("Unknown node type: " + nextNode.getType());
            return;
        }

        switch (nt.getCategory()) {
            case CONDITION -> {
                boolean result = evaluateCondition(event, nextNode, nt, ctx, dataCache);
                String outPin = result ? "pass" : "fail";
                executeFrom(event, nextNode.getId(), outPin, ctx, dataCache, depth + 1);
            }
            case ACTION -> {
                fireAction(event, nextNode, nt, ctx, dataCache);
                executeFrom(event, nextNode.getId(), "exec_out", ctx, dataCache, depth + 1);
            }
            default -> logger.warn("Unexpected node category in exec chain: " + nt.getCategory());
        }
    }

    // -------------------------------------------------------------------------
    // Data pin resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the value of an input pin on a node. If a wire is connected to that pin the
     * value is sourced from the upstream node's output pin; otherwise the inline param value
     * (or empty string) is used.
     *
     * @param event     the event being executed
     * @param nodeId    the ID of the node that owns the input pin
     * @param pinId     the pin ID to resolve
     * @param ctx       the trigger context
     * @param dataCache per-execution cache keyed by {@code "nodeId:pinId"}
     * @return the resolved string value; never {@code null}
     */
    private String resolvePin(DataStore.Event event, String nodeId, String pinId,
                               TriggerContext ctx, Map<String, String> dataCache) {
        String cacheKey = nodeId + ":" + pinId;
        if (dataCache.containsKey(cacheKey)) return dataCache.get(cacheKey);

        // Find wire whose toNodeId == nodeId && toPin == pinId
        DataStore.Event.Connection wire = event.getConnections().stream()
                .filter(c -> c.getToNodeId().equals(nodeId) && c.getToPin().equals(pinId))
                .findFirst().orElse(null);

        String result;
        if (wire != null) {
            result = resolveOutputPin(event, wire.getFromNodeId(), wire.getFromPin(), ctx, dataCache);
        } else {
            // No wire — use inline param or empty string
            DataStore.Event.Node node = findNode(event, nodeId);
            result = (node != null && node.getParams() != null)
                    ? node.getParams().getOrDefault(pinId, "")
                    : "";
        }
        dataCache.put(cacheKey, result);
        return result;
    }

    /**
     * Resolves the value produced by an output pin on a source node. Only TRIGGER and VALUE
     * nodes produce data output; all other categories return an empty string.
     *
     * @param event     the event being executed
     * @param nodeId    the ID of the source node
     * @param pinId     the output pin ID
     * @param ctx       the trigger context
     * @param dataCache per-execution cache keyed by {@code "nodeId:pinId"}
     * @return the resolved string value; never {@code null}
     */
    private String resolveOutputPin(DataStore.Event event, String nodeId, String pinId,
                                     TriggerContext ctx, Map<String, String> dataCache) {
        String cacheKey = nodeId + ":" + pinId;
        if (dataCache.containsKey(cacheKey)) return dataCache.get(cacheKey);

        DataStore.Event.Node node = findNode(event, nodeId);
        if (node == null) return "";

        NodeType nt = NodeType.getByName(node.getType());
        if (nt == null) return "";

        String result = switch (nt.getCategory()) {
            case TRIGGER -> nt.resolveContextPin(ctx, pinId);
            case VALUE   -> resolveValueNode(node, nt, ctx);
            default      -> "";
        };
        dataCache.put(cacheKey, result);
        return result;
    }

    /**
     * Computes the output of a VALUE-category node.
     *
     * @param node the value node
     * @param nt   the resolved {@link NodeType} for this node
     * @param ctx  the trigger context
     * @return the string value produced by the node
     */
    private String resolveValueNode(DataStore.Event.Node node, NodeType nt, TriggerContext ctx) {
        return switch (nt) {
            case GET_STATE -> {
                String stateName = node.getParams() != null
                        ? node.getParams().getOrDefault("stateName", DEFAULT_STATE)
                        : DEFAULT_STATE;
                yield states.getOrDefault(stateName, "");
            }
            case LITERAL -> {
                String val = node.getParams() != null ? node.getParams().getOrDefault("value", "") : "";
                yield resolve(val, ctx);
            }
            default -> "";
        };
    }

    // -------------------------------------------------------------------------
    // Condition evaluation
    // -------------------------------------------------------------------------

    /**
     * Evaluates a CONDITION node against the current execution context.
     *
     * @param event     the event being executed
     * @param node      the condition node to evaluate
     * @param nt        the resolved {@link NodeType} for this node
     * @param ctx       the trigger context
     * @param dataCache per-execution data pin cache
     * @return {@code true} if the condition passes, {@code false} otherwise
     */
    private boolean evaluateCondition(DataStore.Event event, DataStore.Event.Node node, NodeType nt,
                                       TriggerContext ctx, Map<String, String> dataCache) {
        try {
            return switch (nt) {
                case EQUALS -> {
                    String a = resolvePin(event, node.getId(), "data_a", ctx, dataCache);
                    String b = resolvePin(event, node.getId(), "data_b", ctx, dataCache);
                    boolean cs = node.getParams() != null && "true".equalsIgnoreCase(node.getParams().get("caseSensitive"));
                    yield cs ? a.equals(b) : a.equalsIgnoreCase(b);
                }
                case NOT_EQUALS  -> !evaluateCondition(event, node, NodeType.EQUALS, ctx, dataCache);
                case CONTAINS -> {
                    String a = resolvePin(event, node.getId(), "data_a", ctx, dataCache);
                    String b = resolvePin(event, node.getId(), "data_b", ctx, dataCache);
                    boolean cs = node.getParams() != null && "true".equalsIgnoreCase(node.getParams().get("caseSensitive"));
                    yield cs ? a.contains(b) : a.toLowerCase().contains(b.toLowerCase());
                }
                case NOT_CONTAINS -> !evaluateCondition(event, node, NodeType.CONTAINS, ctx, dataCache);
                case STARTS_WITH -> {
                    String a = resolvePin(event, node.getId(), "data_a", ctx, dataCache);
                    String b = resolvePin(event, node.getId(), "data_b", ctx, dataCache);
                    boolean cs = node.getParams() != null && "true".equalsIgnoreCase(node.getParams().get("caseSensitive"));
                    yield cs ? a.startsWith(b) : a.toLowerCase().startsWith(b.toLowerCase());
                }
                case NOT_STARTS_WITH -> !evaluateCondition(event, node, NodeType.STARTS_WITH, ctx, dataCache);
                case ENDS_WITH -> {
                    String a = resolvePin(event, node.getId(), "data_a", ctx, dataCache);
                    String b = resolvePin(event, node.getId(), "data_b", ctx, dataCache);
                    boolean cs = node.getParams() != null && "true".equalsIgnoreCase(node.getParams().get("caseSensitive"));
                    yield cs ? a.endsWith(b) : a.toLowerCase().endsWith(b.toLowerCase());
                }
                case NOT_ENDS_WITH -> !evaluateCondition(event, node, NodeType.ENDS_WITH, ctx, dataCache);
                case REGEX -> {
                    String value   = resolvePin(event, node.getId(), "data_value",   ctx, dataCache);
                    String pattern = resolvePin(event, node.getId(), "data_pattern", ctx, dataCache);
                    yield patternCache.computeIfAbsent(pattern, Pattern::compile).matcher(value).find();
                }
                case GREATER_THAN -> {
                    double a = Double.parseDouble(resolvePin(event, node.getId(), "data_a", ctx, dataCache));
                    double b = Double.parseDouble(resolvePin(event, node.getId(), "data_b", ctx, dataCache));
                    yield a > b;
                }
                case LESS_THAN -> {
                    double a = Double.parseDouble(resolvePin(event, node.getId(), "data_a", ctx, dataCache));
                    double b = Double.parseDouble(resolvePin(event, node.getId(), "data_b", ctx, dataCache));
                    yield a < b;
                }
                case BETWEEN -> {
                    double val = Double.parseDouble(resolvePin(event, node.getId(), "data_value", ctx, dataCache));
                    double min = Double.parseDouble(resolvePin(event, node.getId(), "data_min",   ctx, dataCache));
                    double max = Double.parseDouble(resolvePin(event, node.getId(), "data_max",   ctx, dataCache));
                    yield val >= min && val <= max;
                }
                case IS_NUMERIC -> {
                    String val = resolvePin(event, node.getId(), "data_value", ctx, dataCache);
                    try { Double.parseDouble(val); yield true; } catch (NumberFormatException e) { yield false; }
                }
                case IS_EMPTY -> resolvePin(event, node.getId(), "data_value", ctx, dataCache).isEmpty();
                default -> false;
            };
        } catch (NumberFormatException e) {
            logger.warn("Numeric parse failed in event '" + event.getName() + "' node " + node.getType() + ": " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Action execution
    // -------------------------------------------------------------------------

    /**
     * Fires the side effects of an ACTION node. Each action type reads its inputs from wired
     * data pins (via {@link #resolvePin}) or inline params.
     * <p>
     * This method is called synchronously on the virtual thread that owns the current graph
     * traversal, so slow I/O (HTTP, file, audio) does not block the serial read loop but also
     * does not share state with other concurrent graph executions.
     *
     * @param event     the event being executed
     * @param node      the action node
     * @param nt        the resolved {@link NodeType}
     * @param ctx       the trigger context
     * @param dataCache per-execution data pin cache
     */
    private void fireAction(DataStore.Event event, DataStore.Event.Node node, NodeType nt,
                             TriggerContext ctx, Map<String, String> dataCache) {
        try {
            switch (nt) {
                case WEBHOOK -> {
                    String name = node.getParams() != null ? node.getParams().getOrDefault("webhookName", "") : "";
                    DataStore.Actions.Webhook wh = storage.getData().getActions().getWebhooks().stream()
                            .filter(w -> w.getName().equals(name)).findFirst().orElse(null);
                    if (wh != null) {
                        DataStore.Actions.Webhook resolved = resolveWebhook(wh, ctx);
                        HttpHandler.WebhookResult result = HttpHandler.fireWebhook(resolved);
                        if (result.success()) {
                            logger.info("Webhook \"" + name + "\" responded " + result.statusCode());
                        } else {
                            logger.error("Webhook \"" + name + "\" failed: " +
                                    (result.error() != null ? result.error() : result.statusCode()));
                        }
                    } else {
                        logger.warn("Webhook not found: " + name);
                    }
                }
                case OPEN_PROGRAM -> {
                    String name = node.getParams() != null ? node.getParams().getOrDefault("programName", "") : "";
                    DataStore.Actions.Program prog = storage.getData().getActions().getPrograms().stream()
                            .filter(p -> p.getName().equals(name)).findFirst().orElse(null);
                    if (prog != null) {
                        String path = resolve(prog.getPath(), ctx);
                        String args = resolve(prog.getArguments(), ctx);
                        launchProgram(path, args, prog.isRunAsAdmin());
                        String adminTag = prog.isRunAsAdmin() ? " [admin]" : "";
                        String argsTag  = (args != null && !args.isBlank()) ? " args=\"" + args + "\"" : "";
                        logger.info("Launched program: " + name + argsTag + adminTag);
                    } else {
                        logger.warn("Program not found: " + name);
                    }
                }
                case OPEN_URL -> {
                    String url = resolve(resolvePin(event, node.getId(), "data_url", ctx, dataCache), ctx);
                    if (url != null && !url.isBlank()) {
                        FoxLib.openURL(url);
                        logger.info("Opened URL: " + url);
                    }
                }
                case TYPE_TEXT -> {
                    String text = resolve(resolvePin(event, node.getId(), "data_text", ctx, dataCache), ctx);
                    typeText(text);
                }
                case COPY_TO_CLIPBOARD -> {
                    String text = resolve(resolvePin(event, node.getId(), "data_text", ctx, dataCache), ctx);
                    if (text != null && !text.isBlank()) {
                        StringSelection selection = new StringSelection(text);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        logger.info("Copied to clipboard: " + text);
                    }
                }
                case SHOW_NOTIFICATION -> {
                    String title = resolve(resolvePin(event, node.getId(), "data_title",   ctx, dataCache), ctx);
                    String msg   = resolve(resolvePin(event, node.getId(), "data_message", ctx, dataCache), ctx);
                    showNotification(title, msg);
                }
                case WRITE_TO_FILE -> {
                    String path    = resolve(resolvePin(event, node.getId(), "data_path",    ctx, dataCache), ctx);
                    String content = resolve(resolvePin(event, node.getId(), "data_content", ctx, dataCache), ctx);
                    if (path != null && !path.isBlank()) {
                        Files.writeString(Path.of(path), content != null ? content : "",
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        logger.info("Wrote to file: " + path);
                    }
                }
                case APPEND_TO_FILE -> {
                    String path    = resolve(resolvePin(event, node.getId(), "data_path",    ctx, dataCache), ctx);
                    String content = resolve(resolvePin(event, node.getId(), "data_content", ctx, dataCache), ctx);
                    if (path != null && !path.isBlank()) {
                        Files.writeString(Path.of(path), content != null ? content : "",
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        logger.info("Appended to file: " + path);
                    }
                }
                case PLAY_SOUND -> {
                    String path = resolve(resolvePin(event, node.getId(), "data_path", ctx, dataCache), ctx);
                    playSound(path);
                }
                case SET_STATE -> {
                    String name = resolve(resolvePin(event, node.getId(), "data_name",  ctx, dataCache), ctx);
                    String val  = resolve(resolvePin(event, node.getId(), "data_value", ctx, dataCache), ctx);
                    String key  = (name == null || name.isBlank()) ? DEFAULT_STATE : name;
                    states.put(key, val != null ? val : "");
                    logger.info("State \"" + key + "\" set to: \"" + val + "\"");
                }
                case CLEAR_STATE -> {
                    String name = resolve(resolvePin(event, node.getId(), "data_name", ctx, dataCache), ctx);
                    String key  = (name == null || name.isBlank()) ? DEFAULT_STATE : name;
                    states.remove(key);
                    logger.info("State \"" + key + "\" cleared");
                }
                case SEND_TO_DEVICE -> {
                    String deviceName = resolve(resolvePin(event, node.getId(), "data_device", ctx, dataCache), ctx);
                    String text       = resolve(resolvePin(event, node.getId(), "data_text",   ctx, dataCache), ctx);
                    boolean sent = deviceConnectionManager.sendToDevice(deviceName, text);
                    if (sent) {
                        logger.info("Sent to device \"" + deviceName + "\": " + text);
                    } else {
                        logger.error("Send to Device: device \"" + deviceName + "\" is not connected or not found.");
                    }
                }
                case SEND_WEBSOCKET -> {
                    String channel = resolve(resolvePin(event, node.getId(), "data_channel", ctx, dataCache), ctx);
                    String msg     = resolve(resolvePin(event, node.getId(), "data_message", ctx, dataCache), ctx);
                    fi.natroutter.baudbound.websocket.WebSocketHandler handler = BaudBound.getWebSocketHandler();
                    if (handler == null || !handler.isRunning()) {
                        logger.warn("Send WebSocket: server is not running.");
                    } else if (channel != null && !channel.isBlank()) {
                        handler.sendToChannel(channel, msg);
                        logger.info("WebSocket sent to channel \"" + channel + "\": \"" + msg + "\"");
                    } else {
                        ctx.reply(msg);
                        logger.info("WebSocket reply sent: \"" + msg + "\"");
                    }
                }
                case RUN_COMMAND -> {
                    String cmd = resolve(resolvePin(event, node.getId(), "data_command", ctx, dataCache), ctx);
                    runCommand(cmd);
                }
            }
        } catch (Exception e) {
            logger.error("Action error in event '" + event.getName() + "': " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Node graph helpers
    // -------------------------------------------------------------------------

    /**
     * Finds a node by ID within the given event's node list.
     *
     * @param event  the event to search
     * @param nodeId the node ID to look up
     * @return the matching {@link DataStore.Event.Node}, or {@code null} if not found
     */
    private DataStore.Event.Node findNode(DataStore.Event event, String nodeId) {
        if (event.getNodes() == null) return null;
        return event.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------
    // Private action helpers
    // -------------------------------------------------------------------------

    /**
     * Launches an external program directly without going through event processing or storage lookup.
     * Intended for ad-hoc use such as the "Test" button in the program editor.
     *
     * @param path       absolute path to the executable
     * @param args       argument string, or {@code null}/blank for none
     * @param runAsAdmin when {@code true}, launches via PowerShell {@code Start-Process … -Verb RunAs}
     */
    public void launchProgram(String path, String args, boolean runAsAdmin) throws IOException {
        ProcessBuilder pb;
        if (runAsAdmin) {
            pb = (args != null && !args.isBlank())
                    ? new ProcessBuilder("powershell", "-Command", "Start-Process", "\"" + path + "\"", "-ArgumentList", "\"" + args + "\"", "-Verb", "RunAs")
                    : new ProcessBuilder("powershell", "-Command", "Start-Process", "\"" + path + "\"", "-Verb", "RunAs");
        } else {
            pb = (args != null && !args.isBlank())
                    ? new ProcessBuilder(path, args)
                    : new ProcessBuilder(path);
        }
        pb.start();
    }

    /**
     * Executes {@code command} in the OS shell.
     * Uses {@code cmd.exe /c} on Windows and {@code sh -c} on all other platforms.
     */
    private void runCommand(String command) throws IOException {
        if (command == null || command.isBlank()) return;
        ProcessBuilder pb = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("sh", "-c", command);
        pb.start();
        logger.info("Ran command: " + command);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Types {@code text} by placing it on the clipboard then simulating Ctrl+V.
     */
    private void typeText(String text) throws AWTException {
        if (text == null || text.isBlank()) return;

        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Robot robot = new Robot();
        robot.delay(50);
        robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
        robot.keyPress(java.awt.event.KeyEvent.VK_V);
        robot.keyRelease(java.awt.event.KeyEvent.VK_V);
        robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);

        logger.info("Typed text: " + text);
    }

    /**
     * Shows a system tray notification. If {@code title} or {@code msg} is blank the call is a no-op.
     *
     * @param title the notification title
     * @param msg   the notification body
     */
    private void showNotification(String title, String msg) {
        if (msg == null || msg.isBlank()) return;
        String effectiveTitle = (title != null && !title.isBlank()) ? title : BaudBound.APP_NAME;
        BaudBound.showNotification(effectiveTitle, msg, TrayIcon.MessageType.INFO);
        logger.info("Showed notification: " + msg);
    }

    /**
     * Plays the audio file at {@code filePath}, or falls back to the system beep when the path is
     * blank or the file does not exist.
     */
    private void playSound(String filePath) throws Exception {
        if (filePath == null || filePath.isBlank()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            logger.error("Sound file not found: " + filePath + " — falling back to system beep.");
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        AudioInputStream stream = AudioSystem.getAudioInputStream(file);
        Clip clip = AudioSystem.getClip();
        clip.open(stream);
        activeClips.add(clip);
        clip.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP) {
                activeClips.remove(clip);
                clip.close();
            }
        });
        clip.start();
        logger.info("Playing sound: " + filePath);
    }

    // -------------------------------------------------------------------------
    // Variable substitution
    // -------------------------------------------------------------------------

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter TIME_ONLY_FORMAT  = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Resolves all supported variable tokens in {@code template} using the full trigger context.
     * <p>Supported tokens:
     * <ul>
     *   <li><b>Input:</b> {@code {input}}, {@code {input.upper}}, {@code {input.lower}},
     *       {@code {input.trim}}, {@code {input.length}}, {@code {input.word[N]}},
     *       {@code {input.line[N]}}, {@code {input.replace[old|new]}}, {@code {input.urlencoded}}</li>
     *   <li><b>Date/time:</b> {@code {timestamp}}, {@code {timestamp.unix}}, {@code {date}},
     *       {@code {time}}, {@code {year}}, {@code {month}}, {@code {day}},
     *       {@code {hour}}, {@code {minute}}, {@code {second}}</li>
     *   <li><b>Trigger:</b> {@code {source}}, {@code {channel}}</li>
     *   <li><b>Device:</b> {@code {device}}, {@code {device.port}}, {@code {device.baud}}</li>
     *   <li><b>States:</b> {@code {state}}, {@code {state[name]}}</li>
     *   <li><b>System:</b> {@code {hostname}}, {@code {username}}, {@code {env[VAR]}},
     *       {@code {uuid}}, {@code {random[min,max]}}</li>
     * </ul>
     *
     * @param template the template string; may be {@code null}
     * @param ctx      the trigger context carrying input, device, source, and channel
     * @return the resolved string, or {@code null} if {@code template} is {@code null}
     */
    private String resolve(String template, TriggerContext ctx) {
        if (template == null) return null;
        String input   = ctx.input();
        String channel = ctx.channel() != null ? ctx.channel() : "";
        DataStore.Device device = ctx.device();

        String result = template;

        // ---- Date / time (computed once) ----
        LocalDateTime now       = LocalDateTime.now();
        long          unixSecs  = Instant.now().getEpochSecond();
        result = result
                .replace("{timestamp}",      now.format(TIMESTAMP_FORMAT))
                .replace("{timestamp.unix}", String.valueOf(unixSecs))
                .replace("{date}",           now.toLocalDate().toString())
                .replace("{time}",           now.toLocalTime().format(TIME_ONLY_FORMAT))
                .replace("{year}",           String.format("%04d", now.getYear()))
                .replace("{month}",          String.format("%02d", now.getMonthValue()))
                .replace("{day}",            String.format("%02d", now.getDayOfMonth()))
                .replace("{hour}",           String.format("%02d", now.getHour()))
                .replace("{minute}",         String.format("%02d", now.getMinute()))
                .replace("{second}",         String.format("%02d", now.getSecond()));

        // ---- Input transforms (specific first, {input} last to avoid partial matches) ----
        result = result
                .replace("{input.upper}",      input.toUpperCase())
                .replace("{input.lower}",      input.toLowerCase())
                .replace("{input.trim}",       input.trim())
                .replace("{input.length}",     String.valueOf(input.length()))
                .replace("{input.urlencoded}", URLEncoder.encode(input, StandardCharsets.UTF_8));
        result = replaceIndexedToken(result, "input\\.word", input.trim().split("\\s+", -1));
        result = replaceIndexedToken(result, "input\\.line", input.split("\n", -1));
        result = replaceInputReplace(result, input);
        result = result.replace("{input}", input);

        // ---- Trigger / event ----
        result = result
                .replace("{source}",  ctx.source().name())
                .replace("{channel}", channel);

        // ---- Device (empty string when no device context) ----
        String devName = device != null && device.getName() != null ? device.getName() : "";
        String devPort = device != null && device.getPort() != null ? device.getPort() : "";
        String devBaud = device != null ? String.valueOf(device.getBaudRate()) : "";
        result = result
                .replace("{device}",      devName)
                .replace("{device.port}", devPort)
                .replace("{device.baud}", devBaud);

        // ---- States ----
        result = result.replace("{state}", states.getOrDefault(DEFAULT_STATE, ""));
        result = replaceStateToken(result);

        // ---- System ----
        result = result
                .replace("{hostname}", getHostname())
                .replace("{username}", System.getProperty("user.name", ""))
                .replace("{uuid}",     UUID.randomUUID().toString());
        result = replaceEnvToken(result);
        result = replaceRandomToken(result);

        return result;
    }

    /** Replaces {@code {prefix[N]}} tokens using a pre-split array (0-indexed, out-of-range = empty). */
    private static String replaceIndexedToken(String text, String tokenPattern, String[] parts) {
        return Pattern.compile("\\{" + tokenPattern + "\\[(\\d+)\\]\\}")
                .matcher(text)
                .replaceAll(m -> {
                    int idx = Integer.parseInt(m.group(1));
                    return (idx >= 0 && idx < parts.length)
                            ? Matcher.quoteReplacement(parts[idx]) : "";
                });
    }

    /** Replaces {@code {input.replace[old|new]}} tokens. */
    private static String replaceInputReplace(String text, String input) {
        return Pattern.compile("\\{input\\.replace\\[([^|\\]]*?)\\|([^\\]]*?)\\]\\}")
                .matcher(text)
                .replaceAll(m -> Matcher.quoteReplacement(
                        input.replace(m.group(1), m.group(2))));
    }

    /** Replaces {@code {state[name]}} tokens with their current state values. */
    private String replaceStateToken(String text) {
        return Pattern.compile("\\{state\\[([^\\]]+)\\]\\}")
                .matcher(text)
                .replaceAll(m -> Matcher.quoteReplacement(
                        states.getOrDefault(m.group(1).trim(), "")));
    }

    /** Replaces {@code {env[VAR]}} tokens with OS environment variable values. */
    private static String replaceEnvToken(String text) {
        return Pattern.compile("\\{env\\[([^\\]]+)\\]\\}")
                .matcher(text)
                .replaceAll(m -> {
                    String val = System.getenv(m.group(1).trim());
                    return Matcher.quoteReplacement(val != null ? val : "");
                });
    }

    /** Replaces {@code {random[min,max]}} tokens with a random integer in the inclusive range. */
    private static String replaceRandomToken(String text) {
        return Pattern.compile("\\{random\\[(\\d+),(\\d+)\\]\\}")
                .matcher(text)
                .replaceAll(m -> {
                    int min = Integer.parseInt(m.group(1));
                    int max = Integer.parseInt(m.group(2));
                    if (min > max) return m.group(0);
                    return String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
                });
    }

    private static String getHostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return ""; }
    }

    /**
     * Builds a fully resolved copy of {@code original} with all variable tokens substituted.
     * When {@link DataStore.Actions.Webhook#isUrlEscape()} is {@code true}, the {@code {input}}
     * token is URL-encoded before insertion into the URL, headers, and body.
     *
     * @param original the webhook definition to resolve
     * @param ctx      the trigger context
     * @return a new {@link DataStore.Actions.Webhook} with all templates resolved
     */
    private DataStore.Actions.Webhook resolveWebhook(DataStore.Actions.Webhook original, TriggerContext ctx) {
        String input = ctx.input();
        String resolvedInput = original.isUrlEscape()
                ? URLEncoder.encode(input, StandardCharsets.UTF_8)
                : input;

        TriggerContext resolveCtx = original.isUrlEscape()
                ? new TriggerContext(resolvedInput, ctx.device(), ctx.source(), ctx.channel(), ctx.connection())
                : ctx;

        List<DataStore.Actions.Webhook.Header> resolvedHeaders = original.getHeaders() == null ? List.of() :
                original.getHeaders().stream()
                        .map(h -> new DataStore.Actions.Webhook.Header(h.getKey(), resolve(h.getValue(), resolveCtx)))
                        .toList();

        return new DataStore.Actions.Webhook(
                original.getName(),
                resolve(original.getUrl(), resolveCtx),
                original.getMethod(),
                resolvedHeaders,
                resolve(original.getBody(), resolveCtx),
                original.isUrlEscape()
        );
    }

}
