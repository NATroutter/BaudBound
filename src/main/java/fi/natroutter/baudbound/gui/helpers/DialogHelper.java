package fi.natroutter.baudbound.gui.helpers;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.dialog.general.MessageDialog;
import fi.natroutter.baudbound.storage.DataStore;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;

import java.util.List;

public class DialogHelper {

    private static final MessageDialog messageDialog = BaudBound.getMessageDialog();

    public static <T extends DataStore.Named> void listAndEditorButtons(String id, List<T> data, ImInt selected, boolean fillHeight, Runnable buttonCreate, Runnable buttonEdit, Runnable buttonDelete, Runnable errorCallback) {
        float itemSpacing = ImGui.getStyle().getItemSpacingY();
        float lineHeight = ImGui.getTextLineHeightWithSpacing();

        // listbox_gap(itemSpacing) + spacing(itemSpacing) + separator(1+itemSpacing) + spacing(itemSpacing) + button(20)
        float footerHeight = itemSpacing * 4 + 21;
        float listHeight = fillHeight
                ? ImGui.getContentRegionAvailY() - footerHeight
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
        float btnWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3;
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
        if (ImGui.button("Delete", new ImVec2(btnWidth, 20))) {
            if (!data.isEmpty()) {
                buttonDelete.run();
            } else {
                messageDialog.show("Error", "No items to delete.", new DialogButton("OK", errorCallback));
            }
        }
    }


}
