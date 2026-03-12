package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ConditionType;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.enums.ActionType;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class EventEditorDialog {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();

    // Conditions
    private final List<ImString[]> fieldConditions = new ArrayList<>();
    private final List<ImInt> fieldConditionKeys = new ArrayList<>();

    // Actions
    private final List<ImInt> fieldActionTypes = new ArrayList<>();
    private final List<ImInt> fieldActionComboValues = new ArrayList<>();
    private final List<ImString> fieldActionTextValues = new ArrayList<>();

    private final ImString fieldName = new ImString();

    private String[] webhookNames = {};
    private String[] programNames = {};

    private DialogMode mode = DialogMode.CREATE;
    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);
    private DataStore.Event editing = null;

    public void show() {
        show(DialogMode.CREATE, null);
    }

    public void show(DialogMode dialogMode, DataStore.Event event) {
        this.open = true;
        this.mode = dialogMode;

        webhookNames = storage.getData().getActions().getWebhooks().stream()
                .map(DataStore.Actions.Webhook::getName).toArray(String[]::new);
        programNames = storage.getData().getActions().getPrograms().stream()
                .map(DataStore.Actions.Program::getName).toArray(String[]::new);

        fieldConditions.clear();
        fieldConditionKeys.clear();
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
                }
            }

            if (event.getActions() != null) {
                for (DataStore.Event.Action a : event.getActions()) {
                    ActionType aType = ActionType.getByName(a.getType());
                    if (aType == null) aType = ActionType.values()[0];
                    fieldActionTypes.add(new ImInt(aType.ordinal()));

                    ImInt comboVal = new ImInt(0);
                    ImString textVal = new ImString(512);

                    switch (aType) {
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
                        case OPEN_URL, TYPE_TEXT -> textVal.set(a.getValue() != null ? a.getValue() : "");
                    }

                    fieldActionComboValues.add(comboVal);
                    fieldActionTextValues.add(textVal);
                }
            }
        } else {
            this.editing = null;
            fieldName.set("");
        }
    }

    private void reopen() {
        this.open = true;
    }

    public void render() {
        String popupTitle = mode.getType() + " Event";

        if (open) {
            ImGui.openPopup(popupTitle);
            modalOpen.set(true);
            open = false;
        }

        ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySizeX() / 2,
                ImGui.getIO().getDisplaySizeY() / 2,
                ImGuiCond.Always,
                0.5f, 0.5f
        );
        ImGui.setNextWindowSizeConstraints(ImGui.getIO().getDisplaySizeX() * 0.9f, 0, ImGui.getIO().getDisplaySizeX() * 0.9f, Float.MAX_VALUE);

        boolean wasOpen = ImGui.isPopupOpen(popupTitle);
        if (ImGui.beginPopupModal(popupTitle, modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            ImGui.text("Name");
            GuiHelper.toolTip("This is the name of the event. it will be displayed in the event list.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.beginDisabled(mode == DialogMode.EDIT);
            ImGui.inputText("##name", fieldName);
            ImGui.endDisabled();

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // --- Conditions ---
            ImGui.text("Conditions");
            GuiHelper.toolTip("Conditions that must be met for the event to trigger.");

            GuiHelper.keyValueTable("##conditions", "Condition", "Value",
                    fieldConditions, ConditionType.asFriendlyArray(), fieldConditionKeys);

            ImGui.spacing();
            if (ImGui.button("Add Condition", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldConditions.add(new ImString[]{new ImString(128), new ImString(256)});
                fieldConditionKeys.add(new ImInt(0));
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            // --- Actions ---
            ImGui.text("Actions");
            GuiHelper.toolTip("Actions performed when all conditions are met.");

            boolean hasTextAction = fieldActionTypes.stream().anyMatch(t -> {
                ActionType at = ActionType.values()[t.get()];
                return at == ActionType.OPEN_URL || at == ActionType.TYPE_TEXT;
            });
            if (hasTextAction) {
                ImGui.spacing();
                GuiHelper.instructions("the value fields");
                ImGui.spacing();
            }

            renderActionsTable();

            ImGui.spacing();
            if (ImGui.button("Add Action", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldActionTypes.add(new ImInt(0));
                fieldActionComboValues.add(new ImInt(0));
                fieldActionTextValues.add(new ImString(512));
            }

            // Buttons
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), 20))) {
                save();
            }

            ImGui.endPopup();
        } else if (wasOpen && !modalOpen.get()) {
            modalOpen.set(true);
        }
    }

    private void renderActionsTable() {
        int removeIndex = -1;
        int moveUpIndex = -1;
        int moveDownIndex = -1;

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

                ActionType aType = ActionType.values()[fieldActionTypes.get(i).get()];

                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                switch (aType) {
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
                    case OPEN_URL  -> ImGui.inputText("##aurl" + i, fieldActionTextValues.get(i));
                    case TYPE_TEXT -> ImGui.inputText("##atext" + i, fieldActionTextValues.get(i));
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

    private void save() {
        String name = fieldName.get().trim();
        if (name.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name is required.", new DialogButton("OK", this::reopen));
            return;
        }

        List<DataStore.Event.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < fieldConditions.size(); i++) {
            String condType = ConditionType.values()[fieldConditionKeys.get(i).get()].name();
            String condValue = fieldConditions.get(i)[1].get().trim();
            conditions.add(new DataStore.Event.Condition(condType, condValue));
        }

        List<DataStore.Event.Action> actions = new ArrayList<>();
        for (int i = 0; i < fieldActionTypes.size(); i++) {
            ActionType aType = ActionType.values()[fieldActionTypes.get(i).get()];
            String value = switch (aType) {
                case CALL_WEBHOOK -> webhookNames.length > 0 ? webhookNames[fieldActionComboValues.get(i).get()] : null;
                case OPEN_PROGRAM -> programNames.length > 0 ? programNames[fieldActionComboValues.get(i).get()] : null;
                case OPEN_URL, TYPE_TEXT -> fieldActionTextValues.get(i).get().trim();
            };
            if (value != null && !value.isBlank()) {
                actions.add(new DataStore.Event.Action(aType.name(), value));
            }
        }

        List<DataStore.Event> events = storage.getData().getEvents();

        if (mode == DialogMode.EDIT && editing != null) {
            editing.setConditions(conditions);
            editing.setActions(actions);
        } else {
            boolean nameExists = events.stream().anyMatch(e -> e.getName().equalsIgnoreCase(name));
            if (nameExists) {
                BaudBound.getMessageDialog().show("Error", "An event named \"" + name + "\" already exists.", new DialogButton("OK", this::reopen));
                return;
            }
            events.add(new DataStore.Event(name, conditions, actions));
        }

        storage.save();
        ImGui.closeCurrentPopup();
    }
}