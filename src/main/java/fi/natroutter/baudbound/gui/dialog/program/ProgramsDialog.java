package fi.natroutter.baudbound.gui.dialog.program;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.BaseDialog;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;


public class ProgramsDialog extends BaseDialog {

    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImInt selected = new ImInt(0);
    private final List<DataStore.Actions.Program> items = storage.getData().getActions().getPrograms();

    @Override
    public void render() {
        float fixedH = ImGui.getIO().getDisplaySizeY() * 0.5f;
        if (beginModal("Programs", fixedH)) {

            GuiHelper.listAndEditorButtons(
                    "##Programs", items, selected, true,
                    () -> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getProgramEditorDialog().show(DialogMode.CREATE, null);
                    },
                    () -> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getProgramEditorDialog().show(DialogMode.EDIT, items.get(selected.get()));
                    },
                    () -> {
                        DataStore.Actions.Program orig = items.get(selected.get());
                        items.add(new DataStore.Actions.Program(orig.getName() + " (copy)", orig.getPath(), orig.getArguments(), orig.isRunAsAdmin()));
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