package fi.natroutter.baudbound.gui.util;

import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.dialog.MessageDialog;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class GuiHelper {

    private static FoxLogger logger = BaudBound.getLogger();
    private static MessageDialog messageDialog = BaudBound.getMessageDialog();


    public static <T extends DataStore.Named> void listAndEditorButtons(String id, List<T> data, ImInt selected, boolean fillHeight, Runnable buttonCreate, Runnable buttonEdit, Runnable buttonDuplicate, Runnable buttonDelete, Runnable errorCallback) {
        listAndEditorButtons(id, data, selected, fillHeight, 0f, buttonCreate, buttonEdit, buttonDuplicate, buttonDelete, errorCallback);
    }

    public static <T extends DataStore.Named> void listAndEditorButtons(String id, List<T> data, ImInt selected, boolean fillHeight, float reservedHeight, Runnable buttonCreate, Runnable buttonEdit, Runnable buttonDuplicate, Runnable buttonDelete, Runnable errorCallback) {
        float itemSpacing = ImGui.getStyle().getItemSpacingY();
        float lineHeight = ImGui.getTextLineHeightWithSpacing();

        // listbox_gap(itemSpacing) + spacing(itemSpacing) + separator(1+itemSpacing) + spacing(itemSpacing) + button(20)
        float footerHeight = itemSpacing * 4 + 21;
        float listHeight = fillHeight
                ? ImGui.getContentRegionAvailY() - footerHeight - reservedHeight
                : lineHeight * Math.max(data.size(), 3) + ImGui.getStyle().getFramePaddingY() * 2;

        if (ImGui.beginListBox(id, new ImVec2(ImGui.getContentRegionAvailX(), listHeight))) {
            for (int n = 0; n < data.size(); n++) {
                boolean is_selected = (selected.get() == n);
                if (is_selected) {
                    ImGui.pushStyleColor(ImGuiCol.Header, 0.25882354f, 0.5882353f, 0.9764706f, 0.5f);
                    ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.25882354f, 0.5882353f, 0.9764706f, 0.8f);
                }
                if (ImGui.selectable(data.get(n).getName(), is_selected)) {
                    selected.set(n);
                }
                if (is_selected) {
                    ImGui.popStyleColor(2);
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endListBox();
        }

        // Buttons
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        float btnWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 3) / 4;
        if (ImGui.button("Create", new ImVec2(btnWidth, 20))) {
            buttonCreate.run();
        }
        ImGui.sameLine();
        if (ImGui.button("Edit", new ImVec2(btnWidth, 20))) {
            if (!data.isEmpty()) {
                buttonEdit.run();
            } else {
                messageDialog.show("Error", "No items to edit.", new DialogButton("OK", errorCallback));
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Duplicate", new ImVec2(btnWidth, 20))) {
            if (!data.isEmpty()) {
                buttonDuplicate.run();
            } else {
                messageDialog.show("Error", "No items to duplicate.", new DialogButton("OK", errorCallback));
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Delete", new ImVec2(btnWidth, 20))) {
            if (!data.isEmpty()) {
                buttonDelete.run();
            } else {
                messageDialog.show("Error", "No items to delete.", new DialogButton("OK", errorCallback));
            }
        }
    }

    public static void renderClickableLink(String label, String url) {
        ImGui.textColored(0.3f, 0.7f, 1.0f, 1.0f, label);

        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);

            // Draw underline
            ImVec2 min = ImGui.getItemRectMin();
            ImVec2 max = ImGui.getItemRectMax();
            ImGui.getWindowDrawList().addLine(
                    min.x, max.y,
                    max.x, max.y,
                    ImGui.getColorU32(0.3f, 0.7f, 1.0f, 1.0f)
            );
        }

        if (ImGui.isItemClicked()) {
            try {
                FoxLib.openURL(url);
            } catch (IOException e) {
                logger.error("Failed to open URL '" + url + "': " + e.getMessage());
            }
        }
    }

    public static void toolTip(String content){
        ImGui.sameLine();
        ImGui.textDisabled("(?)");
        ImGui.setItemTooltip(content);
    }

    public static void instructions() {instructions("next fields");}
    public static void instructions(String palce) {
        ImGui.text("Instructions");
        ImGui.beginDisabled();
        ImGui.text("You can use this variables in "+palce+":");
        ImGui.bulletText("{input} - This is the content that was read from the serial port.");
        ImGui.bulletText("{timestamp} - This is the timestamp when the input was read from the serial port.");
        ImGui.endDisabled();
    }

    // Both inputText columns
    public static void keyValueTable(String id, String col0Header, String col1Header, List<ImString[]> rows) {
        int[] r = renderTable(id, col0Header, col1Header, rows,
                row -> ImGui.inputText("##c0" + id + row, rows.get(row)[0]));
        int size = rows.size();
        if (r[0] >= 0)                      rows.remove(r[0]);
        if (r[1] > 0)                       Collections.swap(rows, r[1], r[1] - 1);
        if (r[2] >= 0 && r[2] < size - 1)  Collections.swap(rows, r[2], r[2] + 1);
    }

    // Column 0 is a combo, column 1 is inputText
    public static void keyValueTable(String id, String col0Header, String col1Header,
                                     List<ImString[]> rows, String[] col0Options, List<ImInt> col0Selections) {
        int[] r = renderTable(id, col0Header, col1Header, rows,
                row -> ImGui.combo("##c0" + id + row, col0Selections.get(row), col0Options));
        int size = rows.size();
        if (r[0] >= 0)                      { rows.remove(r[0]); col0Selections.remove(r[0]); }
        if (r[1] > 0)                       { Collections.swap(rows, r[1], r[1] - 1); Collections.swap(col0Selections, r[1], r[1] - 1); }
        if (r[2] >= 0 && r[2] < size - 1)  { Collections.swap(rows, r[2], r[2] + 1); Collections.swap(col0Selections, r[2], r[2] + 1); }
    }

    // Returns int[]{removeIndex, moveUpIndex, moveDownIndex}
    private static int[] renderTable(String id, String col0Header, String col1Header,
                                     List<ImString[]> rows, Consumer<Integer> col0Renderer) {
        int removeIndex = -1, moveUpIndex = -1, moveDownIndex = -1;

        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
        if (ImGui.beginTable(id, 5, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {
            ImGui.tableSetupColumn(col0Header, ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn(col1Header, ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("##up" + id, ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableSetupColumn("##dn" + id, ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableSetupColumn("##rm" + id, ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableHeadersRow();

            for (int row = 0; row < rows.size(); row++) {
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                col0Renderer.accept(row);

                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.inputText("##c1" + id + row, rows.get(row)[1]);

                ImGui.tableSetColumnIndex(2);
                ImGui.beginDisabled(row == 0);
                if (ImGui.button("^##u" + id + row)) moveUpIndex = row;
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(3);
                ImGui.beginDisabled(row == rows.size() - 1);
                if (ImGui.button("v##d" + id + row)) moveDownIndex = row;
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(4);
                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1.0f);
                if (ImGui.button("X##r" + id + row)) removeIndex = row;
                ImGui.popStyleColor();
            }
            ImGui.endTable();
        }
        ImGui.popStyleVar();

        return new int[]{removeIndex, moveUpIndex, moveDownIndex};
    }

}
