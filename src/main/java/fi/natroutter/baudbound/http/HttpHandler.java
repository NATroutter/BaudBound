package fi.natroutter.baudbound.http;

import fi.natroutter.baudbound.storage.DataStore;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

public class HttpHandler {

    public record WebhookResult(boolean success, int statusCode, String body, String error) {}

    public static WebhookResult fireWebhook(DataStore.Actions.Webhook webhook) {
        try {
            Connection.Method method = Connection.Method.valueOf(
                    webhook.getMethod() != null ? webhook.getMethod().toUpperCase() : "GET"
            );

            Connection connection = Jsoup.connect(webhook.getUrl())
                    .method(method)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(10_000);

            if (webhook.getHeaders() != null) {
                for (DataStore.Actions.Webhook.Header header : webhook.getHeaders()) {
                    if (header.getKey() != null && !header.getKey().isBlank()) {
                        connection.header(header.getKey(), header.getValue() != null ? header.getValue() : "");
                    }
                }
            }

            if (webhook.getBody() != null && !webhook.getBody().isBlank()) {
                connection.requestBody(webhook.getBody());
            }

            Connection.Response response = connection.execute();
            int status = response.statusCode();
            return new WebhookResult(status >= 200 && status < 300, status, response.body(), null);

        } catch (Exception e) {
            return new WebhookResult(false, -1, null, e.getMessage());
        }
    }
}