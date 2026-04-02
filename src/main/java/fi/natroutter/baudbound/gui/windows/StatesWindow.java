package fi.natroutter.baudbound.gui.windows;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.event.EventHandler;
import fi.natroutter.baudbound.gui.BaseWindow;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Floating panel window for viewing and manually managing named states.
 * <p>
 * States are normally set/cleared by {@code SET_STATE} / {@code CLEAR_STATE} event actions,
 * but can also be created, edited, and deleted directly from this window.
 */
public class StatesWindow extends BaseWindow {

    private final EventHandler eventHandler = BaudBound.getEventHandler();

    private final ImString fieldName  = new ImString(128);
    private final ImString fieldValue = new ImString(256);

    /** Index of the selected row, or -1 for none. */
    private int selected = -1;
    /** Name of the entry currently being edited (used to detect renames). */
    private String editingKey = null;
    /** Whether the create/edit form is currently visible. */
    private boolean formOpen = false;

    @Override
    public void render() {
        if (!open.get()) return;

        ImGui.setNextWindowSize(420, 340, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(280, 200, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("Active States##stateswindow", open)) {
            Map<String, String> snapshot = eventHandler.getStates();
            List<Map.Entry<String, String>> entries = new ArrayList<>(snapshot.entrySet());

            // Clamp selection
            if (selected >= entries.size()) selected = entries.isEmpty() ? -1 : entries.size() - 1;

            // Calculate heights
            float itemSp    = ImGui.getStyle().getItemSpacingY();
            float formH     = formOpen ? (ImGui.getTextLineHeight() * 2 + ImGui.getFrameHeightWithSpacing() * 2 + itemSp * 4 + GuiTheme.BUTTON_HEIGHT + itemSp * 2 + 2) : 0;
            float footerH   = itemSp * 4 + GuiTheme.BUTTON_HEIGHT + 2;
            float tableH    = Math.max(ImGui.getTextLineHeightWithSpacing() * 3,
                    ImGui.getContentRegionAvailY() - footerH - formH);

            // ── Table ────────────────────────────────────────────────────────
            String deleteTarget = null;
            ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
            if (ImGui.beginChild("##states_list", ImGui.getContentRegionAvailX(), tableH)) {
                if (entries.isEmpty()) {
                    ImGui.textDisabled("No states are currently active.");
                } else if (ImGui.beginTable("##states_table", 3,
                        ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {

                    ImGui.tableSetupColumn("Name",  ImGuiTableColumnFlags.WidthStretch, 0.4f);
                    ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch, 0.6f);
                    ImGui.tableSetupColumn("##del", ImGuiTableColumnFlags.WidthFixed, 22);
                    ImGui.tableHeadersRow();

                    for (int i = 0; i < entries.size(); i++) {
                        Map.Entry<String, String> entry = entries.get(i);
                        boolean isSelected = selected == i;
                        ImGui.tableNextRow();

                        ImGui.tableSetColumnIndex(0);
                        if (isSelected) {
                            ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0,
                                    GuiTheme.colorU32(GuiTheme.COLOR_ACCENT_SELECTED));
                        }
                        if (ImGui.selectable("##sel" + i, isSelected,
                                ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowOverlap,
                                0, ImGui.getFrameHeight())) {
                            selected = i;
                        }
                        ImGui.sameLine();
                        ImGui.textUnformatted(entry.getKey());

                        ImGui.tableSetColumnIndex(1);
                        ImGui.textUnformatted(entry.getValue());

                        ImGui.tableSetColumnIndex(2);
                        ImGui.pushStyleColor(ImGuiCol.Button, GuiTheme.COLOR_DELETE_BUTTON);
                        if (ImGui.button("X##del" + i)) deleteTarget = entry.getKey();
                        ImGui.popStyleColor();
                    }
                    ImGui.endTable();
                }
            }
            ImGui.endChild();
            ImGui.popStyleVar();

            if (deleteTarget != null) {
                eventHandler.clearState(deleteTarget);
                if (selected >= entries.size() - 1) selected = entries.size() - 2;
                formOpen = false;
            }

            // ── Create / Edit form ───────────────────────────────────────────
            if (formOpen) {
                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();

                ImGui.text("Name");
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.inputText("##st_name", fieldName);

                ImGui.text("Value");
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.inputText("##st_value", fieldValue);

                ImGui.spacing();

                float btnW = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2;
                boolean nameBlank = fieldName.get().trim().isEmpty();
                ImGui.beginDisabled(nameBlank);
                if (ImGui.button("Save##stform", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                    String name  = fieldName.get().trim();
                    String value = fieldValue.get().trim();
                    if (editingKey != null && !editingKey.equals(name)) {
                        eventHandler.clearState(editingKey);
                    }
                    eventHandler.setState(name, value);
                    formOpen = false;
                    editingKey = null;
                }
                ImGui.endDisabled();
                ImGui.sameLine();
                if (ImGui.button("Cancel##stform", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                    formOpen = false;
                    editingKey = null;
                }
            }

            // ── Footer buttons ───────────────────────────────────────────────
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float sp   = ImGui.getStyle().getItemSpacingX();
            float btnW = (ImGui.getContentRegionAvailX() - sp * 2) / 3;

            if (ImGui.button("Create##stbtn", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                fieldName.set("");
                fieldValue.set("");
                editingKey = null;
                formOpen = true;
            }
            ImGui.sameLine();
            ImGui.beginDisabled(selected < 0 || selected >= entries.size());
            if (ImGui.button("Edit##stbtn", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                Map.Entry<String, String> e = entries.get(selected);
                fieldName.set(e.getKey());
                fieldValue.set(e.getValue());
                editingKey = e.getKey();
                formOpen = true;
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            ImGui.beginDisabled(snapshot.isEmpty());
            if (ImGui.button("Clear All##stbtn", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                eventHandler.clearAllStates();
                selected = -1;
                formOpen = false;
            }
            ImGui.endDisabled();
        }
        ImGui.end();
    }
}
