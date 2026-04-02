package fi.natroutter.baudbound.gui.windows;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.BaseWindow;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;

import java.util.List;

/**
 * Floating panel window for managing saved webhook definitions.
 * <p>
 * Displays all webhooks in a scrollable list with Create / Edit / Duplicate / Delete buttons.
 * Create and Edit open {@link fi.natroutter.baudbound.gui.dialog.webhook.WebhookEditorDialog};
 * {@code WebhookEditorDialog} reopens this window via its {@code onClose()} override.
 */
public class WebhooksWindow extends BaseWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final ImInt selected = new ImInt(0);
    private final List<DataStore.Actions.Webhook> items = storage.getData().getActions().getWebhooks();

    @Override
    public void render() {
        if (!open.get()) return;

        ImGui.setNextWindowSize(520, 380, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(300, 200, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("Webhooks##webhookswindow", open)) {

            GuiHelper.listAndEditorButtons(
                    "##webhooks", items, selected, true,
                    () -> BaudBound.getWebhookEditorDialog().show(DialogMode.CREATE, null),
                    () -> BaudBound.getWebhookEditorDialog().show(DialogMode.EDIT, items.get(selected.get())),
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
        }
        ImGui.end();
    }
}
