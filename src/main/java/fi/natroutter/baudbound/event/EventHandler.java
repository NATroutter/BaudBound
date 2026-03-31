package fi.natroutter.baudbound.event;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ActionType;
import fi.natroutter.baudbound.enums.ConditionType;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

/**
 * Processes a single line of serial input against the configured event list and fires
 * the matching actions.
 * <p>
 * Each call to {@link #process} evaluates every enabled event in order, checks all of
 * its conditions against {@code input}, and dispatches each action on a virtual thread
 * so that slow actions (HTTP, file I/O, audio) never block the serial read loop.
 * <p>
 * Variable substitution ({@code {input}}, {@code {timestamp}}) is applied to action
 * values immediately before execution via {@link #resolve}.
 */
public class EventHandler {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final DeviceConnectionManager deviceConnectionManager = BaudBound.getDeviceConnectionManager();

    /**
     * Named state map. Keys are state names; the special name {@value DEFAULT_STATE} is
     * used when no explicit name is provided. Written synchronously on the serial thread
     * so every state change is visible to the very next incoming line.
     * <p>
     * Format for action/condition values:
     * <ul>
     *   <li>{@code SET_STATE} — {@code "value"} (default state) or {@code "name|value"}</li>
     *   <li>{@code CLEAR_STATE} — blank (default state) or {@code "name"}</li>
     *   <li>{@code STATE_EQUALS} — {@code "value"} (default) or {@code "name|value"}</li>
     *   <li>{@code STATE_IS_EMPTY} — blank (default) or {@code "name"}</li>
     * </ul>
     */
    private static final String DEFAULT_STATE = "default";
    private final Map<String, String> states = new ConcurrentHashMap<>();

    /** Cache of compiled regex patterns keyed by pattern string to avoid recompilation on every condition check. */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /** Cache of the condition-first sorted event list. Invalidated via {@link #invalidateSortCache()}. */
    private volatile List<DataStore.Event> sortedEventsCache = null;

    /**
     * Returns an unmodifiable snapshot of the current state map for display purposes.
     * Keys are state names; values are the current state values.
     */
    public Map<String, String> getStates() {
        return Map.copyOf(states);
    }

    /**
     * Removes the named state entry. If {@code name} is blank, clears the default state.
     * Safe to call from the GLFW thread since {@link ConcurrentHashMap} handles concurrent access.
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
     * Invalidates the cached condition-first sorted event list.
     * Must be called whenever the event list is modified (add, edit, remove, reorder).
     */
    public void invalidateSortCache() {
        sortedEventsCache = null;
    }

    /**
     * Evaluates all configured events against {@code input} and fires matching actions.
     * Respects the {@code runFirstOnly}, {@code conditionEventsFirst}, and
     * {@code skipEmptyConditions} settings from {@link DataStore.Settings.Event}.
     *
     * @param input  the trimmed line read from the serial port
     * @param device the device that produced this input,
     *               used to evaluate {@link ConditionType#DEVICE_EQUALS} conditions
     */
    public void process(String input, DataStore.Device device) {

        DataStore data = storage.getData();
        DataStore.Settings.Event eventSettings = data.getSettings().getEvent();
        boolean runFirstOnly = eventSettings.isRunFirstOnly();

        List<DataStore.Event> events = data.getEvents();
        if (eventSettings.isConditionEventsFirst()) {
            if (sortedEventsCache == null) {
                sortedEventsCache = events.stream()
                        .sorted((a, b) -> {
                            boolean aHas = a.getConditions() != null && !a.getConditions().isEmpty();
                            boolean bHas = b.getConditions() != null && !b.getConditions().isEmpty();
                            return Boolean.compare(bHas, aHas); // events with conditions first
                        })
                        .toList();
            }
            events = sortedEventsCache;
        }

        boolean skipEmpty = eventSettings.isSkipEmptyConditions();
        List<String> firedNames = new java.util.ArrayList<>();

        for (DataStore.Event event : events) {
            boolean hasConditions = event.getConditions() != null && !event.getConditions().isEmpty();
            if (skipEmpty && !hasConditions) continue;

            if (matchesConditions(event, input, device)) {
                fireAction(event, input);
                firedNames.add(event.getName());
                if (runFirstOnly) break;
            }
        }

        String deviceTag = device != null ? " (device: " + device.getName() + ")" : "";
        if (firedNames.isEmpty()) {
            if (!events.isEmpty()) {
                logger.warn("No events matched input: \"" + input + "\"" + deviceTag);
            }
        } else {
            logger.info("Input: \"" + input + "\"" + deviceTag +
                    " — fired " + firedNames.size() + " event(s): " + String.join(", ", firedNames));
        }
    }

