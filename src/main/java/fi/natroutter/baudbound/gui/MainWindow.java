package fi.natroutter.baudbound.gui;

import fi.natroutter.foxlib.logger.FoxLogger;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.components.DialogMode;
import fi.natroutter.baudbound.gui.helpers.DialogHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.List;

public class MainWindow {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();

    private final ImInt selectedEvent = new ImInt(0);

    private List<DataStore.Event> events = storage.getData().getEvents();

    private MenuBar menuBar = new MenuBar();

    public void render() {
        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());

        int windowFlags = ImGuiWindowFlags.NoDocking |
                        ImGuiWindowFlags.NoTitleBar |
                        ImGuiWindowFlags.NoCollapse |
                        ImGuiWindowFlags.NoResize |
                        ImGuiWindowFlags.NoMove |
                        ImGuiWindowFlags.NoBringToFrontOnFocus |
                        ImGuiWindowFlags.MenuBar |
                        ImGuiWindowFlags.NoNavFocus;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);



        if (ImGui.begin("BaudBound", windowFlags)) {
            menuBar.render();

            DialogHelper.listAndEditorButtons(
                    "##events", events, selectedEvent, true,
                    ()-> {
                        BaudBound.getEventEditorDialog().show();
                    },
                    ()-> {
                        BaudBound.getEventEditorDialog().show(DialogMode.EDIT, events.get(selectedEvent.get()));
                    },
                    ()-> {
                        events.remove(selectedEvent.get());
                        if (selectedEvent.get() >= events.size()) {
                            selectedEvent.set(Math.max(0, events.size() - 1));
                        }
                        storage.save();
                    },
                    ()->{}
            );

        }
        ImGui.end();
        ImGui.popStyleVar(1);
    }



}
