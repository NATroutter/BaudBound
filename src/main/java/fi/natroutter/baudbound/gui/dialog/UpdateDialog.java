package fi.natroutter.baudbound.gui.dialog;

import fi.natroutter.foxlib.updates.data.VersionInfo;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.system.UpdateManager;
import imgui.ImGui;
import imgui.flag.ImGuiChildFlags;

import java.io.File;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modal dialog that presents available update information and drives the download-and-restart flow.
 * <p>
 * Shows the current and latest version numbers, build date, collapsible (and scrollable) release
 * notes rendered from Markdown, and a "Download &amp; Restart" button with an inline progress bar.
 * <p>
 * Thread usage:
 * <ul>
 *   <li>{@link #showUpdate(VersionInfo)} — safe to call from any thread (background checker);
 *       auto-shows only once per session.</li>
 *   <li>{@link #openWith(VersionInfo)} — safe to call from any thread; always opens the dialog
 *       (used by {@link AboutDialog} when the user clicks "View Update Details").</li>
 *   <li>The {@code volatile VersionInfo pending} field is consumed in {@link #render()} on the
 *       GLFW main thread, which also calls {@link #requestOpen()}.</li>
 * </ul>
 */
public class UpdateDialog extends BaseDialog {

    /** Set from any thread; consumed in {@link #render()} on the GLFW thread. */
    private volatile VersionInfo pending = null;

    /** Prevents the background checker from re-opening the dialog on every check cycle. */
    private boolean autoShowGuard = false;

    private VersionInfo versionInfo = null;

    private enum DownloadState { IDLE, DOWNLOADING, ERROR }
    private volatile DownloadState downloadState = DownloadState.IDLE;
    private volatile long downloadedBytes = 0;
    private volatile long totalBytes      = -1;
    private volatile String downloadError = null;

    // -------------------------------------------------------------------------
    // Public API — may be called from any thread
    // -------------------------------------------------------------------------

    /**
     * Schedules the dialog to open with {@code info} if it has not already been
     * auto-shown in this session. Intended for the background checker callback.
     */
    public void showUpdate(VersionInfo info) {
        if (!autoShowGuard) {
            autoShowGuard = true;
            pending = info;
        }
    }

    /**
     * Schedules the dialog to open with {@code info} unconditionally, resetting any
     * previous state. Intended for direct navigation from {@link AboutDialog}.
     */
    public void openWith(VersionInfo info) {
        pending = info;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render() {
        // Consume pending on the GLFW thread so requestOpen() is called safely
        VersionInfo p = pending;
        if (p != null) {
            pending       = null;
            versionInfo   = p;
            downloadState = DownloadState.IDLE;
            downloadedBytes = 0;
            totalBytes      = -1;
            downloadError   = null;
            requestOpen();
        }

        if (versionInfo == null) return;

        if (beginModal("Update Available")) {

            // Version info
            ImGui.text("Current Version: " + BaudBound.VERSION);
            ImGui.text("Latest Version:  " + versionInfo.getLatestVersion());
            ImGui.sameLine();
            ImGui.textColored(1.0f, 0.8f, 0.0f, 1.0f, "(Update Available!)");
            ImGui.text("Build Date: " + BaudBound.BUILD_DATE);

            // Release notes — collapsible, scrollable
            if (!versionInfo.getReleaseNotes().isEmpty()) {
                ImGui.spacing();
                if (ImGui.beginChild("##rn_wrap", ImGui.getContentRegionAvailX(), 0, ImGuiChildFlags.AutoResizeY)) {
                    if (ImGui.collapsingHeader("Release Notes (" + versionInfo.getLatestVersion() + ")")) {
                        ImGui.indent(8);
                        ImGui.spacing();
                        float maxH = ImGui.getIO().getDisplaySizeY() * 0.4f;
                        if (ImGui.beginChild("##rn_scroll", ImGui.getContentRegionAvailX() - 8, maxH)) {
                            renderMarkdown(versionInfo.getReleaseNotes());
                        }
                        ImGui.endChild();
                        ImGui.spacing();
                        ImGui.unindent(8);
                    }
                }
                ImGui.endChild();
            }

            // Download section (only when running from a JAR)
            if (UpdateManager.isRunningFromJar()) {
                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();
                renderDownloadSection();
            }

            endModal();
        }
    }

    // -------------------------------------------------------------------------
    // Download UI
    // -------------------------------------------------------------------------

    /**
     * Renders the download button, progress bar, or error message depending on
     * the current {@link DownloadState}.
     */
    private void renderDownloadSection() {
        float fullWidth = ImGui.getContentRegionAvailX();
        switch (downloadState) {
            case IDLE -> {
                if (ImGui.button("Download & Restart", fullWidth, 0)) {
                    startDownload();
                }
            }
            case DOWNLOADING -> {
                float frac   = totalBytes > 0 ? (float) downloadedBytes / totalBytes : 0f;
                String label = totalBytes > 0
                        ? "%.1f / %.1f MB".formatted(downloadedBytes / 1_048_576f, totalBytes / 1_048_576f)
                        : "Downloading... %.1f MB".formatted(downloadedBytes / 1_048_576f);
                ImGui.progressBar(frac, fullWidth, 0, label);
            }
            case ERROR -> {
                ImGui.textColored(1f, 0.35f, 0.35f, 1f, "Download failed: " + downloadError);
                ImGui.spacing();
                if (ImGui.button("Retry", fullWidth, 0)) {
                    downloadState = DownloadState.IDLE;
                    startDownload();
                }
            }
        }
    }

    /** Initiates the download-and-restart flow via {@link UpdateManager}. */
    private void startDownload() {
        Optional<File> jarFile = UpdateManager.getJarFile();
        if (jarFile.isEmpty()) return;

        downloadState   = DownloadState.DOWNLOADING;
        downloadedBytes = 0;
        totalBytes      = -1;
        downloadError   = null;

        String url = UpdateManager.buildJarDownloadUrl(versionInfo.getReleaseUrl());
        UpdateManager.downloadAndRestart(
                url,
                jarFile.get(),
                (downloaded, total) -> {
                    downloadedBytes = downloaded;
                    totalBytes      = total;
                },
                error -> {
                    downloadError = error;
                    downloadState = DownloadState.ERROR;
                    BaudBound.getLogger().error("Update download failed: " + error);
                }
        );
    }

    // -------------------------------------------------------------------------
    // Markdown renderer
    // -------------------------------------------------------------------------

    private static final Pattern ORDERED_PATTERN = Pattern.compile("^\\d+\\.\\s+");

    /**
     * Renders a GitHub-flavoured Markdown string using ImGui primitives.
     * Supported: headings ({@code #} / {@code ##} / {@code ###}), unordered lists
     * ({@code -} / {@code *} / {@code +}), ordered lists, block quotes ({@code >}),
     * fenced code blocks, and horizontal rules. Inline markers (bold, italic, strikethrough,
     * inline code, links) are stripped from text before display. Lines that cannot be
     * classified fall back to plain wrapped text with markers removed.
     */
    private static void renderMarkdown(String markdown) {
        boolean inCodeBlock = false;

        for (String raw : markdown.split("\n", -1)) {
            // stripTrailing removes \r from \r\n line endings as well as trailing spaces
            String line = raw.stripTrailing();

            // Fenced code block toggle (allow leading spaces before ```)
            if (line.stripLeading().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (inCodeBlock) {
                ImGui.indent(8);
                ImGui.textUnformatted(line);
                ImGui.unindent(8);
                continue;
            }

            // Fully stripped version used for all pattern detection
            String s = line.strip();

            if (s.isEmpty()) { ImGui.spacing(); continue; }

            // Horizontal rule (---, ***, ___)
            if (s.matches("^(-{3,}|\\*{3,}|_{3,})$")) { ImGui.separator(); continue; }

            // Headings
            if (s.startsWith("### ")) { ImGui.separatorText(stripInline(s.substring(4))); continue; }
            if (s.startsWith("## "))  { ImGui.separatorText(stripInline(s.substring(3))); continue; }
            if (s.startsWith("# "))   { ImGui.separatorText(stripInline(s.substring(2))); continue; }

            // Block quote
            if (s.startsWith("> ")) {
                ImGui.beginDisabled();
                ImGui.indent(8);
                ImGui.textUnformatted(stripInline(s.substring(2)));
                ImGui.unindent(8);
                ImGui.endDisabled();
                continue;
            }

            // Unordered list
            if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ ")) {
                ImGui.bullet();
                ImGui.sameLine();
                ImGui.textWrapped(pf(stripInline(s.substring(2))));
                continue;
            }

            // Ordered list
            Matcher m = ORDERED_PATTERN.matcher(s);
            if (m.find()) {
                String prefix = m.group();
                ImGui.textUnformatted(prefix.strip());
                ImGui.sameLine();
                ImGui.textWrapped(pf(stripInline(s.substring(prefix.length()))));
                continue;
            }

            // Regular paragraph
            ImGui.textWrapped(pf(stripInline(s)));
        }
    }

    /**
     * Strips common inline Markdown markers: bold ({@code **} / {@code __}),
     * italic ({@code *} / {@code _}), strikethrough ({@code ~~}), inline code ({@code `}),
     * and links ({@code [text](url)} → {@code text}).
     */
    private static String stripInline(String text) {
        return text
                .replaceAll("\\*\\*([^*\n]+)\\*\\*", "$1")   // **bold**
                .replaceAll("__([^_\n]+)__", "$1")             // __bold__
                .replaceAll("\\*([^*\n]+)\\*", "$1")           // *italic*
                .replaceAll("_([^_\n]+)_", "$1")               // _italic_
                .replaceAll("~~([^~\n]+)~~", "$1")             // ~~strikethrough~~
                .replaceAll("`([^`\n]+)`", "$1")               // `inline code`
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");   // [text](url)
    }

    /**
     * Escapes {@code %} so the string is safe for ImGui calls that internally
     * use C {@code printf}-style formatting (e.g. {@code TextWrapped}).
     */
    private static String pf(String text) {
        return text.replace("%", "%%");
    }
}