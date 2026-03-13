package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
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

import java.util.Collections;
import java.util.List;

public class MainWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final SerialHandler serialHandler = BaudBound.getSerialHandler();


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
            // spacing + button + spacing + text_line + spacing
            float bottomReserve = itemSpacing * 4 + GuiTheme.BUTTON_HEIGHT + lineHeight;

            GuiHelper.listAndEditorButtons(
                    "##events", events, selectedEvent, true, bottomReserve,
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

            ImGui.spacing();

            boolean connected = serialHandler.getStatus() == ConnectionStatus.CONNECTED;
            if (connected) {
                ImGui.pushStyleColor(ImGuiCol.Button, GuiTheme.COLOR_DELETE_BUTTON);
            }
            if (ImGui.button(connected ? "Disconnect" : "Connect", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
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
