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
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public class EventHandler {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();

    public void process(String input) {

        DataStore data = storage.getData();
        List<DataStore.Event> events = data.getEvents();
        DataStore.Settings.Event eventSettings = data.getSettings().getEvent();
        boolean runFirstOnly = eventSettings.isRunFirstOnly();

        String defaultEventName = eventSettings.isUseDefaultEvent() ? eventSettings.getDefaultEvent() : null;

        boolean anyMatched = false;
        for (DataStore.Event event : events) {
            // Skip the default event — it is handled separately after the loop
            if (defaultEventName != null && defaultEventName.equals(event.getName())) continue;

            if (matchesConditions(event, input)) {
                fireAction(event, input);
                anyMatched = true;
                if (runFirstOnly) break;
            }
        }

        // Run the default event only if nothing else matched
        if (!anyMatched && defaultEventName != null) {
            events.stream()
                    .filter(e -> defaultEventName.equals(e.getName()))
                    .findFirst()
                    .ifPresent(e -> fireAction(e, input));
        }
    }

    private boolean matchesConditions(DataStore.Event event, String input) {
        List<DataStore.Event.Condition> conditions = event.getConditions();
        if (conditions == null || conditions.isEmpty()) return true;

        for (DataStore.Event.Condition condition : conditions) {
            ConditionType type = ConditionType.getByName(condition.getType());
            String value = condition.getValue();
            if (type == null || value == null) continue;

            boolean matches = switch (type) {
                case STARTS_WITH -> input.startsWith(value);
                case ENDS_WITH   -> input.endsWith(value);
                case CONTAINS    -> input.contains(value);
                case REGEX       -> Pattern.matches(value, input);
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
                        case CALL_WEBHOOK -> callWebhook(value, input);
                        case OPEN_URL     -> openUrl(value, input);
                        case OPEN_PROGRAM -> openProgram(value, input);
                        case TYPE_TEXT    -> typeText(value, input);
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

        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Robot robot = new Robot();
        robot.delay(50);
        robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
        robot.keyPress(java.awt.event.KeyEvent.VK_V);
        robot.keyRelease(java.awt.event.KeyEvent.VK_V);
        robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);

        logger.info("Typed text: " + text);
    }

    // -------------------------------------------------------------------------
    // Variable substitution
    // -------------------------------------------------------------------------

    private String resolve(String template, String input) {
        if (template == null) return null;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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
