package fi.natroutter.baudbound;


import fi.natroutter.baudbound.gui.dialog.device.DeviceEditorDialog;
import fi.natroutter.baudbound.gui.dialog.device.DevicesDialog;
import fi.natroutter.baudbound.gui.dialog.program.ProgramEditorDialog;
import fi.natroutter.baudbound.gui.dialog.program.ProgramsDialog;
import fi.natroutter.baudbound.event.EventHandler;
import fi.natroutter.foxlib.FoxLib;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.gui.DebugOverlay;
import fi.natroutter.baudbound.gui.MainWindow;
import fi.natroutter.baudbound.gui.dialog.AboutDialog;
import fi.natroutter.baudbound.gui.dialog.EventEditorDialog;
import fi.natroutter.baudbound.gui.dialog.MessageDialog;
import fi.natroutter.baudbound.gui.dialog.SettingsDialog;
import fi.natroutter.baudbound.gui.dialog.StatesDialog;
import fi.natroutter.baudbound.gui.dialog.webhook.WebhookEditorDialog;
import fi.natroutter.baudbound.gui.dialog.webhook.WebhooksDialog;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.serial.DeviceConnectionManager;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.system.SingleInstanceManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.app.Application;
import imgui.app.Configuration;
import imgui.flag.ImGuiConfigFlags;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWImage;

/**
 * Application entry point and singleton registry for all major subsystems.
 * <p>
 * Extends imgui-java's {@link Application} to drive the GLFW + ImGui render loop.
 * All singleton services (storage, device connections, dialogs) are initialized in
 * {@link #main} before {@link #launch} is called and are accessible via static getters
 * throughout the application.
 * <p>
 * Cross-thread notes:
 * <ul>
 *   <li>GLFW calls must happen on the GLFW main thread only.</li>
 *   <li>AWT tray callbacks set {@code volatile} flags ({@code pendingShow},
 *       {@code pendingExit}) that are consumed in {@link #process()} on the GLFW thread.</li>
 * </ul>
 */
public class BaudBound extends Application {

    public static final String APP_NAME = "BaudBound";
    public static final String VERSION;
    public static final String BUILD_DATE;

