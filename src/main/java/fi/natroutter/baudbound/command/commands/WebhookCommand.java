package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;
import fi.natroutter.baudbound.http.HttpHandler;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.foxlib.FoxLib;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Console command for listing and manually firing configured webhooks.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code webhook} — list all configured webhooks</li>
 *   <li>{@code webhook fire <name> [input]} — fire the named webhook, substituting {@code {input}} with the given value</li>
 * </ul>
 * Webhook names that contain spaces must be wrapped in double quotes,
 * e.g. {@code webhook fire "My Webhook" test-value}.
 */
public class WebhookCommand extends Command {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public WebhookCommand() {
        super("webhook", "List or fire configured webhooks  (usage: webhook [fire <name> [input]])");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            showAll();
            return;
        }
        if (args[0].equalsIgnoreCase("fire")) {
            if (args.length < 2) {
                FoxLib.println("  {BRIGHT_RED}Usage: webhook fire <name> [input]{RESET}");
                return;
            }
            String name  = args[1];
            String input = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
            handleFire(name, input);
        } else {
            FoxLib.println("  {BRIGHT_RED}Unknown subcommand: \"" + args[0] + "\"{RESET}  —  usage: {BRIGHT_YELLOW}webhook [fire <name> [input]]{RESET}");
        }
    }

    private void showAll() {
        List<DataStore.Actions.Webhook> webhooks = BaudBound.getStorageProvider().getData().getActions().getWebhooks();
        if (webhooks.isEmpty()) {
            FoxLib.println("  {YELLOW}No webhooks configured.{RESET}");
            return;
        }
        int nameWidth = webhooks.stream().mapToInt(w -> w.getName().length()).max().orElse(0);
        List<String> rows = webhooks.stream().map(w -> {
            String name   = w.getName() + " ".repeat(nameWidth - w.getName().length());
            String method = w.getMethod() != null ? w.getMethod() : "GET";
            return "{BLUE}" + name + "{RESET}  {CYAN}" + method + "{RESET}  {WHITE}" + w.getUrl();
        }).toList();
        ConsoleUI.printBox("Webhooks  (" + webhooks.size() + ")", rows);
    }

    private void handleFire(String name, String input) {
        DataStore.Actions.Webhook webhook = BaudBound.getStorageProvider().getData().getActions().getWebhooks().stream()
                .filter(w -> w.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (webhook == null) {
            FoxLib.println("  {BRIGHT_RED}Unknown webhook: \"" + name + "\"{RESET}");
            return;
        }

        FoxLib.println("  {CYAN}Firing \"" + name + "\" with input: \"" + input + "\"...{RESET}");
        final DataStore.Actions.Webhook resolved = resolveWebhook(webhook, input);
        Thread.ofVirtual().start(() -> {
            HttpHandler.WebhookResult result = HttpHandler.fireWebhook(resolved);
            if (result.success()) {
                FoxLib.println("  {BRIGHT_GREEN}\"" + name + "\" responded " + result.statusCode() + ".{RESET}");
            } else {
                String reason = result.error() != null ? result.error() : "HTTP " + result.statusCode();
                FoxLib.println("  {BRIGHT_RED}\"" + name + "\" failed: " + reason + "{RESET}");
            }
        });
    }

    /**
     * Returns a copy of {@code original} with {@code {input}} and {@code {timestamp}}
     * substituted in the URL, headers, and body.
     */
    private static DataStore.Actions.Webhook resolveWebhook(DataStore.Actions.Webhook original, String input) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        List<DataStore.Actions.Webhook.Header> headers = original.getHeaders() == null ? List.of() :
                original.getHeaders().stream()
                        .map(h -> new DataStore.Actions.Webhook.Header(h.getKey(), resolve(h.getValue(), input, timestamp)))
                        .toList();
        return new DataStore.Actions.Webhook(
                original.getName(),
                resolve(original.getUrl(), input, timestamp),
                original.getMethod(),
                headers,
                resolve(original.getBody(), input, timestamp),
                original.isUrlEscape()
        );
    }

    private static String resolve(String template, String input, String timestamp) {
        if (template == null) return null;
        return template.replace("{input}", input).replace("{timestamp}", timestamp);
    }
}