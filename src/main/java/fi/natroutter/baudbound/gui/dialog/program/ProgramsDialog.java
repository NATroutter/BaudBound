package fi.natroutter.baudbound.gui.dialog.program;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;


public class ProgramsDialog {

    /*
    * Actions
    *  - Webhooks
    *  - Open Program with args
    *  - Open Website
    *
    */

    private StorageProvider storage = BaudBound.getStorageProvider();

    private final ImInt selected = new ImInt(0);
    private List<DataStore.Actions.Program> items = storage.getData().getActions().getPrograms();

    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);

    public void show() {
        this.open = true;
    }

    public void render() {
        if (open) {
            ImGui.openPopup("Programs");
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

        if (ImGui.beginPopupModal("Programs", modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            DialogHelper.listAndEditorButtons(
                    "##Programs", items, selected, true,
                    //Create Callback
                    ()-> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getProgramEditorDialog().show(DialogMode.CREATE, null);
                    },
                    //Edit Callback
                    ()-> {
                        ImGui.closeCurrentPopup();
                        BaudBound.getProgramEditorDialog().show(DialogMode.EDIT, items.get(selected.get()));
                    },
                    //Duplicate Callback
                    ()-> {
                        DataStore.Actions.Program orig = items.get(selected.get());
                        items.add(new DataStore.Actions.Program(orig.getName() + " (copy)", orig.getPath(), orig.getArguments(), orig.isRunAsAdmin()));
                        storage.save();
                    },
                    //Delete Callback
                    ()-> {
                        items.remove(selected.get());
                        if (selected.get() >= items.size()) {
                            selected.set(Math.max(0, items.size() - 1));
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