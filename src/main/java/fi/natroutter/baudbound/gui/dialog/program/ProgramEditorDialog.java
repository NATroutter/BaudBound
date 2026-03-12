package fi.natroutter.baudbound.gui.dialog.program;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.foxlib.logger.FoxLogger;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;

public class ProgramEditorDialog {

    private FoxLogger logger = BaudBound.getLogger();
    private StorageProvider storage = BaudBound.getStorageProvider();

    private final ImString fieldName      = new ImString();
    private final ImString fieldPath      = new ImString(512);
    private final ImString fieldArguments = new ImString(512);
    private final ImBoolean fieldRunAsAdmin = new ImBoolean(false);

    private DialogMode mode = DialogMode.CREATE;
    private boolean open = false;
    private final ImBoolean modalOpen = new ImBoolean(false);
    private DataStore.Actions.Program editing = null;

    private void reopen() {
        this.open = true;
    }

    public void show() {
        show(DialogMode.CREATE, null);
    }

    public void show(DialogMode dialogMode, DataStore.Actions.Program program) {
        this.open = true;
        this.mode = dialogMode;

        if (dialogMode == DialogMode.EDIT && program != null) {
            this.editing = program;
            fieldName.set(program.getName());
            fieldPath.set(program.getPath() != null ? program.getPath() : "");
            fieldArguments.set(program.getArguments() != null ? program.getArguments() : "");
            fieldRunAsAdmin.set(program.isRunAsAdmin());
        } else {
            this.editing = null;
            fieldName.set("");
            fieldPath.set("");
            fieldArguments.set("");
            fieldRunAsAdmin.set(false);
        }
    }

    public void render() {
        String popupTitle = mode.getType() + " Program";

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
        ImGui.setNextWindowSizeConstraints(
                ImGui.getIO().getDisplaySizeX() * 0.9f, 0,
                ImGui.getIO().getDisplaySizeX() * 0.9f, Float.MAX_VALUE
        );

        boolean wasOpen = ImGui.isPopupOpen(popupTitle);
        if (ImGui.beginPopupModal(popupTitle, modalOpen, ImGuiWindowFlags.AlwaysAutoResize)) {

            ImGui.text("Name");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##name", fieldName);

            ImGui.text("Path");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##path", fieldPath);

            ImGui.text("Arguments");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputText("##arguments", fieldArguments);

            ImGui.checkbox("Run as Administrator", fieldRunAsAdmin);

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
            BaudBound.getProgramsDialog().show();
        }
    }

    private void save() {
        String name = fieldName.get().trim();
        String path = fieldPath.get().trim();

        if (name.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name is required.", new DialogButton("OK", this::reopen));
            return;
        }
        if (path.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Path is required.", new DialogButton("OK", this::reopen));
            return;
        }

        List<DataStore.Actions.Program> programs = storage.getData().getActions().getPrograms();
        boolean nameExists = programs.stream().anyMatch(p -> p != editing && p.getName().equalsIgnoreCase(name));
        if (nameExists) {
            BaudBound.getMessageDialog().show("Error",
                    "A program named \"" + name + "\" already exists.",
                    new DialogButton("OK", this::reopen));
            return;
        }

        String arguments = fieldArguments.get().trim();
        boolean runAsAdmin = fieldRunAsAdmin.get();

        if (mode == DialogMode.EDIT && editing != null) {
            editing.setName(name);
            editing.setPath(path);
            editing.setArguments(arguments.isEmpty() ? null : arguments);
            editing.setRunAsAdmin(runAsAdmin);
        } else {
            programs.add(new DataStore.Actions.Program(name, path, arguments.isEmpty() ? null : arguments, runAsAdmin));
        }

        logger.info("Saved program: " + name);
        storage.save();
        ImGui.closeCurrentPopup();
        BaudBound.getProgramsDialog().show();
    }
}