    /**
     * Returns {@code true} if every condition in the event is satisfied by {@code input}.
     * An event with no conditions always matches.
     */
    private boolean matchesConditions(DataStore.Event event, String input, DataStore.Device device) {
        List<DataStore.Event.Condition> conditions = event.getConditions();
        if (conditions == null || conditions.isEmpty()) return true;

        for (DataStore.Event.Condition condition : conditions) {
            ConditionType type = ConditionType.getByName(condition.getType());
            String value = condition.getValue();
            if (type == null) continue;
            if (type != ConditionType.IS_NUMERIC && value == null) continue;

            boolean caseSensitive = condition.isCaseSensitive();
            String  normalizedInput = caseSensitive ? input : input.toLowerCase();
            String  normalizedValue = caseSensitive ? value : value.toLowerCase();

            boolean matches = switch (type) {
                case STARTS_WITH     -> normalizedInput.startsWith(normalizedValue);
                case ENDS_WITH       -> normalizedInput.endsWith(normalizedValue);
                case CONTAINS        -> normalizedInput.contains(normalizedValue);
                case NOT_CONTAINS    -> !normalizedInput.contains(normalizedValue);
                case NOT_STARTS_WITH -> !normalizedInput.startsWith(normalizedValue);
                case EQUALS          -> normalizedInput.equals(normalizedValue);
                case REGEX           -> patternCache.computeIfAbsent(value, Pattern::compile).matcher(input).matches();
                case IS_NUMERIC      -> isNumeric(input);
                case GREATER_THAN    -> compareNumeric(input, value) > 0;
                case LESS_THAN       -> compareNumeric(input, value) < 0;
                case BETWEEN         -> isBetween(input, value);
                case LENGTH_EQUALS   -> parseLengthEquals(input, value);
                case STATE_EQUALS, STATE_NOT_EQUALS -> {
                    String[] p = value.split("\\|", 2);
                    boolean eq = p.length == 2
                            ? p[1].equals(states.get(p[0].trim()))
                            : value.equals(states.get(DEFAULT_STATE));
                    yield type == ConditionType.STATE_EQUALS ? eq : !eq;
                }
                case STATE_IS_EMPTY -> {
                    String name = value.isBlank() ? DEFAULT_STATE : value.trim();
                    String v = states.get(name);
                    yield v == null || v.isBlank();
                }
                case DEVICE_EQUALS, DEVICE_NOT_EQUALS -> {
                    if (device == null || device.getName() == null) yield false;
                    boolean matched = false;
                    for (String part : value.split(",")) {
                        if (part.trim().equalsIgnoreCase(device.getName())) { matched = true; break; }
                    }
                    yield type == ConditionType.DEVICE_EQUALS ? matched : !matched;
                }
            };

            if (!matches) return false;
        }
        return true;
    }

