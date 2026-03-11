package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.dialog.components.ConditionType;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.dialog.components.DialogMode;
import fi.natroutter.baudbound.gui.dialog.components.ActionType;
import fi.natroutter.baudbound.gui.helpers.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;


public class EventEditorDialog {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();


    private final ImString fieldName = new ImString();
    private final List<ImString[]> fieldConditions = new ArrayList<>();
    private final List<ImInt> fieldConditionKeys = new ArrayList<>();
    private final ImInt fieldActionType = new ImInt(0);
    private final ImInt fieldActionWebhook = new ImInt(0);
    private final ImString fieldActionOpenUrl = new ImString();
    private final ImString fieldActionOpenProgramPath = new ImString();
    private final ImString fieldActionOpenProgramArgs = new ImString();
    private final ImString fieldActionTypeText = new ImString();



    private String[] webhookNames = {};

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

        List<DataStore.Actions.Webhook> webhooks = storage.getData().getActions().getWebhooks();
        webhookNames = webhooks.stream().map(DataStore.Actions.Webhook::getName).toArray(String[]::new);

        if (dialogMode == DialogMode.EDIT && event != null) {
            this.editing = event;
            fieldName.set(event.getName());
            fieldActionType.set(ActionType.findIndex(event.getType()));

            fieldConditions.clear();
            fieldConditionKeys.clear();
            if (event.getConditions() != null) {
                for (DataStore.Event.Condition c : event.getConditions()) {
                    ImString val = new ImString(256);
                    val.set(c.getValue() != null ? c.getValue() : "");
                    fieldConditions.add(new ImString[]{new ImString(128), val});
                    fieldConditionKeys.add(new ImInt(ConditionType.findIndex(c.getType())));
                }
            }

            fieldActionWebhook.set(0);
            for (int i = 0; i < webhookNames.length; i++) {
                if (webhookNames[i].equals(event.getActionWebhook())) {
                    fieldActionWebhook.set(i);
                    break;
                }
            }
            fieldActionOpenUrl.set(event.getActionOpenUrl() != null ? event.getActionOpenUrl() : "");
            fieldActionOpenProgramPath.set(event.getActionOpenProgramPath() != null ? event.getActionOpenProgramPath() : "");
            fieldActionOpenProgramArgs.set(event.getActionOpenProgramArgs() != null ? event.getActionOpenProgramArgs() : "");
            fieldActionTypeText.set(event.getActionTypeText() != null ? event.getActionTypeText() : "");
        } else {
            this.editing = null;
            fieldName.set("");
            fieldActionType.set(0);
            fieldConditions.clear();
            fieldConditionKeys.clear();
            fieldActionWebhook.set(0);
            fieldActionOpenUrl.set("");
            fieldActionOpenProgramPath.set("");
            fieldActionOpenProgramArgs.set("");
            fieldActionTypeText.set("");
        }
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

            ImGui.text("Conditions");
            GuiHelper.toolTip("This are the conditions that must be met for the event to trigger.");

