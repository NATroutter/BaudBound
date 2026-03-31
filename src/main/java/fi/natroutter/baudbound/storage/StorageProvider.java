package fi.natroutter.baudbound.storage;

import fi.natroutter.foxlib.files.FileManager;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Manages loading and saving the application's {@link DataStore} to {@code storage.json}
 * in the platform-appropriate user config directory.
 * <p>
 * The config directory is resolved via {@link #getConfigDir()} so the path is stable
 * regardless of the working directory at launch (e.g. autostart on Linux). On construction
 * the file is read and parsed; if absent a default-populated file is written via the embedded
 * resource template. The bundled {@code icon.png} is also exported to the config directory
 * so external tools (e.g. Linux autostart {@code .desktop} files) can reference it by path.
 * <p>
 * Call {@link #save()} after any mutation to {@code DataStore} to persist the change.
 */
public class StorageProvider {

    private final FoxLogger logger = BaudBound.getLogger();
    private final FileManager manager;

    private DataStore data;

    public StorageProvider() {
        File configDir = getConfigDir();
        if (!configDir.exists() && !configDir.mkdirs()) {
            logger.warn("Could not create config directory: " + configDir.getAbsolutePath());
        }

        exportIcon(configDir);

        File storageFile = new File(configDir, "storage.json");
        manager = new FileManager.Builder(storageFile)
                .setExportResource(true)
                .setLogger(logger)
                .setResourceFile("storage.json")
                .onFileCreation(() -> logger.info("Storage file initialized"))
                .onInitialized(e -> {
                    if (e.success() && !e.content().isEmpty()) {
                        data = DataStore.fromJson(e.content());
                    }
                })
                .build();
    }

    /**
     * Returns the loaded {@link DataStore}.
     *
     * @throws IllegalStateException if called before the store has been initialized
     */
    public DataStore getData() {
        if (data == null) {
            throw new IllegalStateException("DataStore accessed before initialization");
        }
        return data;
    }

    /**
     * Returns the platform-appropriate config directory for BaudBound, using
     * {@code user.home} so the path is stable regardless of the working directory.
     * <ul>
     *   <li>Windows — {@code %APPDATA%\BaudBound}</li>
     *   <li>macOS   — {@code ~/Library/Application Support/BaudBound}</li>
     *   <li>Linux   — {@code $XDG_CONFIG_HOME/BaudBound} or {@code ~/.config/BaudBound}</li>
     * </ul>
     */
    public static File getConfigDir() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        String base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = (appData != null && !appData.isEmpty()) ? appData : home;
        } else if (os.contains("mac")) {
            base = home + "/Library/Application Support";
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = (xdg != null && !xdg.isEmpty()) ? xdg : home + "/.config";
        }
        return new File(base, BaudBound.APP_NAME);
    }

    /** Persists the current {@link DataStore} state to disk. No-op if data is null. */
    public void save() {
        if (data != null) {
            logger.info("Datastore saved");
            manager.save(data.toJson());
        }
    }

    /**
     * Copies {@code icon.png} from the JAR resources to {@code configDir/icon.png} if it
     * is not already present. This allows external tools (e.g. Linux {@code .desktop} files)
     * to reference the icon by an absolute path on disk.
     */
    private void exportIcon(File configDir) {
        File dest = new File(configDir, "icon.png");
        if (dest.exists()) return;
        try (InputStream is = BaudBound.class.getResourceAsStream("/icon.png")) {
            if (is == null) return;
            Files.copy(is, dest.toPath());
        } catch (Exception e) {
            logger.warn("Could not export icon.png to config dir: " + e.getMessage());
        }
    }
}