    /**
     * Dispatches each action of the event on a separate virtual thread.
     * Errors in individual actions are caught and logged without stopping other actions.
     */
    private void fireAction(DataStore.Event event, String input) {
        if (event.getActions() == null || event.getActions().isEmpty()) return;

        for (DataStore.Event.Action action : event.getActions()) {
            ActionType type = ActionType.getByName(action.getType());
            if (type == null) continue;
            String value = action.getValue();

            // State actions are executed synchronously so the new state is visible
            // to the very next serial line processed on this thread.
            if (type == ActionType.SET_STATE) {
                String[] p = value != null ? value.split("\\|", 2) : new String[]{""};
                if (p.length == 2) {
                    states.put(p[0].trim(), p[1]);
                    logger.info("State \"" + p[0].trim() + "\" set to: \"" + p[1] + "\"");
                } else {
                    states.put(DEFAULT_STATE, value != null ? value : "");
                    logger.info("State \"" + DEFAULT_STATE + "\" set to: \"" + value + "\"");
                }
                continue;
            }
            if (type == ActionType.CLEAR_STATE) {
                String name = (value != null && !value.isBlank()) ? value.trim() : DEFAULT_STATE;
                states.remove(name);
                logger.info("State \"" + name + "\" cleared");
                continue;
            }

            Thread.ofVirtual().start(() -> {
                try {
                    switch (type) {
                        case CALL_WEBHOOK      -> callWebhook(value, input);
                        case OPEN_URL          -> openUrl(value, input);
                        case OPEN_PROGRAM      -> openProgram(value, input);
                        case TYPE_TEXT         -> typeText(value, input);
                        case COPY_TO_CLIPBOARD -> copyToClipboard(value, input);
                        case SHOW_NOTIFICATION -> showNotification(value, input);
                        case WRITE_TO_FILE     -> writeToFile(value, input);
                        case APPEND_TO_FILE    -> appendToFile(value, input);
                        case PLAY_SOUND        -> playSound(value);
                        case SEND_TO_DEVICE    -> sendToDevice(value, input);
                        default                -> logger.error("Unhandled action type: " + type);
                    }
                } catch (Exception e) {
                    logger.error("Action [" + type + "] failed for event \"" + event.getName() + "\": " + e.getMessage());
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void callWebhook(String webhookName, String input) {
        if (webhookName == null) return;

        storage.getData().getActions().getWebhooks().stream()
                .filter(w -> w.getName().equals(webhookName))
                .findFirst()
                .ifPresentOrElse(webhook -> {
                    DataStore.Actions.Webhook resolved = resolveWebhook(webhook, input);
                    HttpHandler.WebhookResult result = HttpHandler.fireWebhook(resolved);
                    if (result.success()) {
                        logger.info("Webhook \"" + webhookName + "\" responded " + result.statusCode());
                    } else {
                        logger.error("Webhook \"" + webhookName + "\" failed: " +
                                (result.error() != null ? result.error() : result.statusCode()));
                    }
                }, () -> logger.error("Webhook not found: " + webhookName));
    }

    private void openUrl(String url, String input) throws IOException {
        url = resolve(url, input);
        if (url == null || url.isBlank()) return;
        FoxLib.openURL(url);
        logger.info("Opened URL: " + url);
    }

    private void openProgram(String programName, String input) throws IOException {
        if (programName == null) return;

        DataStore.Actions.Program program = storage.getData().getActions().getPrograms().stream()
                .filter(p -> p.getName().equals(programName))
                .findFirst().orElse(null);

        if (program == null) {
            logger.error("Program not found: " + programName);
            return;
        }

        String path = resolve(program.getPath(), input);
        String args = resolve(program.getArguments(), input);

        ProcessBuilder pb;
        if (program.isRunAsAdmin()) {
            pb = (args != null && !args.isBlank())
                    ? new ProcessBuilder("powershell", "-Command", "Start-Process", "\"" + path + "\"", "-ArgumentList", "\"" + args + "\"", "-Verb", "RunAs")
                    : new ProcessBuilder("powershell", "-Command", "Start-Process", "\"" + path + "\"", "-Verb", "RunAs");
        } else {
            pb = (args != null && !args.isBlank())
                    ? new ProcessBuilder(path, args)
                    : new ProcessBuilder(path);
        }
        pb.start();
        logger.info("Launched program: " + programName);
    }

    private void typeText(String text, String input) throws AWTException {
        text = resolve(text, input);
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

    private void copyToClipboard(String text, String input) {
        text = resolve(text, input);
        if (text == null || text.isBlank()) return;
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        logger.info("Copied to clipboard: " + text);
    }

    /**
     * Value format: "message" or "TYPE|message"
     * TYPE is one of: INFO (default), WARNING, ERROR, NONE
     * Example: WARNING|Sensor value out of range: {input}
     */
    private void showNotification(String value, String input) {
        if (value == null || value.isBlank()) return;
        String[] parts = value.split("\\|", 2);
        TrayIcon.MessageType type = TrayIcon.MessageType.INFO;
        String message;
        if (parts.length == 2) {
            try { type = TrayIcon.MessageType.valueOf(parts[0].trim().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
            message = resolve(parts[1], input);
        } else {
            message = resolve(parts[0], input);
        }
        if (message == null || message.isBlank()) return;
        BaudBound.showNotification(BaudBound.APP_NAME, message, type);
        logger.info("Showed notification [" + type + "]: " + message);
    }

    /**
     * Value format: "path" or "path|content template"
     * If no content template is provided, defaults to "{timestamp}: {input}".
     * Both path and content support {input} and {timestamp} substitution.
     * Overwrites the file on each call.
     * Example: C:\logs\data.txt|{timestamp}: {input}
     */
    private void writeToFile(String value, String input) throws IOException {
        if (value == null || value.isBlank()) return;
        String[] parts = value.split("\\|", 2);
        String filePath = resolve(parts[0].trim(), input);
        String contentTemplate = parts.length == 2 ? parts[1] : "{timestamp}: {input}";
        String content = resolve(contentTemplate, input) + System.lineSeparator();
        Files.writeString(Path.of(filePath), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Wrote to file: " + filePath);
    }

    /**
     * Same format as writeToFile but appends instead of overwriting.
     * Example: C:\logs\data.txt|{timestamp}: {input}
     */
    private void appendToFile(String value, String input) throws IOException {
        if (value == null || value.isBlank()) return;
        String[] parts = value.split("\\|", 2);
        String filePath = resolve(parts[0].trim(), input);
        String contentTemplate = parts.length == 2 ? parts[1] : "{timestamp}: {input}";
        String content = resolve(contentTemplate, input) + System.lineSeparator();
        Files.writeString(Path.of(filePath), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        logger.info("Appended to file: " + filePath);
    }

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
        clip.addLineListener(e -> { if (e.getType() == LineEvent.Type.STOP) clip.close(); });
        clip.start();
        logger.info("Playing sound: " + filePath);
    }

    /**
     * Value format: {@code "deviceName|data"}.
     * Resolves {@code {input}} / {@code {timestamp}} in the data portion before sending.
     */
    private void sendToDevice(String value, String input) {
        if (value == null || value.isBlank()) return;
        String[] parts = value.split("\\|", 2);
        if (parts.length < 2) {
            logger.error("Send to Device: value must be 'deviceName|data'.");
            return;
        }
        String deviceName = parts[0].trim();
        String data = resolve(parts[1], input);
        boolean sent = deviceConnectionManager.sendToDevice(deviceName, data);
        if (sent) {
            logger.info("Sent to device \"" + deviceName + "\": " + data);
        } else {
            logger.error("Send to Device: device \"" + deviceName + "\" is not connected or not found.");
        }
    }

    // -------------------------------------------------------------------------
    // Condition helpers
    // -------------------------------------------------------------------------

    private boolean isNumeric(String input) {
        try { Double.parseDouble(input.trim()); return true; }
        catch (NumberFormatException e) { return false; }
    }

    /** Returns Double.compare(input, value), or 0 if either is non-numeric. */
    private int compareNumeric(String input, String value) {
        try {
            return Double.compare(Double.parseDouble(input.trim()), Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) { return 0; }
    }

    /** value format: "min,max" (inclusive on both ends) */
    private boolean isBetween(String input, String value) {
        String[] parts = value.split(",", 2);
        if (parts.length != 2) return false;
        try {
            double val = Double.parseDouble(input.trim());
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            return val >= min && val <= max;
        } catch (NumberFormatException e) { return false; }
    }

    private boolean parseLengthEquals(String input, String value) {
        try { return input.length() == Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return false; }
    }

    // -------------------------------------------------------------------------
    // Variable substitution
    // -------------------------------------------------------------------------

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Substitutes {@code {input}} and {@code {timestamp}} placeholders in {@code template}.
     *
     * @param template the template string; may be {@code null}
     * @param input    the raw serial input line
     * @return the resolved string, or {@code null} if {@code template} is {@code null}
     */
    private String resolve(String template, String input) {
        if (template == null) return null;
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return template
                .replace("{input}", input)
                .replace("{timestamp}", timestamp);
    }

    private DataStore.Actions.Webhook resolveWebhook(DataStore.Actions.Webhook original, String input) {
        String resolvedInput = original.isUrlEscape()
                ? URLEncoder.encode(input, StandardCharsets.UTF_8)
                : input;

        List<DataStore.Actions.Webhook.Header> resolvedHeaders = original.getHeaders() == null ? List.of() :
                original.getHeaders().stream()
                        .map(h -> new DataStore.Actions.Webhook.Header(h.getKey(), resolve(h.getValue(), resolvedInput)))
                        .toList();

        return new DataStore.Actions.Webhook(
                original.getName(),
                resolve(original.getUrl(), resolvedInput),
                original.getMethod(),
                resolvedHeaders,
                resolve(original.getBody(), resolvedInput),
                original.isUrlEscape()
        );
    }

}
