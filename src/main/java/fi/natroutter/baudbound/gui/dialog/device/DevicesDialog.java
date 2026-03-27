package fi.natroutter.baudbound.gui.dialog.device;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.BaseDialog;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.serial.DeviceConnectionManager;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImInt;

import java.util.List;

/**
 * Modal list dialog for managing serial device configurations.
 * <p>
 * Displays all devices in a table with their current {@link ConnectionStatus} and a
 * per-row Connect / Disconnect button. Create and Edit close this dialog and open
 * {@link DeviceEditorDialog} instead; {@code DeviceEditorDialog} reopens this dialog
 * via {@code onClose()}.
 */
public class DevicesDialog extends BaseDialog {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final ImInt selected = new ImInt(0);

    @Override
    public void render() {
        float fixedH = ImGui.getIO().getDisplaySizeY() * 0.6f;
        if (beginModal("Devices", fixedH)) {
            List<DataStore.Device> devices = storage.getData().getDevices();
            DeviceConnectionManager manager = BaudBound.getDeviceConnectionManager();

            // Reserve height for separator + spacing + button row
            float itemSpacing = ImGui.getStyle().getItemSpacingY();
            float footerH = itemSpacing * 4 + GuiTheme.BUTTON_HEIGHT + 1;

            // Scrollable device table
            if (ImGui.beginChild("##devlist", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY() - footerH)) {
                int tableFlags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame;
                // Size the Action column to always fit the wider "Disconnect" label so it never shifts.
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

                        // Column 0: both selection and hover use CellBg so they cover the full
                        // cell including padding. HeaderHovered is suppressed to avoid the
                        // smaller native highlight competing with our full-cell CellBg hover.
                        // Text is rendered manually for vertical centering (SelectableTextAlign.y = 0).
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
                                    ImGui.getColorU32(0.2588f, 0.5882f, 0.9765f, 0.55f));
                        } else if (isSelected) {
                            ImGui.tableSetBgColor(ImGuiTableBgTarget.CellBg,
                                    ImGui.getColorU32(0.2588f, 0.5882f, 0.9765f, 0.35f));
                        } else if (ImGui.isItemHovered()) {
                            ImGui.tableSetBgColor(ImGuiTableBgTarget.CellBg,
                                    ImGui.getColorU32(0.2588f, 0.5882f, 0.9765f, 0.15f));
                        }
                        if (isSelected) ImGui.setItemDefaultFocus();
                        // Draw name text centered vertically within the selectable area
                        float textH = ImGui.getTextLineHeight();
                        ImGui.setCursorScreenPos(cellCursor.x, cellCursor.y + (selectableH - textH) / 2f);
                        ImGui.text(dev.getName());

                        // Column 1: colored connection status
                        ImGui.tableSetColumnIndex(1);
                        ImVec4 color = status.getColor();
                        ImGui.alignTextToFramePadding();
                        ImGui.textColored(color.x, color.y, color.z, color.w, status.getStatus());

                        // Column 2: connect / disconnect button (centered)
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

            // Footer buttons
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float spacing = ImGui.getStyle().getItemSpacingX();
            float btnW    = (ImGui.getContentRegionAvailX() - spacing * 3) / 4;

            boolean selectedConnected = !devices.isEmpty()
                    && manager.getStatus(devices.get(selected.get())) == ConnectionStatus.CONNECTED;

            if (ImGui.button("Create", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                ImGui.closeCurrentPopup();
                BaudBound.getDeviceEditorDialog().show(DialogMode.CREATE, null);
            }
            ImGui.sameLine();
            ImGui.beginDisabled(devices.isEmpty() || selectedConnected);
            if (ImGui.button("Edit", new ImVec2(btnW, GuiTheme.BUTTON_HEIGHT))) {
                ImGui.closeCurrentPopup();
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
                            new DialogButton("OK", this::show));
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
                            new DialogButton("OK", this::show));
                }
            }

            endModal();
        }
    }
}