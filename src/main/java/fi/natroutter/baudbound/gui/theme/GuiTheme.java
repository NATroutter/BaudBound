package fi.natroutter.baudbound.gui.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;

/**
 * Shared visual constants and the application's ImGui theme.
 * <p>
 * Call {@link #applyDarkRuda()} once during {@code initImGui} to apply the dark Ruda-inspired
 * color scheme. The {@link #BUTTON_HEIGHT}, {@link #COLOR_DELETE_BUTTON}, and
 * {@code COLOR_ACCENT_*} constants are referenced throughout the UI to keep sizing and
 * coloring consistent. Use {@link #colorU32(ImVec4)} to convert an {@link ImVec4} constant
 * to the packed integer form required by draw-list and table background APIs.
 */
public class GuiTheme {

    /** Standard height for action buttons (Create, Save, Connect, etc.). */
    public static final float BUTTON_HEIGHT = 20f;

    /** Red color components (r, g, b, a) for destructive/delete buttons. */
    public static final ImVec4 COLOR_DELETE_BUTTON = new ImVec4(0.6f, 0.1f, 0.1f, 1.0f);

    /** Accent blue — pressed/active state (e.g. table row held down). */
    public static final ImVec4 COLOR_ACCENT_ACTIVE          = new ImVec4(0.2588f, 0.5882f, 0.9765f, 0.55f);
    /** Accent blue — selected state (e.g. selected table row CellBg). */
    public static final ImVec4 COLOR_ACCENT_SELECTED        = new ImVec4(0.2588f, 0.5882f, 0.9765f, 0.35f);
    /** Accent blue — hovered state (e.g. table row under cursor). */
    public static final ImVec4 COLOR_ACCENT_HOVERED         = new ImVec4(0.2588f, 0.5882f, 0.9765f, 0.15f);
    /** Accent blue — {@code ImGuiCol.Header} override for selected list items. */
    public static final ImVec4 COLOR_ACCENT_HEADER          = new ImVec4(0.2588f, 0.5882f, 0.9765f, 0.5f);
    /** Accent blue — {@code ImGuiCol.HeaderHovered} override for selected list items. */
    public static final ImVec4 COLOR_ACCENT_HEADER_HOVERED  = new ImVec4(0.2588f, 0.5882f, 0.9765f, 0.8f);

    /** Light blue used for clickable link text and underlines. */
    public static final ImVec4 COLOR_LINK = new ImVec4(0.3f, 0.7f, 1.0f, 1.0f);

    /** Main window background color — used as the ImGui {@code WindowBg} and the GL clear color. */
    public static final ImVec4 COLOR_WINDOW_BG = new ImVec4(0.10980392f, 0.14901961f, 0.16862746f, 1.0f);

