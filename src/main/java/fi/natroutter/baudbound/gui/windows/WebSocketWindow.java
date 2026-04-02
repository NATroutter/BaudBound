package fi.natroutter.baudbound.gui.windows;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.gui.BaseWindow;
import fi.natroutter.baudbound.gui.dialog.components.DialogButton;
import fi.natroutter.baudbound.gui.theme.GuiTheme;
import fi.natroutter.baudbound.gui.util.GuiHelper;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.baudbound.websocket.WebSocketHandler;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiChildFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;

/**
 * Floating panel window for configuring and monitoring the built-in WebSocket server.
 * <p>
 * Allows the user to enable/disable the server, set the port (1–65535, default 8765),
 * and configure an optional authentication token. The status section shows whether the
 * server is running and how many clients are currently connected.
 * <p>
 * Saving restarts the server when the port changes or the server is re-enabled.
 */
public class WebSocketWindow extends BaseWindow {

    private final StorageProvider storage = BaudBound.getStorageProvider();

    private final ImBoolean optionEnabled   = new ImBoolean(false);
    private final ImString  optionHost      = new ImString(64);
    private final ImInt     optionPort      = new ImInt(8765);
    private final ImString  optionAuthToken = new ImString(256);
    private final ImBoolean showToken       = new ImBoolean(false);

    /** Opens the window and loads the current stored configuration. */
    @Override
    public void show() {
        DataStore.Settings.WebSocket cfg = storage.getData().getSettings().getWebSocket();
        optionEnabled.set(cfg.isEnabled());
        optionHost.set(cfg.getEffectiveHost());
        optionPort.set(cfg.getEffectivePort());
        optionAuthToken.set(cfg.getAuthToken() != null ? cfg.getAuthToken() : "");
        showToken.set(false);
        super.show();
    }

