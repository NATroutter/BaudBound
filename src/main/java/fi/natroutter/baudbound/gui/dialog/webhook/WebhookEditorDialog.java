package fi.natroutter.baudbound.gui.dialog.webhook;

import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.BaseDialog;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.enums.HttpMethod;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.http.HttpHandler;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal editor for creating and modifying {@link DataStore.Actions.Webhook} entries.
 * <p>
 * Supports both {@link DialogMode#CREATE} and {@link DialogMode#EDIT} modes. In EDIT mode
 * the existing webhook's data is pre-loaded into the ImGui state fields.
 * <p>
 * The "Test Webhook" button fires the request on a virtual thread so the UI stays
 * responsive; the {@code testing} flag disables the button while the request is in flight.
 * When dismissed via the X button, {@link #onClose()} reopens {@link WebhooksDialog}.
 */
public class WebhookEditorDialog extends BaseDialog {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImString fieldName   = new ImString();
    private final ImString fieldUrl    = new ImString(1024);
    private final ImInt    fieldMethod = new ImInt();
    private final List<ImString[]> fieldHeaders = new ArrayList<>();
    private final ImString fieldBody   = new ImString(4096);

    private volatile boolean testing = false;

    private DialogMode mode = DialogMode.CREATE;
    private DataStore.Actions.Webhook editing = null;

    @Override
    public void show() {
        show(DialogMode.CREATE, null);
    }

    public void show(DialogMode dialogMode, DataStore.Actions.Webhook webhook) {
        this.mode = dialogMode;

        if (dialogMode == DialogMode.EDIT && webhook != null) {
            this.editing = webhook;
            fieldName.set(webhook.getName());
            fieldUrl.set(webhook.getUrl());
            fieldMethod.set(HttpMethod.findIndex(webhook.getMethod()));

            fieldHeaders.clear();
            if (webhook.getHeaders() != null) {
                for (DataStore.Actions.Webhook.Header header : webhook.getHeaders()) {
                    ImString key = new ImString(128);
                    ImString value = new ImString(256);
                    key.set(header.getKey());
                    value.set(header.getValue());
                    fieldHeaders.add(new ImString[]{key, value});
                }
            }
            fieldBody.set(webhook.getBody() != null ? webhook.getBody() : "");
        } else {
            this.editing = null;
            fieldName.set("");
            fieldUrl.set("");
            fieldMethod.set(0);
            fieldHeaders.clear();
            fieldBody.set("");
        }

        requestOpen();
    }

    @Override
    protected void onClose() {
        BaudBound.getWebhooksDialog().show();
    }

    @Override
    public void render() {
        String title = mode.getType() + " Webhook";
        if (beginModal(title)) {

            if (ImGui.beginChild("##instructions_wrap", ImGui.getContentRegionAvailX(), 0, ImGuiChildFlags.AutoResizeY)) {
                if (ImGui.collapsingHeader("Instructions")) {
                    ImGui.indent(8);
                    ImGui.spacing();
                    GuiHelper.instructions("fields (URL, Headers(Value), Body)");
                    ImGui.spacing();
                    ImGui.unindent(8);
                    ImGui.spacing();
                }
            }
            ImGui.endChild();
            ImGui.spacing();

            ImGui.text("Name");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##name", fieldName);

            ImGui.text("URL");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##url", fieldUrl);

            ImGui.text("Method");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##method", fieldMethod, HttpMethod.asArray());

            ImGui.text("Headers");
            GuiHelper.keyValueTable("##headers", "Key", "Value", fieldHeaders);

            ImGui.spacing();
            if (ImGui.button("Add Header", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldHeaders.add(new ImString[]{new ImString(128), new ImString(256)});
            }

            ImGui.text("Body");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputTextMultiline("##body", fieldBody,
                    ImGui.getContentRegionAvailX(), ImGui.getTextLineHeight() * 16,
                    ImGuiInputTextFlags.AllowTabInput);

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2;
            ImGui.beginDisabled(testing);
            if (ImGui.button(testing ? "Testing..." : "Test Webhook", new ImVec2(buttonWidth, GuiTheme.BUTTON_HEIGHT))) {
                testWebhook();
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                save();
            }

            endModal();
        }
    }

    /**
     * Converts the current header row state into a list of {@link DataStore.Actions.Webhook.Header}.
     * Rows with a blank key are silently skipped (HTTP spec requires a valid field name).
     */
    private List<DataStore.Actions.Webhook.Header> buildHeaders() {
        List<DataStore.Actions.Webhook.Header> headers = new ArrayList<>();
        for (ImString[] pair : fieldHeaders) {
            String key = pair[0].get().trim();
            String value = pair[1].get().trim();
            if (!key.isEmpty()) headers.add(new DataStore.Actions.Webhook.Header(key, value));
        }
        return headers;
    }

    /**
     * Fires a test request using the current field values on a virtual thread.
     * Shows a result dialog on completion. The {@code testing} flag prevents concurrent
     * test runs while one is already in flight.
     */
    private void testWebhook() {
        String url = fieldUrl.get().trim();
        if (url.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "URL is required to test the webhook.", new DialogButton("OK", this::requestOpen));
            return;
        }

        String method = HttpMethod.values()[fieldMethod.get()].name();
        String body = fieldBody.get().trim();
        DataStore.Actions.Webhook tempWebhook = new DataStore.Actions.Webhook(
                fieldName.get().trim(), url, method, buildHeaders(), body.isEmpty() ? null : body);

        testing = true;
        Thread.ofVirtual().start(() -> {
            HttpHandler.WebhookResult result = HttpHandler.fireWebhook(tempWebhook);
            testing = false;
            String title = result.success() ? "Test Successful" : "Test Failed";
            String content = (result.error() != null)
                    ? "Error: " + result.error()
                    : "Status: " + result.statusCode() + (result.body() != null && !result.body().isBlank() ? "\n\n" + result.body() : "");
            BaudBound.getMessageDialog().show(title, content, new DialogButton("OK", this::requestOpen));
        });
    }

    private void save() {
        logger.info("Saving webhook...");

        String name = fieldName.get().trim();
        String url = fieldUrl.get().trim();
        if (name.isEmpty() || url.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name and URL are required.", new DialogButton("OK", this::requestOpen));
            return;
        }

        List<DataStore.Actions.Webhook> webhooks = storage.getData().getActions().getWebhooks();
        if (webhooks.stream().anyMatch(w -> w != editing && w.getName().equalsIgnoreCase(name))) {
            BaudBound.getMessageDialog().show("Error", "A webhook named \"" + name + "\" already exists.", new DialogButton("OK", this::requestOpen));
            return;
        }

        String method = HttpMethod.values()[fieldMethod.get()].name();
        String body = fieldBody.get().trim();

        if (mode == DialogMode.EDIT && editing != null) {
            editing.setName(name);
            editing.setUrl(url);
            editing.setMethod(method);
            editing.setHeaders(buildHeaders());
            editing.setBody(body);
        } else {
            webhooks.add(new DataStore.Actions.Webhook(name, url, method, buildHeaders(), body));
        }

        storage.save();
        ImGui.closeCurrentPopup();
        BaudBound.getWebhooksDialog().show();
    }
}