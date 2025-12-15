package hivens.launcher;

import com.google.gson.Gson;
import hivens.core.api.ISettingsService;
import hivens.core.data.SettingsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsService implements ISettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final Gson gson;
    private final Path settingsFile;
    private SettingsData cachedSettings; // Кэш в памяти

    public SettingsService(Gson gson, Path settingsFile) {
        this.gson = gson;
        this.settingsFile = settingsFile;
        // Загружаем сразу при старте
        reload();
    }

    @Override
    public SettingsData getSettings() {
        if (cachedSettings == null) {
            reload();
        }
        return cachedSettings;
    }

    @Override
    public void saveSettings(SettingsData settings) {
        this.cachedSettings = settings; // Обновляем кэш
        try {
            if (settingsFile.getParent() != null) {
                Files.createDirectories(settingsFile.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(settingsFile)) {
                gson.toJson(settings, writer);
            }
        } catch (IOException e) {
            log.error("Failed to save settings", e);
        }
    }

    private void reload() {
        if (!Files.exists(settingsFile)) {
            cachedSettings = SettingsData.defaults();
            return;
        }
        try (Reader reader = Files.newBufferedReader(settingsFile)) {
            cachedSettings = gson.fromJson(reader, SettingsData.class);
            if (cachedSettings == null) cachedSettings = SettingsData.defaults();
        } catch (IOException e) {
            log.error("Failed to load settings, using defaults", e);
            cachedSettings = SettingsData.defaults();
        }
    }
}
