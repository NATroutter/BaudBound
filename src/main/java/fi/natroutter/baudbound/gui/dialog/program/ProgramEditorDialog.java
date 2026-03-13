package fi.natroutter.baudbound.gui.dialog.program;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.DialogMode;
import fi.natroutter.baudbound.gui.dialog.BaseDialog;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.foxlib.logger.FoxLogger;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiChildFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;

public class ProgramEditorDialog extends BaseDialog {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImString  fieldName      = new ImString();
    private final ImString  fieldPath      = new ImString(512);
    private final ImString  fieldArguments = new ImString(512);
    private final ImBoolean fieldRunAsAdmin = new ImBoolean(false);

    private DialogMode mode = DialogMode.CREATE;
    private DataStore.Actions.Program editing = null;

    @Override
    public void show() {
        show(DialogMode.CREATE, null);
    }

    public void show(DialogMode dialogMode, DataStore.Actions.Program program) {
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

        requestOpen();
    }

    @Override
    protected void onClose() {
        BaudBound.getProgramsDialog().show();
    }

    @Override
    public void render() {
        String title = mode.getType() + " Program";
        if (beginModal(title)) {

            if (ImGui.beginChild("##instructions_wrap", ImGui.getContentRegionAvailX(), 0, ImGuiChildFlags.AutoResizeY)) {
                if (ImGui.collapsingHeader("Instructions")) {
                    ImGui.indent(8);
                    ImGui.spacing();
                    GuiHelper.instructions("fields (Path, Arguments)");
                    ImGui.spacing();
                    ImGui.unindent(8);
                }
            }
            ImGui.endChild();
            ImGui.spacing();


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

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                save();
            }

            endModal();
        }
    }

    private void save() {
        String name = fieldName.get().trim();
        String path = fieldPath.get().trim();

        if (name.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Name is required.", new DialogButton("OK", this::requestOpen));
            return;
        }
        if (path.isEmpty()) {
            BaudBound.getMessageDialog().show("Error", "Path is required.", new DialogButton("OK", this::requestOpen));
            return;
        }

        List<DataStore.Actions.Program> programs = storage.getData().getActions().getPrograms();
        if (programs.stream().anyMatch(p -> p != editing && p.getName().equalsIgnoreCase(name))) {
            BaudBound.getMessageDialog().show("Error", "A program named \"" + name + "\" already exists.", new DialogButton("OK", this::requestOpen));
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