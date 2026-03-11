package fi.natroutter.baudbound.gui.dialog.actions;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.components.DialogMode;
import fi.natroutter.baudbound.gui.helpers.DialogHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;


public class WebhooksDialog {

    /*
    * Actions
    *  - Webhooks
    *  - Open Program with args
    *  - Open Website
    *
    */

    private StorageProvider storage = BaudBound.getStorageProvider();

    private final ImInt selectedWebhook = new ImInt(0);
    private List<DataStore.Actions.Webhook> webhooks = storage.getData().getActions().getWebhooks();

    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);

    public void show() {
        this.open = true;
    }

    public void render() {
        if (open) {
            ImGui.openPopup("Webhooks");
            modalOpen.set(true);
            open = false;
        }

        ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySizeX() / 2,
                ImGui.getIO().getDisplaySizeY() / 2,
                ImGuiCond.Always,
                0.5f, 0.5f
        );

        ImGui.setNextWindowSizeConstraints(
                ImGui.getIO().getDisplaySizeX() * 0.9f,
                ImGui.getIO().getDisplaySizeY() * 0.5f,
                ImGui.getIO().getDisplaySizeX() * 0.9f,
                ImGui.getIO().getDisplaySizeY() * 0.5f
        );

        if (ImGui.beginPopupModal("Webhooks", modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            DialogHelper.listAndEditorButtons(
                    "##webhooks", webhooks, selectedWebhook, true,
                    //Create Callback
                    ()-> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getWebhookEditorDialog().show(DialogMode.CREATE, null);
                    },
                    //Edit Callback
                    ()-> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getWebhookEditorDialog().show(DialogMode.EDIT, webhooks.get(selectedWebhook.get()));
                    },
                    //Delete Callback
                    ()-> {
                        webhooks.remove(selectedWebhook.get());
                        if (selectedWebhook.get() >= webhooks.size()) {
                            selectedWebhook.set(Math.max(0, webhooks.size() - 1));
                        }
                        storage.save();
                    },
                    //Error Callback
                    this::show
            );

            ImGui.endPopup();
        }
    }
}