    static {
        Properties props = new Properties();
        try (InputStream is = BaudBound.class.getResourceAsStream("/build.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {}
        VERSION    = props.getProperty("version",    "unknown");
        BUILD_DATE = props.getProperty("build.date", "unknown");
    }

    @Getter private static FoxLogger logger;
    @Getter private static StorageProvider storageProvider;
    @Getter private static EventHandler eventHandler;
    @Getter private static DeviceConnectionManager deviceConnectionManager;
    @Getter private static MessageDialog messageDialog;
    @Getter private static AboutDialog aboutDialog;
    @Getter private static SettingsDialog settingsDialog;
    @Getter private static DevicesDialog devicesDialog;
    @Getter private static DeviceEditorDialog deviceEditorDialog;
    @Getter private static WebhooksDialog webhooksDialog;
    @Getter private static WebhookEditorDialog webhookEditorDialog;
    @Getter private static ProgramsDialog programsDialog;
    @Getter private static ProgramEditorDialog programEditorDialog;
    @Getter private static EventEditorDialog eventEditorDialog;
    @Getter private static StatesDialog statesDialog;

    private static MainWindow mainWindow;
    private static DebugOverlay debugOverlay;

    private static TrayIcon trayIcon = null;

    private volatile boolean pendingShow = false;
    private volatile boolean pendingExit = false;

    private long lastFrameNanos = System.nanoTime();

    public static void main(String[] args) {

        logger = new FoxLogger.Builder()
                .setDebug(false)
                .setPruneOlderThanDays(35)
                .setSaveIntervalSeconds(300)
                .setLoggerName(APP_NAME)
                .build();

        BaudBound app = new BaudBound();
        if (!SingleInstanceManager.tryAcquire(app::requestShow)) {
            FoxLib.println("{BRIGHT_RED}BaudBound is already running.");
            System.exit(0);
        }

        storageProvider = new StorageProvider();
        eventHandler = new EventHandler();

        deviceConnectionManager = new DeviceConnectionManager();
        deviceConnectionManager.autoConnectAll(storageProvider.getData().getDevices());

        settingsDialog = new SettingsDialog();
        devicesDialog = new DevicesDialog();
        deviceEditorDialog = new DeviceEditorDialog();
        webhooksDialog = new WebhooksDialog();
        webhookEditorDialog = new WebhookEditorDialog();
        programsDialog = new ProgramsDialog();
        programEditorDialog = new ProgramEditorDialog();
        messageDialog = new MessageDialog();
        aboutDialog = new AboutDialog();
        eventEditorDialog = new EventEditorDialog();
        statesDialog = new StatesDialog();
        mainWindow = new MainWindow();
        debugOverlay = new DebugOverlay();

        launch(app);
        System.exit(0);

    }

    void requestShow() {
        pendingShow = true;
    }

    /**
     * Sleeps the GLFW thread to enforce the configured FPS limit when vsync is disabled.
     * No-op when vsync is enabled (swap interval already limits the frame rate).
     */
    private void limitFrameRate() {
        DataStore.Settings.Graphics g = storageProvider.getData().getSettings().getGraphics();
        if (g.isVsync() || g.getFpsLimit() <= 0) return;

        long targetNanos = 1_000_000_000L / g.getFpsLimit();
        long sleepNanos  = targetNanos - (System.nanoTime() - lastFrameNanos);
        if (sleepNanos > 1_000_000) {
            try { Thread.sleep(sleepNanos / 1_000_000); } catch (InterruptedException ignored) {}
        }
        lastFrameNanos = System.nanoTime();
    }

    /**
     * Applies the current vsync setting via {@code glfwSwapInterval}.
     * Must be called from the GLFW main thread.
     * <p>
     * If neither vsync nor fpsCap has been configured (both at their Gson-default zero/false),
     * vsync is enabled as a backwards-compatible default.
     */
    public static void applyVsync() {
        DataStore.Settings.Graphics g = storageProvider.getData().getSettings().getGraphics();
        boolean effectiveVsync = g.isVsync() || g.getFpsLimit() == 0;
        GLFW.glfwSwapInterval(effectiveVsync ? 1 : 0);
    }

    public static void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    @Override
    public void process() {
        limitFrameRate();

        if (pendingExit) {
            GLFW.glfwSetWindowShouldClose(getHandle(), true);
            return;
        }

        if (pendingShow) {
            pendingShow = false;
            GLFW.glfwShowWindow(getHandle());
            GLFW.glfwFocusWindow(getHandle());
        }

        mainWindow.render();
        messageDialog.render();
        aboutDialog.render();
        settingsDialog.render();
        devicesDialog.render();
        deviceEditorDialog.render();
        webhooksDialog.render();
        webhookEditorDialog.render();
        eventEditorDialog.render();
        programsDialog.render();
        programEditorDialog.render();
        statesDialog.render();

        debugOverlay.render();
    }


    @Override
    protected void configure(final Configuration config) {
        config.setTitle(APP_NAME);
        config.setWidth(1280);
        config.setHeight(720);
    }

    @Override
    protected void initWindow(final Configuration config) {
        super.initWindow(config);
        applyVsync();
        GLFW.glfwSetWindowSizeLimits(getHandle(), 400, 300, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);

        setWindowIcon(loadIcon());
        setupTray();

        // Intercept close and minimize — hide to tray instead (only if tray is available)
        if (trayIcon != null) {
            GLFW.glfwSetWindowCloseCallback(getHandle(), window -> {
                GLFW.glfwSetWindowShouldClose(window, false);
                GLFW.glfwHideWindow(window);
            });

            GLFW.glfwSetWindowIconifyCallback(getHandle(), (window, iconified) -> {
                if (iconified) {
                    GLFW.glfwRestoreWindow(window);
                    GLFW.glfwHideWindow(window);
                }
            });

            if (storageProvider.getData().getSettings().getGeneric().isStartHidden()) {
                GLFW.glfwHideWindow(getHandle());
            }
        }
    }

    @Override
    protected void initImGui(final Configuration config) {
        super.initImGui(config);

        final ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        GuiTheme.applyDarkRuda();

        // Match the OpenGL clear color to the dark theme WindowBg so no white
        // bleed-through appears at window edges on Linux.
        colorBg.set(GuiTheme.COLOR_WINDOW_BG.x, GuiTheme.COLOR_WINDOW_BG.y, GuiTheme.COLOR_WINDOW_BG.z, GuiTheme.COLOR_WINDOW_BG.w);

        // On HiDPI / fractional-scaled Linux displays (and macOS retina), the window
        // content scale can be > 1.  Scale ImGui's global font size and all style sizes
        // so the UI is legible at the physical resolution.
        float[] xScale = {1f}, yScale = {1f};
        GLFW.glfwGetWindowContentScale(getHandle(), xScale, yScale);
        float scale = Math.max(xScale[0], yScale[0]);
        if (scale > 1.0f) {
            io.setFontGlobalScale(scale);
            ImGui.getStyle().scaleAllSizes(scale);
        }
    }

    @Override
    public void dispose() {
        logger.info("Saving storage...");
        storageProvider.save();
        deviceConnectionManager.disconnectAll();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        SingleInstanceManager.release();
        super.dispose();
    }

    private void setupTray() {
        if (!SystemTray.isSupported()) {
            logger.info("System tray not supported on this platform — start hidden disabled.");
            return;
        }

        try {
            PopupMenu popup = new PopupMenu();

            MenuItem showItem = new MenuItem("Show " + APP_NAME);
            showItem.addActionListener(e -> pendingShow = true);

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> pendingExit = true);

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            BufferedImage iconImage = loadIcon();
            Image trayImage = iconImage != null ? iconImage : createFallbackIcon();
            trayIcon = new TrayIcon(trayImage, APP_NAME, popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> pendingShow = true); // double-click to show

            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            logger.error("Failed to setup system tray: " + e.getMessage());
            trayIcon = null;
        }
    }

    private Image createFallbackIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3B82F6));
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        FontMetrics fm = g.getFontMetrics();
        String letter = "B";
        g.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }

    private BufferedImage loadIcon() {
        try (InputStream is = BaudBound.class.getResourceAsStream("/icon.png")) {
            if (is != null) return ImageIO.read(is);
        } catch (IOException e) {
            logger.error("Failed to load icon.png: " + e.getMessage());
        }
        return null;
    }

    private void setWindowIcon(BufferedImage img) {
        if (img == null) return;

        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
            buffer.put((byte) ((pixel >> 8)  & 0xFF)); // G
            buffer.put((byte) ( pixel        & 0xFF)); // B
            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
        buffer.flip();

        GLFWImage glfwImage = GLFWImage.malloc();
        GLFWImage.Buffer iconBuffer = GLFWImage.malloc(1);
        glfwImage.set(width, height, buffer);
        iconBuffer.put(0, glfwImage);
        GLFW.glfwSetWindowIcon(getHandle(), iconBuffer);
        iconBuffer.free();
        glfwImage.free();
    }

}