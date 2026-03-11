package fi.natroutter.baudbound;


import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.gui.MainWindow;
import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.EventEditorDialog;
import fi.natroutter.baudbound.gui.dialog.general.MessageDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.actions.WebhookEditorDialog;
import fi.natroutter.baudbound.gui.dialog.actions.WebhooksDialog;
import fi.natroutter.baudbound.gui.helpers.GuiTheme;
import fi.natroutter.baudbound.serial.SerialHandler;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.app.Application;
import imgui.app.Configuration;
import imgui.flag.ImGuiConfigFlags;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;

public class BaudBound extends Application {

    // Format: month-day-year
    public static String APP_NAME = "BaudBound";
    public static String BUILD_DATE = "3-10-2025";
    public static String VERSION = "1.0.0";

    @Getter private static FoxLogger logger;
    @Getter private static StorageProvider storageProvider;
    @Getter private static SerialHandler serialHandler;
    @Getter private static MessageDialog messageDialog;
    @Getter private static AboutDialog aboutDialog;
    @Getter private static SettingsDialog settingsDialog;
    @Getter private static WebhooksDialog webhooksDialog;
    @Getter private static WebhookEditorDialog webhookEditorDialog;
    @Getter private static EventEditorDialog eventEditorDialog;

    private static MainWindow mainWindow;

    public static void main(String[] args) {

        logger = new FoxLogger.Builder()
                .setDebug(false)
                .setPruneOlderThanDays(35)
                .setSaveIntervalSeconds(300)
                .setLoggerName(APP_NAME)
                .build();

        storageProvider = new StorageProvider();
        serialHandler = new SerialHandler();

        settingsDialog = new SettingsDialog();
        webhooksDialog = new WebhooksDialog();
        webhookEditorDialog = new WebhookEditorDialog();
        messageDialog = new MessageDialog();
        aboutDialog = new AboutDialog();
        eventEditorDialog = new EventEditorDialog();
        mainWindow = new MainWindow();

        launch(new BaudBound());
        System.exit(0);

    }

    @Override
    public void process() {
        mainWindow.render();
        messageDialog.render();
        aboutDialog.render();
        settingsDialog.render();
        webhooksDialog.render();
        webhookEditorDialog.render();
        eventEditorDialog.render();
    }

    @Override
    protected void configure(final Configuration config) {
        config.setTitle(APP_NAME+" | v"+VERSION);
        config.setWidth(1280);
        config.setHeight(720);
    }

    @Override
    protected void initWindow(final Configuration config) {
        super.initWindow(config);
        GLFW.glfwSetWindowSizeLimits(getHandle(), 400, 300, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE );
    }

    @Override
    protected void initImGui(final Configuration config) {
        super.initImGui(config);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Saving storage...");
            storageProvider.save();
        }));

        final ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        GuiTheme.applyDarkRuda();
    }


}