    /** Returns the packed ABGR integer for the given color, usable with draw-list and table APIs. */
    public static int colorU32(ImVec4 c) {
        return ImGui.getColorU32(c.x, c.y, c.z, c.w);
    }

/** Applies the dark Ruda color scheme to the current ImGui style. Call once during startup. */
public static void applyDarkRuda() {
    // custom1 style from ImThemes
    ImGuiStyle style = ImGui.getStyle();

    style.setAlpha(1.0f);
    style.setDisabledAlpha(0.6f);
    style.setWindowPadding(8.0f, 8.0f);
    style.setWindowRounding(8.0f);
    style.setWindowBorderSize(1.0f);
    style.setWindowMinSize(32.0f, 32.0f);
    style.setWindowTitleAlign(0.0f, 0.5f);
    style.setWindowMenuButtonPosition(ImGuiDir.Left);
    style.setChildRounding(0.0f);
    style.setChildBorderSize(1.0f);
    style.setPopupRounding(4.0f);
    style.setPopupBorderSize(0.0f);
    style.setFramePadding(8.0f, 4.0f);
    style.setFrameRounding(4.0f);
    style.setFrameBorderSize(0.0f);
    style.setItemSpacing(8.0f, 4.0f);
    style.setItemInnerSpacing(4.0f, 4.0f);
    style.setCellPadding(4.0f, 2.0f);
    style.setIndentSpacing(21.0f);
    style.setColumnsMinSpacing(6.0f);
    style.setScrollbarSize(14.0f);
    style.setScrollbarRounding(9.0f);
    style.setGrabMinSize(10.0f);
    style.setGrabRounding(4.0f);
    style.setTabRounding(4.0f);
    style.setTabBorderSize(0.0f);
    style.setTabMinWidthForCloseButton(0.0f);
    style.setColorButtonPosition(ImGuiDir.Right);
    style.setButtonTextAlign(0.5f, 0.5f);
    style.setSelectableTextAlign(0.0f, 0.0f);


    style.setColor(ImGuiCol.Text, 0.9490196f, 0.95686275f, 0.9764706f, 1.0f);
    style.setColor(ImGuiCol.TextDisabled, 0.35686275f, 0.41960785f, 0.46666667f, 1.0f);
    style.setColor(ImGuiCol.WindowBg, COLOR_WINDOW_BG.x, COLOR_WINDOW_BG.y, COLOR_WINDOW_BG.z, COLOR_WINDOW_BG.w);
    style.setColor(ImGuiCol.ChildBg, 0.14901961f, 0.1764706f, 0.21960784f, 1.0f);
    style.setColor(ImGuiCol.PopupBg, 0.078431375f, 0.078431375f, 0.078431375f, 0.94f);
    style.setColor(ImGuiCol.Border, 0.078431375f, 0.09803922f, 0.11764706f, 1.0f);
    style.setColor(ImGuiCol.BorderShadow, 0.0f, 0.0f, 0.0f, 0.0f);
    style.setColor(ImGuiCol.FrameBg, 0.2f, 0.24705882f, 0.28627452f, 1.0f);
    style.setColor(ImGuiCol.FrameBgHovered, 0.11764706f, 0.2f, 0.2784314f, 1.0f);
    style.setColor(ImGuiCol.FrameBgActive, 0.08627451f, 0.11764706f, 0.13725491f, 1.0f);
    style.setColor(ImGuiCol.TitleBg, 0.08627451f, 0.11764706f, 0.13725491f, 0.65f);
    style.setColor(ImGuiCol.TitleBgActive, 0.078431375f, 0.09803922f, 0.11764706f, 1.0f);
    style.setColor(ImGuiCol.TitleBgCollapsed, 0.0f, 0.0f, 0.0f, 0.51f);
    style.setColor(ImGuiCol.MenuBarBg, 0.14901961f, 0.1764706f, 0.21960784f, 1.0f);
    style.setColor(ImGuiCol.ScrollbarBg, 0.019607844f, 0.019607844f, 0.019607844f, 0.39f);
    style.setColor(ImGuiCol.ScrollbarGrab, 0.2f, 0.24705882f, 0.28627452f, 1.0f);
    style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.1764706f, 0.21960784f, 0.24705882f, 1.0f);
    style.setColor(ImGuiCol.ScrollbarGrabActive, 0.08627451f, 0.20784314f, 0.30980393f, 1.0f);
    style.setColor(ImGuiCol.CheckMark, 0.2784314f, 0.5568628f, 1.0f, 1.0f);
    style.setColor(ImGuiCol.SliderGrab, 0.2784314f, 0.5568628f, 1.0f, 1.0f);
    style.setColor(ImGuiCol.SliderGrabActive, 0.36862746f, 0.60784316f, 1.0f, 1.0f);
    style.setColor(ImGuiCol.Button, 0.2f, 0.24705882f, 0.28627452f, 1.0f);
    style.setColor(ImGuiCol.ButtonHovered, 0.2784314f, 0.5568628f, 1.0f, 1.0f);
    style.setColor(ImGuiCol.ButtonActive, 0.05882353f, 0.5294118f, 0.9764706f, 1.0f);
    style.setColor(ImGuiCol.Header, 0.2f, 0.24705882f, 0.28627452f, 0.55f);
    style.setColor(ImGuiCol.HeaderHovered, 0.25882354f, 0.5882353f, 0.9764706f, 0.8f);
    style.setColor(ImGuiCol.HeaderActive, 0.25882354f, 0.5882353f, 0.9764706f, 1.0f);
    style.setColor(ImGuiCol.Separator, 0.2f, 0.24705882f, 0.28627452f, 1.0f);
    style.setColor(ImGuiCol.SeparatorHovered, 0.09803922f, 0.4f, 0.7490196f, 0.78f);
    style.setColor(ImGuiCol.SeparatorActive, 0.09803922f, 0.4f, 0.7490196f, 1.0f);
    style.setColor(ImGuiCol.ResizeGrip, 0.25882354f, 0.5882353f, 0.9764706f, 0.25f);
    style.setColor(ImGuiCol.ResizeGripHovered, 0.25882354f, 0.5882353f, 0.9764706f, 0.67f);
    style.setColor(ImGuiCol.ResizeGripActive, 0.25882354f, 0.5882353f, 0.9764706f, 0.95f);
    style.setColor(ImGuiCol.Tab, 0.10980392f, 0.14901961f, 0.16862746f, 1.0f);
    style.setColor(ImGuiCol.TabHovered, 0.25882354f, 0.5882353f, 0.9764706f, 0.8f);
    style.setColor(ImGuiCol.TabActive, 0.2f, 0.24705882f, 0.28627452f, 1.0f);
    style.setColor(ImGuiCol.TabUnfocused, 0.10980392f, 0.14901961f, 0.16862746f, 1.0f);
    style.setColor(ImGuiCol.TabUnfocusedActive, 0.10980392f, 0.14901961f, 0.16862746f, 1.0f);
    style.setColor(ImGuiCol.PlotLines, 0.60784316f, 0.60784316f, 0.60784316f, 1.0f);
    style.setColor(ImGuiCol.PlotLinesHovered, 1.0f, 0.42745098f, 0.34901962f, 1.0f);
    style.setColor(ImGuiCol.PlotHistogram, 0.2784314f, 0.5568628f, 1.0f, 1.0f);
    style.setColor(ImGuiCol.PlotHistogramHovered, 0.20112728f, 0.42002246f, 0.76824033f, 1.0f);
    style.setColor(ImGuiCol.TableHeaderBg, 0.1882353f, 0.1882353f, 0.2f, 1.0f);
    style.setColor(ImGuiCol.TableBorderStrong, 0.30980393f, 0.30980393f, 0.34901962f, 1.0f);
    style.setColor(ImGuiCol.TableBorderLight, 0.22745098f, 0.22745098f, 0.24705882f, 1.0f);
    style.setColor(ImGuiCol.TableRowBg, 0.0f, 0.0f, 0.0f, 0.0f);
    style.setColor(ImGuiCol.TableRowBgAlt, 1.0f, 1.0f, 1.0f, 0.06f);
    style.setColor(ImGuiCol.TextSelectedBg, 0.25882354f, 0.5882353f, 0.9764706f, 0.35f);
    style.setColor(ImGuiCol.DragDropTarget, 1.0f, 1.0f, 0.0f, 0.9f);
    style.setColor(ImGuiCol.NavHighlight, 0.25882354f, 0.5882353f, 0.9764706f, 1.0f);
    style.setColor(ImGuiCol.NavWindowingHighlight, 1.0f, 1.0f, 1.0f, 0.7f);
    style.setColor(ImGuiCol.NavWindowingDimBg, 0.8f, 0.8f, 0.8f, 0.2f);
    style.setColor(ImGuiCol.ModalWindowDimBg, 0.8f, 0.8f, 0.8f, 0.35f);
}

}
