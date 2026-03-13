package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ActionType;
import fi.natroutter.baudbound.enums.ConditionType;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
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


public class EventEditorDialog extends BaseDialog {

    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImString fieldName = new ImString();

    // Conditions
    private final List<ImString[]> fieldConditions          = new ArrayList<>();
    private final List<ImInt>      fieldConditionKeys       = new ArrayList<>();
    private final List<ImBoolean>  fieldConditionCaseSens   = new ArrayList<>();

    // Actions
    private final List<ImInt>    fieldActionTypes      = new ArrayList<>();
    private final List<ImInt>    fieldActionComboValues = new ArrayList<>();
    private final List<ImString> fieldActionTextValues  = new ArrayList<>();

    private String[] webhookNames = {};
    private String[] programNames = {};

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

        fieldConditions.clear();
        fieldConditionKeys.clear();
        fieldConditionCaseSens.clear();
        fieldActionTypes.clear();
        fieldActionComboValues.clear();
        fieldActionTextValues.clear();

        if (dialogMode == DialogMode.EDIT && event != null) {
            this.editing = event;
            fieldName.set(event.getName());

            if (event.getConditions() != null) {
                for (DataStore.Event.Condition c : event.getConditions()) {
                    ImString val = new ImString(256);
                    val.set(c.getValue() != null ? c.getValue() : "");
                    fieldConditions.add(new ImString[]{new ImString(128), val});
                    fieldConditionKeys.add(new ImInt(ConditionType.findIndex(c.getType())));
                    fieldConditionCaseSens.add(new ImBoolean(c.isCaseSensitive()));
                }
            }

            if (event.getActions() != null) {
                for (DataStore.Event.Action a : event.getActions()) {
                    ActionType actionType = ActionType.getByName(a.getType());
                    if (actionType == null) actionType = ActionType.values()[0];
                    fieldActionTypes.add(new ImInt(actionType.ordinal()));

                    ImInt comboVal = new ImInt(0);
                    ImString textVal = new ImString(512);
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
                        case OPEN_URL, TYPE_TEXT,
                             COPY_TO_CLIPBOARD, SHOW_NOTIFICATION,
                             WRITE_TO_FILE, APPEND_TO_FILE, PLAY_SOUND -> textVal.set(a.getValue() != null ? a.getValue() : "");
                    }
                    fieldActionComboValues.add(comboVal);
                    fieldActionTextValues.add(textVal);
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
                fieldConditionKeys.add(new ImInt(0));
                fieldConditionCaseSens.add(new ImBoolean(false));
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
                fieldActionComboValues.add(new ImInt(0));
                fieldActionTextValues.add(new ImString(512));
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), 20))) {
                save();
            }

            endModal();
        }
    }

    private void renderActionHints() {
        ImGui.spacing();
        ImGui.text("Format hints");
        ImGui.beginDisabled();

        ImGui.text("Write to File:");
        ImGui.bulletText("Overwrites the file with new content on each trigger.");
        ImGui.bulletText("Value: path  — writes \"{timestamp}: {input}\".");
        ImGui.bulletText("Value: path|content  — writes custom content.");
        ImGui.bulletText("Example: C:\\logs\\latest.txt|{input}");

        ImGui.spacing();
        ImGui.text("Append to File:");
        ImGui.bulletText("Appends a new line to the file on each trigger.");
        ImGui.bulletText("Value: path  — appends \"{timestamp}: {input}\".");
        ImGui.bulletText("Value: path|content  — appends custom content.");
        ImGui.bulletText("Example: C:\\logs\\data.txt|{timestamp}: {input}");

        ImGui.spacing();
        ImGui.text("Show Notification:");
        ImGui.bulletText("Value: message  — shows an INFO notification.");
        ImGui.bulletText("Value: TYPE|message  — TYPE: INFO, WARNING, ERROR, NONE.");
        ImGui.bulletText("Example: WARNING|Temperature high: {input}");

        ImGui.spacing();
        ImGui.text("Play Sound:");
        ImGui.bulletText("Value: path to a .wav file.");
        ImGui.bulletText("Leave empty to play the system beep.");

        ImGui.spacing();
        ImGui.text("Copy to Clipboard:");
        ImGui.bulletText("Text is placed in the clipboard without pasting.");

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
                ImGui.combo("##ctype" + i, fieldConditionKeys.get(i), ConditionType.asFriendlyArray());

                ConditionType condType = ConditionType.values()[fieldConditionKeys.get(i).get()];
                boolean noValue = condType == ConditionType.IS_NUMERIC;
                boolean noCase  = noValue || condType == ConditionType.REGEX
                        || condType == ConditionType.GREATER_THAN || condType == ConditionType.LESS_THAN
                        || condType == ConditionType.BETWEEN       || condType == ConditionType.LENGTH_EQUALS;
                if (noValue) {
                    fieldConditions.get(i)[1].set("");
                    fieldConditionCaseSens.get(i).set(false);
                }

                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.beginDisabled(noValue);
                ImGui.inputText("##cval" + i, fieldConditions.get(i)[1]);
                ImGui.endDisabled();

                ImGui.tableSetColumnIndex(2);
                ImGui.beginDisabled(noCase);
                float checkboxSize = ImGui.getFrameHeight();
                ImGui.setCursorPosX(ImGui.getCursorPosX() + (ImGui.getContentRegionAvailX() - checkboxSize) / 2);
                ImGui.checkbox("##ccs" + i, fieldConditionCaseSens.get(i));
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
                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1.0f);
                if (ImGui.button("X##cr" + i)) removeIndex = i;
                ImGui.popStyleColor();
            }

            ImGui.endTable();
        }
        ImGui.popStyleVar();

        int size = fieldConditions.size();
        if (removeIndex >= 0) {
            fieldConditions.remove(removeIndex);
            fieldConditionKeys.remove(removeIndex);
            fieldConditionCaseSens.remove(removeIndex);
        }
        if (moveUpIndex > 0) {
            Collections.swap(fieldConditions, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldConditionKeys, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldConditionCaseSens, moveUpIndex, moveUpIndex - 1);
        }
        if (moveDownIndex >= 0 && moveDownIndex < size - 1) {
            Collections.swap(fieldConditions, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldConditionKeys, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldConditionCaseSens, moveDownIndex, moveDownIndex + 1);
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
                        fieldActionComboValues.get(i).set(0);
                        fieldActionTextValues.get(i).set("");
                    }
                }

                ActionType actionType = ActionType.values()[fieldActionTypes.get(i).get()];

                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                switch (actionType) {
                    case CALL_WEBHOOK -> {
                        if (webhookNames.length > 0) {
                            ImGui.combo("##awh" + i, fieldActionComboValues.get(i), webhookNames);
                        } else {
                            ImGui.textDisabled("No webhooks");
                        }
                    }
                    case OPEN_PROGRAM -> {
                        if (programNames.length > 0) {
                            ImGui.combo("##aprog" + i, fieldActionComboValues.get(i), programNames);
                        } else {
                            ImGui.textDisabled("No programs");
                        }
                    }
                    case OPEN_URL, TYPE_TEXT,
                         COPY_TO_CLIPBOARD, SHOW_NOTIFICATION,
                         WRITE_TO_FILE, APPEND_TO_FILE, PLAY_SOUND -> ImGui.inputText("##atext" + i, fieldActionTextValues.get(i));
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
                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1.0f);
                if (ImGui.button("X##ar" + i)) removeIndex = i;
                ImGui.popStyleColor();
            }

            ImGui.endTable();
        }
        ImGui.popStyleVar();

        int size = fieldActionTypes.size();
        if (removeIndex >= 0) {
            fieldActionTypes.remove(removeIndex);
            fieldActionComboValues.remove(removeIndex);
            fieldActionTextValues.remove(removeIndex);
        }
        if (moveUpIndex > 0) {
            Collections.swap(fieldActionTypes, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldActionComboValues, moveUpIndex, moveUpIndex - 1);
            Collections.swap(fieldActionTextValues, moveUpIndex, moveUpIndex - 1);
        }
        if (moveDownIndex >= 0 && moveDownIndex < size - 1) {
            Collections.swap(fieldActionTypes, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldActionComboValues, moveDownIndex, moveDownIndex + 1);
            Collections.swap(fieldActionTextValues, moveDownIndex, moveDownIndex + 1);
        }
    }

    private String validateConditions() {
        for (int i = 0; i < fieldConditions.size(); i++) {
            ConditionType type = ConditionType.values()[fieldConditionKeys.get(i).get()];
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
            }
        }
        return null;
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

        List<DataStore.Event.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < fieldConditions.size(); i++) {
            String condType = ConditionType.values()[fieldConditionKeys.get(i).get()].name();
            String condValue = fieldConditions.get(i)[1].get().trim();
            boolean caseSensitive = fieldConditionCaseSens.get(i).get();
            conditions.add(new DataStore.Event.Condition(condType, condValue, caseSensitive));
        }

        List<DataStore.Event.Action> actions = new ArrayList<>();
        for (int i = 0; i < fieldActionTypes.size(); i++) {
            ActionType actionType = ActionType.values()[fieldActionTypes.get(i).get()];
            String value = switch (actionType) {
                case CALL_WEBHOOK -> webhookNames.length > 0 ? webhookNames[fieldActionComboValues.get(i).get()] : null;
                case OPEN_PROGRAM -> programNames.length > 0 ? programNames[fieldActionComboValues.get(i).get()] : null;
                case OPEN_URL, TYPE_TEXT, COPY_TO_CLIPBOARD,
                     SHOW_NOTIFICATION, WRITE_TO_FILE, APPEND_TO_FILE, PLAY_SOUND -> fieldActionTextValues.get(i).get().trim();
            };
            boolean allowEmpty = actionType == ActionType.PLAY_SOUND;
            if (allowEmpty || (value != null && !value.isBlank())) {
                actions.add(new DataStore.Event.Action(actionType.name(), value != null ? value : ""));
            }
        }

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
        ImGui.closeCurrentPopup();
    }
}