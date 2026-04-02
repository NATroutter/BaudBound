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
 * Floating panel window for managing saved program definitions.
 * <p>
 * Displays all programs in a scrollable list with Create / Edit / Duplicate / Delete buttons.
 * Create and Edit open {@link fi.natroutter.baudbound.gui.dialog.program.ProgramEditorDialog};
 * {@code ProgramEditorDialog} reopens this window via its {@code onClose()} override.
 */
public class ProgramsWindow extends BaseWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final ImInt selected = new ImInt(0);
    private final List<DataStore.Actions.Program> items = storage.getData().getActions().getPrograms();

    @Override
    public void render() {
        if (!open.get()) return;

        ImGui.setNextWindowSize(520, 380, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(300, 200, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("Programs##programswindow", open)) {

            GuiHelper.listAndEditorButtons(
                    "##Programs", items, selected, true,
                    () -> BaudBound.getProgramEditorDialog().show(DialogMode.CREATE, null),
                    () -> BaudBound.getProgramEditorDialog().show(DialogMode.EDIT, items.get(selected.get())),
                    () -> {
                        DataStore.Actions.Program copy = items.get(selected.get()).deepCopy();
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
