package fi.natroutter.baudbound.event;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ActionType;
import fi.natroutter.baudbound.enums.ConditionType;
import fi.natroutter.baudbound.http.HttpHandler;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.logger.FoxLogger;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

public class EventHandler {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();

    public void process(String input) {

        DataStore data = storage.getData();
        DataStore.Settings.Event eventSettings = data.getSettings().getEvent();
        boolean runFirstOnly = eventSettings.isRunFirstOnly();

        List<DataStore.Event> events = data.getEvents();
        if (eventSettings.isConditionEventsFirst()) {
            events = events.stream()
                    .sorted((a, b) -> {
                        boolean aHas = a.getConditions() != null && !a.getConditions().isEmpty();
                        boolean bHas = b.getConditions() != null && !b.getConditions().isEmpty();
                        return Boolean.compare(bHas, aHas); // events with conditions first
                    })
                    .toList();
        }

        boolean skipEmpty = eventSettings.isSkipEmptyConditions();

        for (DataStore.Event event : events) {
            boolean hasConditions = event.getConditions() != null && !event.getConditions().isEmpty();
            if (skipEmpty && !hasConditions) continue;

            if (matchesConditions(event, input)) {
                fireAction(event, input);
                if (runFirstOnly) break;
            }
        }
    }

    private boolean matchesConditions(DataStore.Event event, String input) {
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
                case REGEX           -> Pattern.matches(value, input); // regex handles its own case
                case IS_NUMERIC      -> isNumeric(input);
                case GREATER_THAN    -> compareNumeric(input, value) > 0;
                case LESS_THAN       -> compareNumeric(input, value) < 0;
                case BETWEEN         -> isBetween(input, value);
                case LENGTH_EQUALS   -> parseLengthEquals(input, value);
            };

            if (!matches) return false;
        }
        return true;
    }

    private void fireAction(DataStore.Event event, String input) {
        if (event.getActions() == null || event.getActions().isEmpty()) return;

        for (DataStore.Event.Action action : event.getActions()) {
            ActionType type = ActionType.getByName(action.getType());
            if (type == null) continue;
            String value = action.getValue();

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

    private String resolve(String template, String input) {
        if (template == null) return null;
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return template
                .replace("{input}", input)
                .replace("{timestamp}", timestamp);
    }

    private DataStore.Actions.Webhook resolveWebhook(DataStore.Actions.Webhook original, String input) {
        List<DataStore.Actions.Webhook.Header> resolvedHeaders = original.getHeaders() == null ? List.of() :
                original.getHeaders().stream()
                        .map(h -> new DataStore.Actions.Webhook.Header(h.getKey(), resolve(h.getValue(), input)))
                        .toList();

        return new DataStore.Actions.Webhook(
                original.getName(),
                resolve(original.getUrl(), input),
                original.getMethod(),
                resolvedHeaders,
                resolve(original.getBody(), input)
        );
    }

}
