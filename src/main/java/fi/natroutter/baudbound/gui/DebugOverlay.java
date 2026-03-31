package fi.natroutter.baudbound.gui;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.event.EventHandler;
import fi.natroutter.baudbound.serial.DeviceConnectionManager;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;

/**
 * Renders a comprehensive real-time debug overlay when the debug overlay setting is enabled.
 * <p>
 * Covers six sections: Performance (FPS history, frame time, vsync/limit), Memory (heap and
 * non-heap), JVM (runtime, threads, GC collectors), System (OS, CPU, load), Devices (per-device
 * status), and Events &amp; States (counts and live state map).
 * <p>
 * The overlay is draggable and auto-resizes to its content. Call {@link #render()} once per frame
 * from the GLFW render thread; it is a no-op when the setting is disabled.
 * <p>
 * Sampling interval and visibility are configured via {@link DataStore.Settings.Debug} in the
 * Settings dialog.
 */
public class DebugOverlay {

    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final EventHandler eventHandler = BaudBound.getEventHandler();
    private final DeviceConnectionManager deviceConnectionManager = BaudBound.getDeviceConnectionManager();

    private static final int HISTORY_SIZE = 120;

    private final float[] fpsHistory       = new float[HISTORY_SIZE];
    private final float[] frameTimeHistory = new float[HISTORY_SIZE];
    private final float[] memoryHistory    = new float[HISTORY_SIZE];
    private int historyIdx = 0;

    /** Interval labels shown in the Settings combo and their corresponding millisecond values. */
    public static final String[] INTERVAL_LABELS = { "Every frame", "50 ms", "100 ms", "250 ms", "500 ms", "1 s", "2 s", "5 s" };
    public static final long[]   INTERVAL_MS     = {             0,      50,      100,      250,      500,  1000,  2000,  5000 };

    private long lastSampleNanos = 0;

