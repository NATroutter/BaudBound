package fi.natroutter.baudbound.gui.windows;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.enums.NodeType;
import fi.natroutter.baudbound.gui.BaseWindow;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.Collections;
import java.util.List;

/**
 * Floating panel window that displays the configured event list.
 * <p>
 * Provides Create / Edit / Duplicate / Delete / reorder controls via
 * {@link GuiHelper#tableAndEditorButtons}. Previously embedded in the fullscreen
 * {@code MainWindow}; now an independent movable window.
 */
public class EventsWindow extends BaseWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final ImInt selectedEvent = new ImInt(0);

    @Override
    public void render() {
        if (!open.get()) return;

        ImGui.setNextWindowSize(620, 420, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(300, 200, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("Events##eventswindow", open, ImGuiWindowFlags.None)) {
            List<DataStore.Event> events = storage.getData().getEvents();

            String[] eventHeaders      = {"Name", "Triggers", "Conditions", "Actions"};
            float[]  eventColumnWidths = {0f, 150f, 80f, 60f};

            GuiHelper.tableAndEditorButtons(
                    "##events", events, selectedEvent, true, 0f,
                    eventHeaders, eventColumnWidths,
                    event -> {
                        int triggers   = (int) event.getNodes().stream().filter(n -> { NodeType nt = NodeType.getByName(n.getType()); return nt != null && nt.getCategory() == NodeType.Category.TRIGGER; }).count();
                        int conditions = (int) event.getNodes().stream().filter(n -> { NodeType nt = NodeType.getByName(n.getType()); return nt != null && nt.getCategory() == NodeType.Category.CONDITION; }).count();
                        int actions    = (int) event.getNodes().stream().filter(n -> { NodeType nt = NodeType.getByName(n.getType()); return nt != null && nt.getCategory() == NodeType.Category.ACTION; }).count();
                        return new String[]{ triggers + " triggers", String.valueOf(conditions), String.valueOf(actions) };
                    },
                    () -> BaudBound.getEventEditorDialog().show(),
                    () -> {
                        if (!events.isEmpty()) {
                            BaudBound.getEventEditorDialog().show(DialogMode.EDIT, events.get(selectedEvent.get()));
                        }
                    },
                    () -> {
                        if (!events.isEmpty()) {
                            DataStore.Event copy = events.get(selectedEvent.get()).deepCopy();
                            copy.setName(copy.getName() + " (copy)");
                            events.add(copy);
                            storage.save();
                            BaudBound.getEventHandler().invalidateSortCache();
                        }
                    },
                    () -> {
                        if (!events.isEmpty()) {
                            events.remove(selectedEvent.get());
                            if (selectedEvent.get() >= events.size()) {
                                selectedEvent.set(Math.max(0, events.size() - 1));
                            }
                            storage.save();
                            BaudBound.getEventHandler().invalidateSortCache();
                        }
                    },
                    () -> {
                        int i = selectedEvent.get();
                        if (i > 0) {
                            Collections.swap(events, i, i - 1);
                            selectedEvent.set(i - 1);
                            storage.save();
                            BaudBound.getEventHandler().invalidateSortCache();
                        }
                    },
                    () -> {
                        int i = selectedEvent.get();
                        if (i < events.size() - 1) {
                            Collections.swap(events, i, i + 1);
                            selectedEvent.set(i + 1);
                            storage.save();
                            BaudBound.getEventHandler().invalidateSortCache();
                        }
                    },
                    () -> {}
            );
        }
        ImGui.end();
    }
}
