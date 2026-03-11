package fi.natroutter.baudbound.gui.helpers;

import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseCursor;

import java.io.IOException;

public class GuiHelper {

    private static FoxLogger logger = BaudBound.getLogger();

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
        ImGui.bulletText("{date} - This is the current date.");
        ImGui.bulletText("{timestamp} - This is the timestamp when the input was read from the serial port.");
        ImGui.text("Time formating can be changed in the settings");
        ImGui.endDisabled();
    }

}
