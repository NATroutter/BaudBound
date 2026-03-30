package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ActionType;
import fi.natroutter.baudbound.enums.ConditionType;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * Modal editor for creating and modifying {@link DataStore.Event} entries.
 * <p>
 * Supports both {@link DialogMode#CREATE} and {@link DialogMode#EDIT} modes, selected
 * via the {@link #show(DialogMode, DataStore.Event)} overload. In EDIT mode the existing
 * event's data is pre-loaded into the ImGui state fields.
 * <p>
 * Conditions and actions are backed by parallel lists of ImGui state objects. Mutations
 * (remove, reorder) are deferred until after the table rendering loop to avoid modifying
 * lists while iterating.
 */
public class EventEditorDialog extends BaseDialog {

    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImString fieldName = new ImString();

    // Conditions
    private final List<ImString[]> fieldConditions                = new ArrayList<>();
    private final List<ImInt>      fieldConditionTypeIndices      = new ArrayList<>();
    private final List<ImBoolean>  fieldConditionCaseSensitive    = new ArrayList<>();
    private final List<ImBoolean[]> fieldConditionDeviceSelections = new ArrayList<>();

    // Actions
    private final List<ImInt>    fieldActionTypes            = new ArrayList<>();
    private final List<ImInt>    fieldActionComboIndices     = new ArrayList<>();
    private final List<ImString> fieldActionValues           = new ArrayList<>();
    private final List<ImString> fieldActionSecondaryValues  = new ArrayList<>();

    private static final String[] NOTIFICATION_TYPES = {"INFO", "WARNING", "ERROR", "NONE"};

    private String[] webhookNames = {};
    private String[] programNames = {};
    private String[] deviceNames  = {};

    private DialogMode mode = DialogMode.CREATE;
    private DataStore.Event editing = null;

    public void show() {
        show(DialogMode.CREATE, null);
    }

    public void show(DialogMode dialogMode, DataStore.Event event) {
        this.mode = dialogMode;

        webhookNames = storage.getData().getActions().getWebhooks().stream()
                .map(DataStore.Actions.Webhook::getName).toArray(String[]::new);
        programNames = storage.getData().getActions().getPrograms().stream()
                .map(DataStore.Actions.Program::getName).toArray(String[]::new);
        deviceNames = storage.getData().getDevices().stream()
                .map(DataStore.Device::getName).toArray(String[]::new);

        fieldConditions.clear();
        fieldConditionTypeIndices.clear();
        fieldConditionCaseSensitive.clear();
        fieldConditionDeviceSelections.clear();
        fieldActionTypes.clear();
        fieldActionComboIndices.clear();
        fieldActionValues.clear();
        fieldActionSecondaryValues.clear();

        if (dialogMode == DialogMode.EDIT && event != null) {
            this.editing = event;
            fieldName.set(event.getName());

            if (event.getConditions() != null) {
                for (DataStore.Event.Condition c : event.getConditions()) {
                    ConditionType condType = ConditionType.getByName(c.getType());
                    ImString stateName = new ImString(128);
                    ImString val = new ImString(256);
                    if ((condType == ConditionType.STATE_EQUALS || condType == ConditionType.STATE_NOT_EQUALS) && c.getValue() != null) {
                        String[] parts = c.getValue().split("\\|", 2);
                        if (parts.length == 2) { stateName.set(parts[0].trim()); val.set(parts[1]); }
                        else { val.set(c.getValue()); }
                    } else {
                        val.set(c.getValue() != null ? c.getValue() : "");
                    }
                    fieldConditions.add(new ImString[]{stateName, val});
                    fieldConditionTypeIndices.add(new ImInt(ConditionType.findIndex(c.getType())));
                    fieldConditionCaseSensitive.add(new ImBoolean(c.isCaseSensitive()));

                    ImBoolean[] deviceSel = new ImBoolean[deviceNames.length];
                    for (int j = 0; j < deviceNames.length; j++) deviceSel[j] = new ImBoolean(false);
                    if ((condType == ConditionType.DEVICE_EQUALS || condType == ConditionType.DEVICE_NOT_EQUALS) && c.getValue() != null) {
                        for (String part : c.getValue().split(",")) {
                            String name = part.trim();
                            for (int j = 0; j < deviceNames.length; j++) {
                                if (deviceNames[j].equalsIgnoreCase(name)) { deviceSel[j].set(true); break; }
                            }
                        }
                    }
                    fieldConditionDeviceSelections.add(deviceSel);
                }
            }

            if (event.getActions() != null) {
                for (DataStore.Event.Action a : event.getActions()) {
                    ActionType actionType = ActionType.getByName(a.getType());
                    if (actionType == null) actionType = ActionType.values()[0];
                    fieldActionTypes.add(new ImInt(actionType.ordinal()));

                    ImInt comboVal = new ImInt(0);
                    ImString textVal = new ImString(512);
                    ImString secondaryVal = new ImString(512);
                    switch (actionType) {
                        case CALL_WEBHOOK -> {
                            for (int i = 0; i < webhookNames.length; i++) {
                                if (webhookNames[i].equals(a.getValue())) { comboVal.set(i); break; }
                            }
                        }
                        case OPEN_PROGRAM -> {
                            for (int i = 0; i < programNames.length; i++) {
                                if (programNames[i].equals(a.getValue())) { comboVal.set(i); break; }
                            }
                        }
                        case SHOW_NOTIFICATION -> {
                            String[] parts = a.getValue() != null ? a.getValue().split("\\|", 2) : new String[]{};
                            if (parts.length == 2) {
                                for (int k = 0; k < NOTIFICATION_TYPES.length; k++) {
                                    if (NOTIFICATION_TYPES[k].equalsIgnoreCase(parts[0].trim())) { comboVal.set(k); break; }
                                }
                                textVal.set(parts[1]);
                            } else {
                                textVal.set(parts.length == 1 ? parts[0] : "");
                            }
                        }
                        case WRITE_TO_FILE, APPEND_TO_FILE -> {
                            String[] parts = a.getValue() != null ? a.getValue().split("\\|", 2) : new String[]{};
                            textVal.set(parts.length >= 1 ? parts[0] : "");
                            secondaryVal.set(parts.length == 2 ? parts[1] : "");
                        }
                        case SET_STATE -> {
                            String[] parts = a.getValue() != null ? a.getValue().split("\\|", 2) : new String[]{};
                            if (parts.length == 2) {
                                secondaryVal.set(parts[0]); // state name
                                textVal.set(parts[1]);      // value
                            } else {
                                textVal.set(parts.length == 1 ? parts[0] : "");
                            }
                        }
                        case SEND_TO_DEVICE -> {
                            String[] parts = a.getValue() != null ? a.getValue().split("\\|", 2) : new String[]{};
                            if (parts.length >= 1) {
                                for (int i = 0; i < deviceNames.length; i++) {
                                    if (deviceNames[i].equals(parts[0].trim())) { comboVal.set(i); break; }
                                }
                            }
                            textVal.set(parts.length == 2 ? parts[1] : "");
                        }
                        case OPEN_URL, TYPE_TEXT, COPY_TO_CLIPBOARD,
                             PLAY_SOUND, CLEAR_STATE -> textVal.set(a.getValue() != null ? a.getValue() : "");
                    }
                    fieldActionComboIndices.add(comboVal);
                    fieldActionValues.add(textVal);
                    fieldActionSecondaryValues.add(secondaryVal);
                }
            }
        } else {
            this.editing = null;
            fieldName.set("");
        }

        requestOpen();
    }

    public void render() {
        String title = mode.getType() + " Event";
        if (beginModal(title)) {

            if (ImGui.beginChild("##instructions_wrap", ImGui.getContentRegionAvailX(), 0, ImGuiChildFlags.AutoResizeY)) {
                if (ImGui.collapsingHeader("Instructions")) {
                    ImGui.indent(8);
                    ImGui.spacing();
                    GuiHelper.instructions("the value fields");
                    ImGui.spacing();
                    renderActionHints();
                    ImGui.spacing();
                    ImGui.unindent(8);
                }
            }
            ImGui.endChild();
            ImGui.spacing();

            ImGui.text("Name");
            GuiHelper.toolTip("Name of the event as shown in the event list.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##name", fieldName);

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text("Conditions");
            GuiHelper.toolTip("All conditions must match for the event to trigger.");
            renderConditionsTable();
            ImGui.spacing();
            if (ImGui.button("Add Condition", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldConditions.add(new ImString[]{new ImString(128), new ImString(256)});
                fieldConditionTypeIndices.add(new ImInt(0));
                fieldConditionCaseSensitive.add(new ImBoolean(false));
                ImBoolean[] newDeviceSel = new ImBoolean[deviceNames.length];
                for (int j = 0; j < deviceNames.length; j++) newDeviceSel[j] = new ImBoolean(false);
                fieldConditionDeviceSelections.add(newDeviceSel);
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.text("Actions");
            GuiHelper.toolTip("Actions performed when all conditions are met.");

            ImGui.spacing();
            renderActionsTable();
            ImGui.spacing();

            ImGui.spacing();
            if (ImGui.button("Add Action", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldActionTypes.add(new ImInt(0));
                fieldActionComboIndices.add(new ImInt(0));
                fieldActionValues.add(new ImString(512));
                fieldActionSecondaryValues.add(new ImString(512));
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                save();
            }

            endModal();
        }
    }

    private void renderActionHints() {
        ImGui.spacing();
        ImGui.text("Action format hints");
        ImGui.beginDisabled();

        ImGui.text("Open URL:");
        ImGui.bulletText("Opens the URL in the default system browser.");
        ImGui.bulletText("{input} can be used anywhere in the URL.");
        ImGui.bulletText("Example: https://example.com/item/{input}");

        ImGui.spacing();
        ImGui.text("Type Text:");
        ImGui.bulletText("Writes text by placing it on the clipboard and sending Ctrl+V.");
        ImGui.bulletText("The clipboard contents will be replaced each time this fires.");
        ImGui.bulletText("Make sure the target window is focused before the trigger fires.");

        ImGui.spacing();
        ImGui.text("Copy to Clipboard:");
        ImGui.bulletText("Text is placed in the clipboard without pasting.");

        ImGui.spacing();
        ImGui.text("Write to File / Append to File:");
        ImGui.bulletText("Path: the file to write or append to.");
        ImGui.bulletText("Content: optional template; defaults to \"{timestamp}: {input}\".");
        ImGui.bulletText("{input} and {timestamp} can be used in both fields.");

        ImGui.spacing();
        ImGui.text("Show Notification:");
        ImGui.bulletText("Select the notification type (INFO, WARNING, ERROR, NONE).");
        ImGui.bulletText("Enter the message text. {input} and {timestamp} are supported.");

        ImGui.spacing();
        ImGui.text("Play Sound:");
        ImGui.bulletText("Value: path to a .wav file.");
        ImGui.bulletText("Leave empty to play the system beep.");

        ImGui.spacing();
        ImGui.text("Set State:");
        ImGui.bulletText("State name: optional; leave blank to set the default state.");
        ImGui.bulletText("Value: the value to assign to the state.");

        ImGui.spacing();
        ImGui.text("Clear State:");
        ImGui.bulletText("Leave blank to clear the default state.");
        ImGui.bulletText("Value: name - clears the named state 'name'.");

        ImGui.spacing();
        ImGui.text("Send to Device:");
        ImGui.bulletText("Sends data to a connected serial device.");
        ImGui.bulletText("Select the target device, then enter the data to send.");
        ImGui.bulletText("{input} and {timestamp} can be used in the data field.");
        ImGui.bulletText("No line ending is appended automatically.");

        ImGui.endDisabled();

        ImGui.spacing();
        ImGui.text("Condition format hints");
        ImGui.beginDisabled();

        ImGui.text("State Equals / State Not Equals:");
        ImGui.bulletText("State name: optional; leave blank to check the default state.");
        ImGui.bulletText("Expected value: the value the state must equal (or not equal).");

        ImGui.spacing();
        ImGui.text("State Is Empty:");
        ImGui.bulletText("Leave blank to check the default state.");
        ImGui.bulletText("Value: name - checks if state 'name' is unset.");

        ImGui.spacing();

        ImGui.endDisabled();
    }

    private void renderConditionsTable() {
        int removeIndex = -1, moveUpIndex = -1, moveDownIndex = -1;

        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
        if (ImGui.beginTable("##conditions_table", 6,
                ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {

            ImGui.tableSetupColumn("Condition", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Value",     ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Case sensitive",        ImGuiTableColumnFlags.WidthFixed, ImGui.calcTextSizeX("Case sensitive"));
            ImGui.tableSetupColumn("##cup",     ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableSetupColumn("##cdn",     ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableSetupColumn("##crm",     ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableHeadersRow();

            for (int i = 0; i < fieldConditions.size(); i++) {
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.combo("##ctype" + i, fieldConditionTypeIndices.get(i), ConditionType.asFriendlyArray());

                ConditionType condType = ConditionType.values()[fieldConditionTypeIndices.get(i).get()];
                boolean noValue = !condType.requiresValue();
                boolean noCase  = !condType.supportsCaseSensitivity();
                if (noValue) {
                    fieldConditions.get(i)[1].set("");
                    fieldConditionCaseSensitive.get(i).set(false);
                }

                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                if (condType == ConditionType.DEVICE_EQUALS || condType == ConditionType.DEVICE_NOT_EQUALS) {
                    if (deviceNames.length > 0) {
                        ImBoolean[] sel = fieldConditionDeviceSelections.get(i);
                        StringBuilder previewSb = new StringBuilder();
                        for (int j = 0; j < sel.length; j++) {
                            if (sel[j].get()) { if (!previewSb.isEmpty()) previewSb.append(", "); previewSb.append(deviceNames[j]); }
                        }
                        String preview = previewSb.isEmpty() ? "None" : previewSb.toString();
                        if (ImGui.beginCombo("##cdev" + i, preview)) {
                            for (int j = 0; j < deviceNames.length; j++) {
                                ImGui.checkbox(deviceNames[j] + "##cdevchk" + i + "_" + j, sel[j]);
                            }
                            ImGui.endCombo();
                        }
                    } else {
                        ImGui.textDisabled("No devices");
                    }
                } else if (condType == ConditionType.STATE_EQUALS || condType == ConditionType.STATE_NOT_EQUALS) {
                    float nameW = ImGui.getContentRegionAvailX() * 0.4f;
                    ImGui.setNextItemWidth(nameW);
                    ImGui.inputTextWithHint("##cname" + i, "State name (default)", fieldConditions.get(i)[0]);
                    ImGui.sameLine();
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputTextWithHint("##cval" + i, "Expected value", fieldConditions.get(i)[1]);
                } else {
                    ImGui.beginDisabled(noValue);
                    ImGui.inputText("##cval" + i, fieldConditions.get(i)[1]);
                    ImGui.endDisabled();
                }

                ImGui.tableSetColumnIndex(2);
                ImGui.beginDisabled(noCase);
                float checkboxSize = ImGui.getFrameHeight();
                ImGui.setCursorPosX(ImGui.getCursorPosX() + (ImGui.getContentRegionAvailX() - checkboxSize) / 2);
                ImGui.checkbox("##ccs" + i, fieldConditionCaseSensitive.get(i));
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(3);
                ImGui.beginDisabled(i == 0);
                if (ImGui.button("^##cu" + i)) moveUpIndex = i;
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(4);
                ImGui.beginDisabled(i == fieldConditions.size() - 1);
                if (ImGui.button("v##cd" + i)) moveDownIndex = i;
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(5);
                ImGui.pushStyleColor(ImGuiCol.Button, GuiTheme.COLOR_DELETE_BUTTON);
                if (ImGui.button("X##cr" + i)) removeIndex = i;
                ImGui.popStyleColor();
            }

            ImGui.endTable();
        }
        ImGui.popStyleVar();

        int size = fieldConditions.size();
        if (removeIndex >= 0) {
            fieldConditions.remove(removeIndex);
            fieldConditionTypeIndices.remove(removeIndex);
            fieldConditionCaseSensitive.remove(removeIndex);
            fieldConditionDeviceSelections.remove(removeIndex);
        }
        if (moveUpIndex > 0) {
            Collections.swap(fieldConditions, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldConditionTypeIndices, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldConditionCaseSensitive, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldConditionDeviceSelections, moveUpIndex, moveUpIndex - 1);
        }
        if (moveDownIndex >= 0 && moveDownIndex < size - 1) {
            Collections.swap(fieldConditions, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldConditionTypeIndices, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldConditionCaseSensitive, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldConditionDeviceSelections, moveDownIndex, moveDownIndex + 1);
        }
    }

    private void renderActionsTable() {
        int removeIndex = -1, moveUpIndex = -1, moveDownIndex = -1;

        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
        if (ImGui.beginTable("##actions_table", 5,
                ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {

            ImGui.tableSetupColumn("Type",  ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("##up",  ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableSetupColumn("##dn",  ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableSetupColumn("##rm",  ImGuiTableColumnFlags.WidthFixed, 22);
            ImGui.tableHeadersRow();

            for (int i = 0; i < fieldActionTypes.size(); i++) {
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                int prevType = fieldActionTypes.get(i).get();
                if (ImGui.combo("##atype" + i, fieldActionTypes.get(i), ActionType.asFriendlyArray())) {
                    if (fieldActionTypes.get(i).get() != prevType) {
                        fieldActionComboIndices.get(i).set(0);
                        fieldActionValues.get(i).set("");
                        fieldActionSecondaryValues.get(i).set("");
                    }
                }

                ActionType actionType = ActionType.values()[fieldActionTypes.get(i).get()];

                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                switch (actionType) {
                    case CALL_WEBHOOK -> {
                        if (webhookNames.length > 0) {
                            ImGui.combo("##awh" + i, fieldActionComboIndices.get(i), webhookNames);
                        } else {
                            ImGui.textDisabled("No webhooks");
                        }
                    }
                    case OPEN_PROGRAM -> {
                        if (programNames.length > 0) {
                            ImGui.combo("##aprog" + i, fieldActionComboIndices.get(i), programNames);
                        } else {
                            ImGui.textDisabled("No programs");
                        }
                    }
                    case SEND_TO_DEVICE -> {
                        float avail = ImGui.getContentRegionAvailX();
                        float comboW = avail * 0.4f;
                        ImGui.setNextItemWidth(comboW);
                        if (deviceNames.length > 0) {
                            ImGui.combo("##adev" + i, fieldActionComboIndices.get(i), deviceNames);
                        } else {
                            ImGui.beginDisabled();
                            ImGui.combo("##adev" + i, fieldActionComboIndices.get(i), new String[]{"No devices"});
                            ImGui.endDisabled();
                        }
                        ImGui.sameLine();
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        ImGui.inputText("##atext" + i, fieldActionValues.get(i));
                    }
                    case SHOW_NOTIFICATION -> {
                        float typeW = ImGui.getContentRegionAvailX() * 0.3f;
                        ImGui.setNextItemWidth(typeW);
                        ImGui.combo("##anotiftype" + i, fieldActionComboIndices.get(i), NOTIFICATION_TYPES);
                        ImGui.sameLine();
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        ImGui.inputTextWithHint("##atext" + i, "Message", fieldActionValues.get(i));
                    }
                    case WRITE_TO_FILE, APPEND_TO_FILE -> {
                        float pathW = ImGui.getContentRegionAvailX() * 0.5f;
                        ImGui.setNextItemWidth(pathW);
                        ImGui.inputTextWithHint("##atext" + i, "File path", fieldActionValues.get(i));
                        ImGui.sameLine();
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        ImGui.inputTextWithHint("##asec" + i, "Content ({timestamp}: {input})", fieldActionSecondaryValues.get(i));
                    }
                    case SET_STATE -> {
                        float nameW = ImGui.getContentRegionAvailX() * 0.4f;
                        ImGui.setNextItemWidth(nameW);
                        ImGui.inputTextWithHint("##asec" + i, "State name (default)", fieldActionSecondaryValues.get(i));
                        ImGui.sameLine();
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        ImGui.inputTextWithHint("##atext" + i, "Value", fieldActionValues.get(i));
                    }
                    case OPEN_URL, TYPE_TEXT, COPY_TO_CLIPBOARD,
                         PLAY_SOUND, CLEAR_STATE -> ImGui.inputText("##atext" + i, fieldActionValues.get(i));
                }

                ImGui.tableSetColumnIndex(2);
                ImGui.beginDisabled(i == 0);
                if (ImGui.button("^##au" + i)) moveUpIndex = i;
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(3);
                ImGui.beginDisabled(i == fieldActionTypes.size() - 1);
                if (ImGui.button("v##ad" + i)) moveDownIndex = i;
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(4);
                ImGui.pushStyleColor(ImGuiCol.Button, GuiTheme.COLOR_DELETE_BUTTON);
                if (ImGui.button("X##ar" + i)) removeIndex = i;
                ImGui.popStyleColor();
            }

            ImGui.endTable();
        }
        ImGui.popStyleVar();

        int size = fieldActionTypes.size();
        if (removeIndex >= 0) {
            fieldActionTypes.remove(removeIndex);
            fieldActionComboIndices.remove(removeIndex);
            fieldActionValues.remove(removeIndex);
            fieldActionSecondaryValues.remove(removeIndex);
        }
        if (moveUpIndex > 0) {
            Collections.swap(fieldActionTypes, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldActionComboIndices, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldActionValues, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldActionSecondaryValues, moveUpIndex, moveUpIndex - 1);
        }
        if (moveDownIndex >= 0 && moveDownIndex < size - 1) {
            Collections.swap(fieldActionTypes, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldActionComboIndices, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldActionValues, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldActionSecondaryValues, moveDownIndex, moveDownIndex + 1);
        }
    }

    /**
     * Validates all condition rows and returns a human-readable error message if any
     * condition is invalid, or {@code null} if all pass.
     */
    private String validateConditions() {
        for (int i = 0; i < fieldConditions.size(); i++) {
            ConditionType type = ConditionType.values()[fieldConditionTypeIndices.get(i).get()];
            String val = fieldConditions.get(i)[1].get().trim();
            String label = "Condition " + (i + 1) + " (" + type.getFriendlyName() + "): ";

            switch (type) {
                case STARTS_WITH, ENDS_WITH, CONTAINS, NOT_CONTAINS, NOT_STARTS_WITH, EQUALS -> {
                    if (val.isEmpty()) return label + "value cannot be empty.";
                }
                case REGEX -> {
                    if (val.isEmpty()) return label + "value cannot be empty.";
                    try { Pattern.compile(val); }
                    catch (PatternSyntaxException e) { return label + "invalid regex pattern: " + e.getDescription(); }
                }
                case GREATER_THAN, LESS_THAN -> {
                    if (val.isEmpty()) return label + "value cannot be empty.";
                    try { Double.parseDouble(val); }
                    catch (NumberFormatException e) { return label + "value must be a number (e.g. 42 or 3.14)."; }
                }
                case BETWEEN -> {
                    String[] parts = val.split(",", 2);
                    if (parts.length != 2) return label + "value must be 'min,max' (e.g. 10,50).";
                    try {
                        double min = Double.parseDouble(parts[0].trim());
                        double max = Double.parseDouble(parts[1].trim());
                        if (min > max) return label + "min must be less than or equal to max.";
                    } catch (NumberFormatException e) { return label + "both min and max must be numbers (e.g. 10,50)."; }
                }
                case LENGTH_EQUALS -> {
                    if (val.isEmpty()) return label + "value cannot be empty.";
                    try {
                        int len = Integer.parseInt(val);
                        if (len < 0) return label + "length cannot be negative.";
                    } catch (NumberFormatException e) { return label + "value must be a whole number (e.g. 5)."; }
                }
                case IS_NUMERIC -> {} // no value required
                case STATE_IS_EMPTY -> {} // value is an optional state name; blank = default state
                case STATE_EQUALS, STATE_NOT_EQUALS -> {
                    if (val.isEmpty()) return label + "value cannot be empty.";
                }
                case DEVICE_EQUALS, DEVICE_NOT_EQUALS -> {
                    if (deviceNames.length == 0) return label + "no devices configured.";
                    ImBoolean[] sel = fieldConditionDeviceSelections.get(i);
                    boolean anySelected = false;
                    for (ImBoolean b : sel) { if (b.get()) { anySelected = true; break; } }
                    if (!anySelected) return label + "select at least one device.";
                }
            }
        }
        return null;
    }

    /** Converts the current ImGui condition state into a list of {@link DataStore.Event.Condition} objects. */
    private List<DataStore.Event.Condition> buildConditions() {
        List<DataStore.Event.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < fieldConditions.size(); i++) {
            ConditionType condType = ConditionType.values()[fieldConditionTypeIndices.get(i).get()];
            String typeName = condType.name();
            String value;
            if (condType == ConditionType.DEVICE_EQUALS || condType == ConditionType.DEVICE_NOT_EQUALS) {
                StringBuilder sb = new StringBuilder();
                ImBoolean[] sel = fieldConditionDeviceSelections.get(i);
                for (int j = 0; j < sel.length; j++) {
                    if (sel[j].get()) { if (!sb.isEmpty()) sb.append(","); sb.append(deviceNames[j]); }
                }
                value = sb.toString();
            } else if (condType == ConditionType.STATE_EQUALS || condType == ConditionType.STATE_NOT_EQUALS) {
                String name = fieldConditions.get(i)[0].get().trim();
                String stateVal = fieldConditions.get(i)[1].get().trim();
                value = name.isEmpty() ? stateVal : name + "|" + stateVal;
            } else {
                value = fieldConditions.get(i)[1].get().trim();
            }
            boolean caseSensitive = fieldConditionCaseSensitive.get(i).get();
            conditions.add(new DataStore.Event.Condition(typeName, value, caseSensitive));
        }
        return conditions;
    }

    /**
     * Converts the current ImGui action state into a list of {@link DataStore.Event.Action} objects.
     * Actions with a blank value are omitted unless the type allows empty values (e.g. PLAY_SOUND).
     */
    private List<DataStore.Event.Action> buildActions() {
        List<DataStore.Event.Action> actions = new ArrayList<>();
        for (int i = 0; i < fieldActionTypes.size(); i++) {
            ActionType actionType = ActionType.values()[fieldActionTypes.get(i).get()];
            String value = switch (actionType) {
                case CALL_WEBHOOK -> webhookNames.length > 0 ? webhookNames[fieldActionComboIndices.get(i).get()] : null;
                case OPEN_PROGRAM -> programNames.length > 0 ? programNames[fieldActionComboIndices.get(i).get()] : null;
                case SEND_TO_DEVICE -> deviceNames.length > 0
                        ? deviceNames[fieldActionComboIndices.get(i).get()] + "|" + fieldActionValues.get(i).get().trim()
                        : null;
                case SHOW_NOTIFICATION -> {
                    String type = NOTIFICATION_TYPES[fieldActionComboIndices.get(i).get()];
                    String msg = fieldActionValues.get(i).get().trim();
                    yield msg.isEmpty() ? null : type + "|" + msg;
                }
                case WRITE_TO_FILE, APPEND_TO_FILE -> {
                    String path = fieldActionValues.get(i).get().trim();
                    String content = fieldActionSecondaryValues.get(i).get().trim();
                    yield content.isEmpty() ? path : path + "|" + content;
                }
                case SET_STATE -> {
                    String stateName = fieldActionSecondaryValues.get(i).get().trim();
                    String stateVal = fieldActionValues.get(i).get().trim();
                    yield stateName.isEmpty() ? stateVal : stateName + "|" + stateVal;
                }
                case OPEN_URL, TYPE_TEXT, COPY_TO_CLIPBOARD,
                     PLAY_SOUND, CLEAR_STATE -> fieldActionValues.get(i).get().trim();
            };
            boolean allowEmptyValue = actionType == ActionType.PLAY_SOUND || actionType == ActionType.CLEAR_STATE;
            if (allowEmptyValue || (value != null && !value.isBlank())) {
                actions.add(new DataStore.Event.Action(actionType.name(), value != null ? value : ""));
            }
            // Note: value may be null only for CALL_WEBHOOK / OPEN_PROGRAM when no entries exist.
        }
        return actions;
    }

    private void save() {
        String name = fieldName.get().trim();
        if (name.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name is required.", new DialogButton("OK", this::requestOpen));
            return;
        }

        String conditionError = validateConditions();
        if (conditionError != null) {
            BaudBound.getMessageDialog().show("Error", conditionError, new DialogButton("OK", this::requestOpen));
            return;
        }

        List<DataStore.Event.Condition> conditions = buildConditions();
        List<DataStore.Event.Action> actions = buildActions();
        List<DataStore.Event> events = storage.getData().getEvents();

        if (mode == DialogMode.EDIT && editing != null) {
            if (!editing.getName().equalsIgnoreCase(name) && events.stream().anyMatch(e -> e.getName().equalsIgnoreCase(name))) {
                BaudBound.getMessageDialog().show("Error", "An event named \"" + name + "\" already exists.", new DialogButton("OK", this::requestOpen));
                return;
            }
            editing.setName(name);
            editing.setConditions(conditions);
            editing.setActions(actions);
        } else {
            if (events.stream().anyMatch(e -> e.getName().equalsIgnoreCase(name))) {
                BaudBound.getMessageDialog().show("Error", "An event named \"" + name + "\" already exists.", new DialogButton("OK", this::requestOpen));
                return;
            }
            events.add(new DataStore.Event(name, conditions, actions));
        }

        storage.save();
        BaudBound.getEventHandler().invalidateSortCache();
        ImGui.closeCurrentPopup();
        BaudBound.getMessageDialog().show("Saved", "Event \"" + name + "\" saved successfully.", new DialogButton("OK", () -> {}));
    }
}