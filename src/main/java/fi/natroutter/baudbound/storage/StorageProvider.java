package fi.natroutter.baudbound.storage;

import fi.natroutter.foxlib.files.FileManager;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;

import java.io.File;
import java.nio.file.Path;

public class StorageProvider {

    private FoxLogger logger = BaudBound.getLogger();
    private FileManager manager;

    private DataStore data;

    public StorageProvider() {
        File storageFile = Path.of(System.getProperty("user.dir"), "storage.json").toFile();

        manager = new FileManager.Builder(storageFile)
                .setExportResource(true)
                .setLogger(logger)
                .setResourceFile("storage.json")
                .onFileCreation(()->{
                   logger.info("Storage file initialized");
                })
                .onInitialized(e-> {
                    if (e.success() && !e.content().isEmpty()) {
                        data = DataStore.fromJson(e.content());
                    }
                })
                .build();
    }

    public DataStore getData() {
        if (data == null) {
            logger.error("Datastore can not be accessed before its initialized!");
            System.exit(0);
        }
        return data;
    }


    public void reload() {
        logger.info("Datastore Reloaded!");
        manager.reload();
    }

    public void save() {
        if (data != null) {
            logger.info("Datastore Saved!");
            manager.save(data.toJson());
        }
    }

}