    /**
     * Renders the debug overlay for the current frame.
     * No-op when the debug overlay setting is disabled.
     */
    public void render() {
        DataStore.Settings.Debug debug = storage.getData().getSettings().getDebug();
        if (!debug.isOverlay() && !BaudBound.getArgs().isDebug()) return;

        // --- Performance ---
        float fps = ImGui.getIO().getFramerate();
        float frameTimeMs = fps > 0 ? 1000f / fps : 0f;
        Runtime rtSample = Runtime.getRuntime();
        float heapMbSample = (rtSample.totalMemory() - rtSample.freeMemory()) / (1024f * 1024f);

        int intervalIdx = Math.min(debug.getSampleIntervalIdx(), INTERVAL_MS.length - 1);
        long intervalMs = INTERVAL_MS[intervalIdx];
        long nowNanos   = System.nanoTime();
        if (intervalMs == 0 || (nowNanos - lastSampleNanos) >= intervalMs * 1_000_000L) {
            fpsHistory[historyIdx]       = fps;
            frameTimeHistory[historyIdx] = frameTimeMs;
            memoryHistory[historyIdx]    = heapMbSample;
            historyIdx      = (historyIdx + 1) % HISTORY_SIZE;
            lastSampleNanos = nowNanos;
        }

        float minFps = Float.MAX_VALUE, maxFps = 0, sumFps = 0;
        int validSamples = 0;
        for (float f : fpsHistory) {
            if (f > 0) { minFps = Math.min(minFps, f); maxFps = Math.max(maxFps, f); sumFps += f; validSamples++; }
        }
        if (minFps == Float.MAX_VALUE) minFps = 0;
        float avgFps = validSamples > 0 ? sumFps / validSamples : 0;
        DataStore.Settings.Graphics g = storage.getData().getSettings().getGraphics();

        // --- Memory ---
        Runtime rt = Runtime.getRuntime();
        long heapUsed  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapAlloc = rt.totalMemory() / (1024 * 1024);
        long heapMax   = rt.maxMemory() == Long.MAX_VALUE ? -1 : rt.maxMemory() / (1024 * 1024);
        MemoryUsage nonHeap    = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long nonHeapUsed       = nonHeap.getUsed()      / (1024 * 1024);
        long nonHeapCommit     = nonHeap.getCommitted()  / (1024 * 1024);

        // --- JVM ---
        RuntimeMXBean runtimeBean                 = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadBean                   = ManagementFactory.getThreadMXBean();
        List<GarbageCollectorMXBean> gcBeans      = ManagementFactory.getGarbageCollectorMXBeans();
        long uptimeSec = runtimeBean.getUptime() / 1000;
        String uptime  = "%dh %02dm %02ds".formatted(uptimeSec / 3600, (uptimeSec % 3600) / 60, uptimeSec % 60);

        // --- System ---
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getSystemLoadAverage();

        // --- App data ---
        List<DataStore.Device> devices = storage.getData().getDevices();
        List<DataStore.Event>  events  = storage.getData().getEvents();
        long eventsWithConds = events.stream()
                .filter(e -> e.getConditions() != null && !e.getConditions().isEmpty()).count();
        Map<String, String> states = eventHandler.getStates();

        // --- Render ---
        // Pin to left side, centered vertically — pivot (0, 0.5) anchors the left edge at x=10
        // and the vertical midpoint at the screen centre.
        ImGui.setNextWindowPos(10, ImGui.getIO().getDisplaySizeY() / 2f, ImGuiCond.Always, 0f, 0.5f);
        ImGui.setNextWindowBgAlpha(0.75f);
        ImGui.begin("##debugoverlay",
                ImGuiWindowFlags.AlwaysAutoResize   |
                ImGuiWindowFlags.NoSavedSettings    |
                ImGuiWindowFlags.NoFocusOnAppearing |
                ImGuiWindowFlags.NoTitleBar         |
                ImGuiWindowFlags.NoMouseInputs      |
                ImGuiWindowFlags.NoMove             |
                ImGuiWindowFlags.NoNav);

        // Performance
        ImGui.separatorText("Performance");
        ImGui.text("FPS       %.1f  (min %.1f  max %.1f  avg %.1f)".formatted(fps, minFps, maxFps, avgFps));
        ImGui.text("Frame     %.3f ms".formatted(fps > 0 ? 1000f / fps : 0f));
        ImGui.text("VSync     %s".formatted(g.isVsync() ? "ON" : "OFF"));
        ImGui.text("FPS Limit %s".formatted(g.isVsync() ? "vsync" : g.getFpsLimit() > 0 ? String.valueOf(g.getFpsLimit()) : "uncapped"));

        // Memory
        ImGui.separatorText("Memory");
        ImGui.text("Heap      %d MB used / %d MB alloc / %s MB max"
                .formatted(heapUsed, heapAlloc, heapMax < 0 ? "?" : String.valueOf(heapMax)));
        ImGui.text("Non-Heap  %d MB used / %d MB committed".formatted(nonHeapUsed, nonHeapCommit));

        // JVM
        ImGui.separatorText("JVM");
        ImGui.text("Runtime   %s %s".formatted(runtimeBean.getVmName(), runtimeBean.getVmVersion()));
        ImGui.text("Java      %s".formatted(System.getProperty("java.version")));
        ImGui.text("Uptime    %s".formatted(uptime));
        ImGui.text("Threads   %d live  /  %d daemon  /  %d peak"
                .formatted(threadBean.getThreadCount(), threadBean.getDaemonThreadCount(), threadBean.getPeakThreadCount()));
        for (GarbageCollectorMXBean gc : gcBeans) {
            ImGui.text("GC [%s]  runs: %d  time: %d ms"
                    .formatted(gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()));
        }

        // System
        ImGui.separatorText("System");
        textTruncated("OS        ", "%s %s (%s)".formatted(osBean.getName(), osBean.getVersion(), osBean.getArch()), 40);
        ImGui.text("CPUs      %d logical".formatted(osBean.getAvailableProcessors()));
        textTruncated("CPU Load  ", cpuLoad < 0 ? "N/A" : "%.1f%%".formatted(cpuLoad * 100), 40);

        // Devices
        ImGui.separatorText("Devices (%d)".formatted(devices.size()));
        if (devices.isEmpty()) {
            ImGui.textDisabled("No devices configured");
        } else {
            for (DataStore.Device d : devices) {
                ConnectionStatus status = deviceConnectionManager.getStatus(d);
                ImGui.text("[%s]  %s  @  %s  %d baud"
                        .formatted(status.name(), d.getName(), d.getPort() != null ? d.getPort() : "?", d.getBaudRate()));
            }
        }

        // Events & States
        ImGui.separatorText("Events & States");
        ImGui.text("Events    %d total  /  %d with conditions  /  %d without"
                .formatted(events.size(), eventsWithConds, events.size() - eventsWithConds));
        ImGui.text("States    %d active".formatted(states.size()));
        if (!states.isEmpty()) {
            for (Map.Entry<String, String> entry : states.entrySet()) {
                ImGui.text("  [%s] = \"%s\"".formatted(entry.getKey(), entry.getValue()));
            }
        }

        ImGui.end();

        renderGraphsWindow(intervalIdx);
    }

