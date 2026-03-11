package fi.natroutter.baudbound.gui.dialog.actions;

import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.dialog.components.DialogMode;
import fi.natroutter.baudbound.gui.helpers.GuiHelper;
import fi.natroutter.baudbound.http.data.Method;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;
import imgui.type.ImBoolean;

import java.util.*;

public class WebhookEditorDialog {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();

    private final ImString fieldName = new ImString();
    private final ImString fieldUrl = new ImString();
    private final ImInt fieldMethod = new ImInt();
    private final List<ImString[]> fieldHeaders = new ArrayList<>();
    private final ImString fieldBody = new ImString();

    private DialogMode mode = DialogMode.CREATE;
    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);
    private DataStore.Actions.Webhook editing = null;

    public void show() {
        show(DialogMode.CREATE, null);
    }

    public void show(DialogMode dialogMode, DataStore.Actions.Webhook webhook) {
        this.open = true;
        this.mode = dialogMode;

        if (dialogMode == DialogMode.EDIT && webhook != null) {
            this.editing = webhook;
            fieldName.set(webhook.getName());
            fieldUrl.set(webhook.getUrl());

            // Find method index by name
            fieldMethod.set(Method.findIndex(webhook.getName()));

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
    }

    public void render() {
        if (open) {
            ImGui.openPopup(mode.getType() + " Webhook");
            modalOpen.set(true);
            open = false;
        }

        ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySizeX() / 2,
                ImGui.getIO().getDisplaySizeY() / 2,
                ImGuiCond.Always,
                0.5f, 0.5f
        );
        ImGui.setNextWindowSizeConstraints(ImGui.getIO().getDisplaySizeX() * 0.9f, 0, ImGui.getIO().getDisplaySizeX() * 0.9f, Float.MAX_VALUE);

        String popupTitle = mode.getType() + " Webhook";
        boolean wasOpen = ImGui.isPopupOpen(popupTitle);
        if (ImGui.beginPopupModal(popupTitle, modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            GuiHelper.instructions("fields (URL, Headers(Value), Body)");
            ImGui.separator();
            ImGui.spacing();

            ImGui.text("Name");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.beginDisabled(mode == DialogMode.EDIT);
            ImGui.inputText("##name", fieldName);
            ImGui.endDisabled();

            ImGui.text("URL");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##url", fieldUrl);

            ImGui.text("Method");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.combo("##method", fieldMethod, Method.asArray());

            ImGui.text("Headers");
            int removeIndex = -1;
            ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
            if (ImGui.beginTable("##headers", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {
                ImGui.tableSetupColumn("Key", imgui.flag.ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("Value", imgui.flag.ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("##remove", imgui.flag.ImGuiTableColumnFlags.WidthFixed, 22);
                ImGui.tableHeadersRow();

                for (int row = 0; row < fieldHeaders.size(); row++) {
                    ImString[] pair = fieldHeaders.get(row);
                    ImGui.tableNextRow();

                    ImGui.tableSetColumnIndex(0);
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputText("##hk" + row, pair[0]);

                    ImGui.tableSetColumnIndex(1);
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputText("##hv" + row, pair[1]);

                    ImGui.tableSetColumnIndex(2);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1.0f);
                    if (ImGui.button("X##hr" + row, 0, 0)) {
                        removeIndex = row;
                    }
                    ImGui.popStyleColor();
                }
                ImGui.endTable();
            }
            ImGui.popStyleVar();
            if (removeIndex >= 0) fieldHeaders.remove(removeIndex);
            ImGui.spacing();
            if (ImGui.button("Add Header", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldHeaders.add(new ImString[]{new ImString(128), new ImString(256)});
            }

            ImGui.text("Body");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputTextMultiline("##body", fieldBody,
                    ImGui.getContentRegionAvailX(), ImGui.getTextLineHeight() * 16,
                    ImGuiInputTextFlags.AllowTabInput
            );


            // Buttons
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), 20))) {
                save();
            }

            ImGui.endPopup();
        } else if (wasOpen && !modalOpen.get()) {
            modalOpen.set(true);
            BaudBound.getWebhooksDialog().show();
        }
    }

    private void save() {
        logger.info("Saving webhook...");

        String name = fieldName.get().trim();
        String url = fieldUrl.get().trim();
        if (name.isEmpty() || url.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name and URL are required.", new DialogButton("OK", this::show));
            return;
        }

        List<DataStore.Actions.Webhook> webhooks = storage.getData().getActions().getWebhooks();
        boolean nameExists = webhooks.stream().anyMatch(w -> w != editing && w.getName().equalsIgnoreCase(name));
        if (nameExists) {
            BaudBound.getMessageDialog().show(
                    "Error",
                    "A webhook with the name \"" + name + "\" already exists.",
                    new DialogButton("OK", this::show)
            );
            return;
        }

        List<DataStore.Actions.Webhook.Header> headers = new ArrayList<>();
        for (ImString[] pair : fieldHeaders) {
            String key = pair[0].get().trim();
            String value = pair[1].get().trim();
            if (!key.isEmpty()) headers.add(new DataStore.Actions.Webhook.Header(key, value));
        }

        String method = Method.values()[fieldMethod.get()].name();
        String body = fieldBody.get().trim();

        if (mode == DialogMode.EDIT && editing != null) {
            editing.setName(name);
            editing.setUrl(url);
            editing.setMethod(method);
            editing.setHeaders(headers);
            editing.setBody(body);
        } else {
            webhooks.add(new DataStore.Actions.Webhook(name, url, method, headers, body));
        }

        storage.save();
        ImGui.closeCurrentPopup();
        BaudBound.getWebhooksDialog().show();
    }
}