            int removeIndex = -1;
            ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 4, 6);
            if (ImGui.beginTable("##conditions", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchSame)) {
                ImGui.tableSetupColumn("Condition", imgui.flag.ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("Value", imgui.flag.ImGuiTableColumnFlags.WidthStretch);
                ImGui.tableSetupColumn("##remove", imgui.flag.ImGuiTableColumnFlags.WidthFixed, 22);
                ImGui.tableHeadersRow();

                for (int row = 0; row < fieldConditions.size(); row++) {
                    ImString[] pair = fieldConditions.get(row);
                    ImGui.tableNextRow();

                    ImGui.tableSetColumnIndex(0);
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.combo("##hk" + row, fieldConditionKeys.get(row), ConditionType.asFriendlyArray());

                    ImGui.tableSetColumnIndex(1);
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputText("##hv" + row, pair[1]);

                    ImGui.tableSetColumnIndex(2);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1.0f);
                    if (ImGui.button("X##hr" + row, 0, 0)) {
                        removeIndex = row;
                    }
                    ImGui.popStyleColor();
                }
                ImGui.endTable();
            }

            ImGui.popStyleVar();
            if (removeIndex >= 0) {
                fieldConditions.remove(removeIndex);
                fieldConditionKeys.remove(removeIndex);
            }

            ImGui.spacing();
            if (ImGui.button("Add Condition", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                fieldConditions.add(new ImString[]{new ImString(128), new ImString(256)});
                fieldConditionKeys.add(new ImInt(0));
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ActionType value = ActionType.values()[fieldActionType.get()];

            switch (value) {
                case OPEN_URL -> {
                    GuiHelper.instructions("\"Url to open\"");
                }
                case TYPE_TEXT -> {
                    GuiHelper.instructions("\"Text to type\"");
                }
                case OPEN_PROGRAM -> {
                    GuiHelper.instructions("\"Program Path\", \"Program Arguments\"");
                }
            }

            ImGui.text("Action Type");
            GuiHelper.toolTip("This is the action that will be performed when the all conditions are met.");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo("##actiontype", fieldActionType, ActionType.asFriendlyArray())) {
                fieldActionWebhook.set(0);
                fieldActionOpenUrl.set("");
                fieldActionOpenProgramPath.set("");
                fieldActionOpenProgramArgs.set("");
                fieldActionTypeText.set("");
            }

            switch (value) {
                case CALL_WEBHOOK ->  {
                    ImGui.text("Webhook");
                    GuiHelper.toolTip("This is the webhook that will be called. Webhooks can be created in the webhooks editor you can access it from the menu bars actions tab");
                    if (webhookNames.length > 0) {
                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        ImGui.combo("##Webhook", fieldActionWebhook, webhookNames);
                    } else {
                        ImGui.text("No webhooks available.");
                    }
                }
                case OPEN_URL -> {
                    ImGui.text("Url to open");
                    GuiHelper.toolTip("This is the url that will be opened in the default browser.");
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputText("##openurl", fieldActionOpenUrl);
                }
                case OPEN_PROGRAM -> {
                    ImGui.text("Program Path");
                    GuiHelper.toolTip("This is the path to the program that will be opened.");
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputText("##runprogrampath", fieldActionOpenProgramPath);

                    ImGui.text("Program Aruments");
                    GuiHelper.toolTip("This is the arguments that will be passed to the program.");
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputText("##runprogramargs", fieldActionOpenProgramArgs);
                }
                case TYPE_TEXT -> {
                    ImGui.text("Text to type");
                    GuiHelper.toolTip("This is the text that will be typed by the program using keyboard input emulation.");
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.inputTextMultiline("##texttotype", fieldActionTypeText, ImGuiInputTextFlags.AllowTabInput);
                }
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

    private void save() {
        String name = fieldName.get().trim();
        if (name.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name is required.", new DialogButton("OK", this::show));
            return;
        }

        List<DataStore.Event> events = storage.getData().getEvents();
        String type = ActionType.values()[fieldActionType.get()].name();
        ActionType actionType = ActionType.values()[fieldActionType.get()];

        List<DataStore.Event.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < fieldConditions.size(); i++) {
            String condType = ConditionType.values()[fieldConditionKeys.get(i).get()].name();
            String condValue = fieldConditions.get(i)[1].get().trim();
            conditions.add(new DataStore.Event.Condition(condType, condValue));
        }

        String actionWebhook = null;
        String actionOpenUrl = null;
        String actionOpenProgramPath = null;
        String actionOpenProgramArgs = null;
        String actionTypeText = null;

        switch (actionType) {
            case CALL_WEBHOOK -> actionWebhook = webhookNames.length > 0 ? webhookNames[fieldActionWebhook.get()] : null;
            case OPEN_URL -> actionOpenUrl = fieldActionOpenUrl.get().trim();
            case OPEN_PROGRAM -> {
                actionOpenProgramPath = fieldActionOpenProgramPath.get().trim();
                actionOpenProgramArgs = fieldActionOpenProgramArgs.get().trim();
            }
            case TYPE_TEXT -> actionTypeText = fieldActionTypeText.get().trim();
        }

        if (mode == DialogMode.EDIT && editing != null) {
            editing.setType(type);
            editing.setConditions(conditions);
            editing.setActionWebhook(actionWebhook);
            editing.setActionOpenUrl(actionOpenUrl);
            editing.setActionOpenProgramPath(actionOpenProgramPath);
            editing.setActionOpenProgramArgs(actionOpenProgramArgs);
            editing.setActionTypeText(actionTypeText);
        } else {
            boolean nameExists = events.stream().anyMatch(e -> e.getName().equalsIgnoreCase(name));
            if (nameExists) {
                BaudBound.getMessageDialog().show("Error", "An event named \"" + name + "\" already exists.", new DialogButton("OK", this::show));
                return;
            }
            events.add(new DataStore.Event(name, type, conditions, actionWebhook, actionOpenUrl, actionOpenProgramPath, actionOpenProgramArgs, actionTypeText));
        }

        storage.save();
        ImGui.closeCurrentPopup();
    }
}