    /**
     * Renders the performance graphs window pinned to the right side of the screen,
     * centered vertically. Shows rolling FPS, frame time, and heap memory plots.
     *
     * @param intervalIdx the current sample interval index (clamped, from {@link DataStore.Settings.Debug})
     */
    private void renderGraphsWindow(int intervalIdx) {
        float displayW = ImGui.getIO().getDisplaySizeX();
        float displayH = ImGui.getIO().getDisplaySizeY();

        // Pin right edge 10px from the screen right, centered vertically
        ImGui.setNextWindowPos(displayW - 10, displayH / 2f, ImGuiCond.Always, 1f, 0.5f);
        ImGui.setNextWindowBgAlpha(0.75f);
        ImGui.begin("##debuggraphs",
                ImGuiWindowFlags.AlwaysAutoResize   |
                ImGuiWindowFlags.NoSavedSettings    |
                ImGuiWindowFlags.NoFocusOnAppearing |
                ImGuiWindowFlags.NoTitleBar         |
                ImGuiWindowFlags.NoMove             |
                ImGuiWindowFlags.NoMouseInputs      |
                ImGuiWindowFlags.NoNav);

        float graphW = 300;
        float graphH = 70;

        long windowMs = INTERVAL_MS[intervalIdx] == 0 ? 0 : INTERVAL_MS[intervalIdx] * HISTORY_SIZE;
        ImGui.separatorText("History Window");
        if (windowMs > 0) {
            ImGui.textDisabled("%d s  (%s per sample)".formatted(windowMs / 1000, INTERVAL_LABELS[intervalIdx]));
        } else {
            ImGui.textDisabled("~%d frames  (every frame)".formatted(HISTORY_SIZE));
        }

        // FPS graph
        float maxFpsGraph = 0;
        for (float f : fpsHistory) maxFpsGraph = Math.max(maxFpsGraph, f);
        ImGui.separatorText("FPS");
        ImGui.plotLines("##fpsgraph", fpsHistory, fpsHistory.length, historyIdx,
                "%.0f fps".formatted(fpsHistory[(historyIdx + HISTORY_SIZE - 1) % HISTORY_SIZE]),
                0, Math.max(maxFpsGraph * 1.2f, 10), new imgui.ImVec2(graphW, graphH));

        // Frame time graph
        float maxFtGraph = 0;
        for (float f : frameTimeHistory) maxFtGraph = Math.max(maxFtGraph, f);
        ImGui.separatorText("Frame Time");
        ImGui.plotLines("##ftgraph", frameTimeHistory, frameTimeHistory.length, historyIdx,
                "%.2f ms".formatted(frameTimeHistory[(historyIdx + HISTORY_SIZE - 1) % HISTORY_SIZE]),
                0, Math.max(maxFtGraph * 1.2f, 1), new imgui.ImVec2(graphW, graphH));

        // Heap memory graph
        float maxMemGraph = 0;
        for (float f : memoryHistory) maxMemGraph = Math.max(maxMemGraph, f);
        long heapMaxMb = Runtime.getRuntime().maxMemory() == Long.MAX_VALUE
                ? (long) maxMemGraph * 2
                : Runtime.getRuntime().maxMemory() / (1024 * 1024);
        ImGui.separatorText("Heap Memory");
        ImGui.plotLines("##memgraph", memoryHistory, memoryHistory.length, historyIdx,
                "%.0f MB".formatted(memoryHistory[(historyIdx + HISTORY_SIZE - 1) % HISTORY_SIZE]),
                0, Math.max(heapMaxMb, 1), new imgui.ImVec2(graphW, graphH));

        ImGui.end();
    }

    /**
     * Renders {@code label + value} as a single text line. If {@code value} exceeds
     * {@code maxLen} characters it is truncated to {@code maxLen - 3} chars with a
     * {@code "..."} suffix. When the text is truncated and the user hovers over it,
     * a tooltip shows the full untruncated value.
     *
     * @param label  fixed prefix (e.g. {@code "OS        "})
     * @param value  the dynamic part of the line
     * @param maxLen maximum character length for {@code value} before truncation
     */
    private static void textTruncated(String label, String value, int maxLen) {
        boolean truncated = value.length() > maxLen;
        String display = truncated ? value.substring(0, maxLen - 3) + "..." : value;
        ImGui.textUnformatted(label + display);
        if (truncated && ImGui.isItemHovered()) {
            ImGui.setTooltip(value);
        }
    }

}