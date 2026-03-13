package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageDialog {

    private boolean shouldOpen = false;
    private String message = "";
    private List<DialogButton> buttons = new ArrayList<>();
    private String popupId = "";

    public void show(String title, String message) {
        show(title, message, new DialogButton("OK"));
    }

    public void show(String title, String message, DialogButton... buttons) {
        this.message = message;
        this.buttons = Arrays.asList(buttons);
        this.popupId = title + "##MessageDialog";
        this.shouldOpen = true;
    }

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(popupId);
            shouldOpen = false;

            // Center the popup on the first appearance
            float centerX = ImGui.getIO().getDisplaySizeX() / 2;
            float centerY = ImGui.getIO().getDisplaySizeY() / 2;
            ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Appearing, 0.5f, 0.5f);
            ImGui.setNextWindowSize(300, 200, ImGuiCond.Appearing);
        }

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textWrapped(message);
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            renderButtons();

            ImGui.endPopup();
        }
    }

    private void renderButtons() {
        if (buttons.isEmpty()) {
            return;
        }

        float availableWidth = ImGui.getContentRegionAvailX();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float totalSpacing = spacing * (buttons.size() - 1);
        float buttonWidth = (availableWidth - totalSpacing) / buttons.size();

        for (int i = 0; i < buttons.size(); i++) {
            DialogButton button = buttons.get(i);
            if (ImGui.button(button.label, new ImVec2(buttonWidth, 20))) {
                if (button.action != null) {
                    button.action.run();
                }
                ImGui.closeCurrentPopup();
            }

            if (i < buttons.size() - 1) {
                ImGui.sameLine(0, spacing);
            }
        }
    }
}