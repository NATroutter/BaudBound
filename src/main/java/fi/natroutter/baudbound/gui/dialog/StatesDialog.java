package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.event.EventHandler;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only modal dialog that displays all currently active named states managed by
 * {@link EventHandler}. States are set and cleared by {@code SET_STATE} /
 * {@code CLEAR_STATE} actions and can also be cleared individually from this dialog.
 */
public class StatesDialog extends BaseDialog {

    private final EventHandler eventHandler = BaudBound.getEventHandler();

    @Override
    public void render() {
        if (beginModal("Active States")) {

            Map<String, String> snapshot = eventHandler.getStates();

            if (snapshot.isEmpty()) {
                ImGui.textDisabled("No states are currently active.");
            } else {
                // Collect into a stable list so table rows are consistent this frame.
                List<Map.Entry<String, String>> entries = new ArrayList<>(snapshot.entrySet());
                String clearTarget = null;

                ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
                if (ImGui.beginTable("##states_table", 3,
                        ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {

                    ImGui.tableSetupColumn("Name",  ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn("##clr", ImGuiTableColumnFlags.WidthFixed, 22);
                    ImGui.tableHeadersRow();

                    for (int i = 0; i < entries.size(); i++) {
                        Map.Entry<String, String> entry = entries.get(i);
                        ImGui.tableNextRow();

                        ImGui.tableSetColumnIndex(0);
                        ImGui.textUnformatted(entry.getKey());

                        ImGui.tableSetColumnIndex(1);
                        ImGui.textUnformatted(entry.getValue());

                        ImGui.tableSetColumnIndex(2);
                        ImGui.pushStyleColor(ImGuiCol.Button, GuiTheme.COLOR_DELETE_BUTTON);
                        if (ImGui.button("X##clr" + i)) clearTarget = entry.getKey();
                        ImGui.popStyleColor();
                    }

                    ImGui.endTable();
                }
                ImGui.popStyleVar();

                if (clearTarget != null) {
                    eventHandler.clearState(clearTarget);
                }
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.beginDisabled(snapshot.isEmpty());
            if (ImGui.button("Clear All", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                eventHandler.clearAllStates();
            }
            ImGui.endDisabled();

            endModal();
        }
    }
}