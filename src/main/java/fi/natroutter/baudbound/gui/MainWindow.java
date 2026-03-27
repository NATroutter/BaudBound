package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.gui.util.GuiHelper;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.Collections;
import java.util.List;

/**
 * The primary full-screen window that fills the entire GLFW surface.
 * <p>
 * Renders the event list with Create / Edit / Duplicate / Delete / reorder controls.
 * The menu bar is delegated to {@link MenuBar}. Device management is in the Devices dialog.
 */
public class MainWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImInt selectedEvent = new ImInt(0);

    private final List<DataStore.Event> events = storage.getData().getEvents();

    private final MenuBar menuBar = new MenuBar();

    /** Renders the full-screen window. Must be called from the GLFW main thread each frame. */
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

            GuiHelper.listAndEditorButtons(
                    "##events", events, selectedEvent, true, 0f,
                    ()-> BaudBound.getEventEditorDialog().show(),
                    ()-> BaudBound.getEventEditorDialog().show(DialogMode.EDIT, events.get(selectedEvent.get())),
                    ()-> {
                        DataStore.Event copy = events.get(selectedEvent.get()).deepCopy();
                        copy.setName(copy.getName() + " (copy)");
                        events.add(copy);
                        storage.save();
                    },
                    ()-> {
                        events.remove(selectedEvent.get());
                        if (selectedEvent.get() >= events.size()) {
                            selectedEvent.set(Math.max(0, events.size() - 1));
                        }
                        storage.save();
                    },
                    ()-> {
                        int i = selectedEvent.get();
                        Collections.swap(events, i, i - 1);
                        selectedEvent.set(i - 1);
                        storage.save();
                    },
                    ()-> {
                        int i = selectedEvent.get();
                        Collections.swap(events, i, i + 1);
                        selectedEvent.set(i + 1);
                        storage.save();
                    },
                    ()->{}
            );
        }
        ImGui.end();
        ImGui.popStyleVar(1);
    }

}