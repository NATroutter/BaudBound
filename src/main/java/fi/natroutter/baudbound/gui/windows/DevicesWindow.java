package fi.natroutter.baudbound.gui.windows;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.BaseWindow;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.serial.DeviceConnectionManager;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImInt;

import java.util.List;

/**
 * Floating panel window for managing serial device configurations.
 * <p>
 * Displays all devices in a table with their current {@link ConnectionStatus} and a
 * per-row Connect / Disconnect button. Create and Edit open
 * {@link fi.natroutter.baudbound.gui.dialog.device.DeviceEditorDialog};
 * {@code DeviceEditorDialog} reopens this window via its {@code onClose()} override.
 */
public class DevicesWindow extends BaseWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final ImInt selected = new ImInt(0);

    @Override
    public void render() {
        if (!open.get()) return;

        ImGui.setNextWindowSize(520, 400, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(300, 200, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("Devices##deviceswindow", open)) {
            List<DataStore.Device> devices = storage.getData().getDevices();
            DeviceConnectionManager manager = BaudBound.getDeviceConnectionManager();

            // Reserve height for separator + spacing + button row
            float itemSpacing = ImGui.getStyle().getItemSpacingY();
            float footerH = itemSpacing * 4 + GuiTheme.BUTTON_HEIGHT + 1;

            if (ImGui.beginChild("##devlist", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY() - footerH)) {
                int tableFlags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame;
                float actionColW = ImGui.calcTextSize("Disconnect").x
                        + ImGui.getStyle().getFramePaddingX() * 2
                        + ImGui.getStyle().getCellPaddingX() * 2;
                ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
                if (ImGui.beginTable("##devtable", 3, tableFlags)) {
                    ImGui.tableSetupColumn("Name",   ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableSetupColumn("Status", ImGuiTableColumnFlags.WidthFixed, 140);
                    ImGui.tableSetupColumn("Action", ImGuiTableColumnFlags.WidthFixed, actionColW);
                    ImGui.tableHeadersRow();

                    for (int i = 0; i < devices.size(); i++) {
                        DataStore.Device dev = devices.get(i);
                        ConnectionStatus status = manager.getStatus(dev);
                        boolean isSelected = selected.get() == i;

                        ImGui.tableNextRow();

                        ImGui.tableSetColumnIndex(0);
                        float selectableH = ImGui.getFrameHeight();
                        ImVec2 cellCursor = ImGui.getCursorScreenPos();
                        ImGui.pushStyleColor(ImGuiCol.Header,        0f, 0f, 0f, 0f);
                        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0f, 0f, 0f, 0f);
                        ImGui.pushStyleColor(ImGuiCol.HeaderActive,  0f, 0f, 0f, 0f);
                        if (ImGui.selectable("##row" + i, isSelected,
                                ImGuiSelectableFlags.None, ImGui.getContentRegionAvailX(), selectableH)) {
                            selected.set(i);
                        }
                        ImGui.popStyleColor(3);
                        if (ImGui.isItemActive()) {
                            ImGui.tableSetBgColor(ImGuiTableBgTarget.CellBg,
                                    GuiTheme.colorU32(GuiTheme.COLOR_ACCENT_ACTIVE));
                        } else if (isSelected) {
                            ImGui.tableSetBgColor(ImGuiTableBgTarget.CellBg,
                                    GuiTheme.colorU32(GuiTheme.COLOR_ACCENT_SELECTED));
                        } else if (ImGui.isItemHovered()) {
                            ImGui.tableSetBgColor(ImGuiTableBgTarget.CellBg,
                                    GuiTheme.colorU32(GuiTheme.COLOR_ACCENT_HOVERED));
                        }
                        if (isSelected) ImGui.setItemDefaultFocus();
                        float textH = ImGui.getTextLineHeight();
                        ImGui.setCursorScreenPos(cellCursor.x, cellCursor.y + (selectableH - textH) / 2f);
                        ImGui.text(dev.getName());

                        ImGui.tableSetColumnIndex(1);
                        ImVec4 color = status.getColor();
                        ImGui.alignTextToFramePadding();
                        ImGui.textColored(color.x, color.y, color.z, color.w, status.getStatus());

                        ImGui.tableSetColumnIndex(2);
                        boolean connected = status == ConnectionStatus.CONNECTED;
                        float btnW = ImGui.calcTextSize("Disconnect").x + ImGui.getStyle().getFramePaddingX() * 2;
                        ImGui.setCursorPosX(ImGui.getCursorPosX() + (ImGui.getContentRegionAvailX() - btnW) / 2);
                        if (ImGui.button((connected ? "Disconnect" : "Connect") + "##btn" + i, new ImVec2(btnW, 0))) {
                            if (connected) manager.disconnect(dev);
                            else           manager.connect(dev);
                        }
                    }
                    ImGui.endTable();
                }
                ImGui.popStyleVar();
            }
            ImGui.endChild();

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float spacing = ImGui.getStyle().getItemSpacingX();
            float btnW    = (ImGui.getContentRegionAvailX() - spacing * 3) / 4;

            boolean selectedConnected = !devices.isEmpty()
                    && manager.getStatus(devices.get(selected.get())) == ConnectionStatus.CONNECTED;

            if (ImGui.button("Create", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                BaudBound.getDeviceEditorDialog().show(DialogMode.CREATE, null);
            }
            ImGui.sameLine();
            ImGui.beginDisabled(devices.isEmpty() || selectedConnected);
            if (ImGui.button("Edit", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                BaudBound.getDeviceEditorDialog().show(DialogMode.EDIT, devices.get(selected.get()));
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button("Duplicate", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                if (!devices.isEmpty()) {
                    DataStore.Device copy = devices.get(selected.get()).deepCopy();
                    copy.setName(copy.getName() + " (copy)");
                    devices.add(copy);
                    storage.save();
                } else {
                    BaudBound.getMessageDialog().show("Error", "No devices to duplicate.",
                            new DialogButton("OK", () -> {}));
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Delete", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                if (!devices.isEmpty()) {
                    DataStore.Device dev = devices.get(selected.get());
                    BaudBound.getDeviceConnectionManager().unregister(dev);
                    devices.remove(selected.get());
                    if (selected.get() >= devices.size()) {
                        selected.set(Math.max(0, devices.size() - 1));
                    }
                    storage.save();
                } else {
                    BaudBound.getMessageDialog().show("Error", "No devices to delete.",
                            new DialogButton("OK", () -> {}));
                }
            }
        }
        ImGui.end();
    }
}