    @Override
    public void render() {
        if (!open.get()) return;

        // Minimum height: fixed config+status sections + 3 log lines + footer + title bar + padding
        float sp      = ImGui.getStyle().getItemSpacingY();
        float lineH   = ImGui.getTextLineHeightWithSpacing();
        float frameH  = ImGui.getFrameHeightWithSpacing();
        float footerH = sp * 5 + GuiTheme.BUTTON_HEIGHT * 2 + 2;
        float minH    = frameH * 6   // checkbox, port, token, 3 separator rows
                      + lineH * 6    // Port label, Auth Token label, status rows, section labels
                      + sp * 8
                      + lineH * 3    // minimum 3 log lines
                      + footerH
                      + ImGui.getFrameHeight()                   // title bar
                      + ImGui.getStyle().getWindowPaddingY() * 2;

        ImGui.setNextWindowSize(500, 520, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(360, minH, Float.MAX_VALUE, Float.MAX_VALUE);

        if (ImGui.begin("WebSocket Server##wswindow", open,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {
            ImGui.separatorText("Server Configuration");
            ImGui.spacing();

            ImGui.checkbox("Enable WebSocket Server##wsen", optionEnabled);
            GuiHelper.toolTip("When enabled, BaudBound listens for incoming WebSocket connections\n"
                    + "and fires matching events tagged as WebSocket trigger source.");

            ImGui.spacing();
            ImGui.text("Host / Interface");
            GuiHelper.toolTip("The network interface to bind to.\n"
                    + "0.0.0.0 = all interfaces (default)\n"
                    + "127.0.0.1 = loopback only\n"
                    + "192.168.x.x = specific LAN interface");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            ImGui.inputTextWithHint("##wshost", "0.0.0.0 (all interfaces)", optionHost);

            ImGui.spacing();
            ImGui.text("Port");
            GuiHelper.toolTip("The port BaudBound will listen on. Default: 8765.");
            ImGui.setNextItemWidth(120);
            ImGui.inputInt("##wsport", optionPort);
            int port = Math.max(1, Math.min(65535, optionPort.get()));
            optionPort.set(port);

            ImGui.spacing();
            ImGui.text("Auth Token");
            GuiHelper.toolTip("Optional. When set, clients must send AUTH:<token> as their\n"
                    + "first message. Leave blank to allow unauthenticated connections.");

            float tokenFieldW = ImGui.getContentRegionAvailX() - 90 - ImGui.getStyle().getItemSpacingX();
            ImGui.setNextItemWidth(tokenFieldW);
            if (showToken.get()) {
                ImGui.inputText("##wstoken", optionAuthToken);
            } else {
                ImGui.inputText("##wstoken", optionAuthToken, ImGuiInputTextFlags.Password);
            }
            ImGui.sameLine();
            if (ImGui.button(showToken.get() ? "Hide" : "Show", new ImVec2(90, 0))) {
                showToken.set(!showToken.get());
            }

            ImGui.spacing();
            ImGui.separatorText("Status");
            ImGui.spacing();

            WebSocketHandler handler = BaudBound.getWebSocketHandler();
            boolean running = handler != null && handler.isRunning();

            ImGui.text("Status:");
            ImGui.sameLine();
            if (running) {
                ImGui.textColored(0.45f, 0.9f, 0.45f, 1.0f, "Running");
            } else {
                ImGui.textDisabled("Stopped");
            }

            ImGui.text("Connected clients:");
            ImGui.sameLine();
            ImGui.text(handler != null ? String.valueOf(handler.getConnectedCount()) : "0");

            if (running && handler != null) {
                ImGui.text("Address:");
                ImGui.sameLine();
                ImGui.textDisabled("ws://" + handler.getHost() + ":" + handler.getPort());
            }

            ImGui.spacing();
            ImGui.separatorText("Incoming Messages");
            ImGui.spacing();

            List<String> log = handler != null ? handler.getMessageLog() : List.of();
            // Footer below the log: spacing + Clear button + spacing + separator + spacing + Save button
            float logHeight = Math.max(ImGui.getTextLineHeightWithSpacing() * 4,
                    ImGui.getContentRegionAvailY() - footerH);
            if (ImGui.beginChild("##wslog", ImGui.getContentRegionAvailX(), logHeight,
                    ImGuiChildFlags.Border)) {
                ImGui.pushTextWrapPos(0f);
                for (String entry : log) {
                    ImGui.textUnformatted(entry);
                }
                ImGui.popTextWrapPos();
                if (ImGui.getScrollY() >= ImGui.getScrollMaxY()) {
                    ImGui.setScrollHereY(1.0f);
                }
            }
            ImGui.endChild();

            ImGui.spacing();
            if (ImGui.button("Clear##wslogclear", new ImVec2(ImGui.getContentRegionAvailX(), 0))) {
                if (handler != null) handler.clearMessageLog();
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            if (ImGui.button("Save", new ImVec2(ImGui.getContentRegionAvailX(), GuiTheme.BUTTON_HEIGHT))) {
                save();
            }
            ImGui.spacing();
        }
        ImGui.end();
    }

    private void save() {
        DataStore.Settings.WebSocket cfg = storage.getData().getSettings().getWebSocket();

        boolean wasEnabled   = cfg.isEnabled();
        String  oldHost      = cfg.getEffectiveHost();
        int     oldPort      = cfg.getEffectivePort();
        boolean isNowEnabled = optionEnabled.get();
        String  newHost      = optionHost.get().trim().isEmpty() ? "0.0.0.0" : optionHost.get().trim();
        int     newPort      = Math.max(1, Math.min(65535, optionPort.get()));
        boolean bindChanged  = !oldHost.equals(newHost) || oldPort != newPort;

        cfg.setEnabled(isNowEnabled);
        cfg.setHost(newHost);
        cfg.setPort(newPort);
        cfg.setAuthToken(optionAuthToken.get().trim());
        storage.save();

        Thread.ofVirtual().start(() -> {
            WebSocketHandler oldHandler = BaudBound.getWebSocketHandler();
            if (oldHandler != null && (wasEnabled && (bindChanged || !isNowEnabled))) {
                oldHandler.stopServer();
            }
            if (isNowEnabled && (!wasEnabled || bindChanged)) {
                WebSocketHandler newHandler = new WebSocketHandler(newHost, newPort, cfg.getAuthToken());
                BaudBound.setWebSocketHandler(newHandler);
                newHandler.startServer();
            }
        });

        BaudBound.getMessageDialog().show("Saved", "WebSocket settings saved successfully.",
                new DialogButton("OK", () -> {}));
    }
}
