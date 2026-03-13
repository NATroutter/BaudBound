package fi.natroutter.baudbound.storage;

import fi.natroutter.foxlib.files.FileManager;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;

import java.io.File;
import java.nio.file.Path;

/**
 * Manages loading and saving the application's {@link DataStore} to {@code storage.json}
 * in the working directory.
 * <p>
 * On construction the file is read and parsed; if absent a default-populated file is
 * written via the embedded resource template. Call {@link #save()} after any mutation
 * to {@code DataStore} to persist the change.
 */
public class StorageProvider {

    private final FoxLogger logger = BaudBound.getLogger();
    private final FileManager manager;

    private DataStore data;

    public StorageProvider() {
        File storageFile = Path.of(System.getProperty("user.dir"), "storage.json").toFile();

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

    /** Persists the current {@link DataStore} state to disk. No-op if data is null. */
    public void save() {
        if (data != null) {
            logger.info("Datastore saved");
            manager.save(data.toJson());
        }
    }
}