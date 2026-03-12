package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.serial.SerialHandler;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.List;

public class MainWindow {

    private StorageProvider storage = BaudBound.getStorageProvider();
    private SerialHandler serialHandler = BaudBound.getSerialHandler();


    private final ImInt selectedEvent = new ImInt(0);


    private final List<DataStore.Event> events = storage.getData().getEvents();

    private final MenuBar menuBar = new MenuBar();

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

            float itemSpacing = ImGui.getStyle().getItemSpacingY();
            float lineHeight = ImGui.getTextLineHeightWithSpacing();
            // spacing + button(20) + spacing + text_line + spacing
            float bottomReserve = itemSpacing * 4 + 20 + lineHeight;

            DialogHelper.listAndEditorButtons(
                    "##events", events, selectedEvent, true, bottomReserve,
                    ()-> {
                        BaudBound.getEventEditorDialog().show();
                    },
                    ()-> {
                        BaudBound.getEventEditorDialog().show(DialogMode.EDIT, events.get(selectedEvent.get()));
                    },
                    ()-> {
                        DataStore.Event orig = events.get(selectedEvent.get());
                        List<DataStore.Event.Condition> condCopy = orig.getConditions() == null ? new java.util.ArrayList<>() :
                                orig.getConditions().stream().map(c -> new DataStore.Event.Condition(c.getType(), c.getValue())).toList();
                        List<DataStore.Event.Action> actCopy = orig.getActions() == null ? new java.util.ArrayList<>() :
                                orig.getActions().stream().map(a -> new DataStore.Event.Action(a.getType(), a.getValue())).toList();
                        events.add(new DataStore.Event(orig.getName() + " (copy)", condCopy, actCopy));
                        storage.save();
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

            ImGui.spacing();

            boolean connected = serialHandler.getStatus() == ConnectionStatus.CONNECTED;
            if (connected) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1.0f);
            }
            if (ImGui.button(connected ? "Disconnect" : "Connect", new ImVec2(ImGui.getContentRegionAvailX(), 20))) {
                if (connected) {
                    serialHandler.disconnect();
                } else {
                    serialHandler.connect();
                }
            }
            if (connected) {
                ImGui.popStyleColor();
            }

            ImGui.spacing();

            ImGui.text("Status:");
            ImGui.sameLine();

            ConnectionStatus status = serialHandler.getStatus();
            ImGui.textColored(status.getColor(), status.getStatus());

        }
        ImGui.end();
        ImGui.popStyleVar(1);
    }



}
