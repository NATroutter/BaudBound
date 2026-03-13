package fi.natroutter.baudbound.gui.dialog.webhook;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.BaseDialog;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;


public class WebhooksDialog extends BaseDialog {

    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImInt selected = new ImInt(0);
    private final List<DataStore.Actions.Webhook> items = storage.getData().getActions().getWebhooks();

    @Override
    public void render() {
        float fixedH = ImGui.getIO().getDisplaySizeY() * 0.5f;
        if (beginModal("Webhooks", fixedH)) {

            GuiHelper.listAndEditorButtons(
                    "##webhooks", items, selected, true,
                    () -> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getWebhookEditorDialog().show(DialogMode.CREATE, null);
                    },
                    () -> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getWebhookEditorDialog().show(DialogMode.EDIT, items.get(selected.get()));
                    },
                    () -> {
                        DataStore.Actions.Webhook copy = items.get(selected.get()).deepCopy();
                        copy.setName(copy.getName() + " (copy)");
                        items.add(copy);
                        storage.save();
                    },
                    () -> {
                        items.remove(selected.get());
                        if (selected.get() >= items.size()) {
                            selected.set(Math.max(0, items.size() - 1));
                        }
                        storage.save();
                    },
                    this::show
            );

            endModal();
        }
    }
}