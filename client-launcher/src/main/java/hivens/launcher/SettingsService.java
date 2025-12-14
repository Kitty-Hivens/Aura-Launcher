package hivens.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import hivens.core.api.ISettingsService;
import hivens.core.data.SettingsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Реализация сервиса настроек.
 * Читает и сохраняет SettingsData в .json файл.
 */
public class SettingsService implements ISettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final Gson gson;
    private final Path settingsFilePath;

    /**
     * Конструктор для внедрения зависимостей.
     * @param gson Экземпляр Gson (из DI).
     * @param settingsFilePath Путь к файлу настроек (из DI).
     */
    public SettingsService(Gson gson, Path settingsFilePath) {
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
        this.settingsFilePath = Objects.requireNonNull(settingsFilePath, "Settings path cannot be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SettingsData loadSettings() throws IOException {
        try (Reader reader = Files.newBufferedReader(settingsFilePath)) {
            
            SettingsData settings = gson.fromJson(reader, SettingsData.class);
            log.info("Settings loaded from {}", settingsFilePath);
            
            // Проверка на случай, если файл пустой или поврежден
            if (settings == null) {
                log.warn("Settings file is empty or corrupt. Returning defaults.");
                return SettingsData.defaults();
            }
            return settings;

        } catch (NoSuchFileException e) {
            // Файл не найден - это нормально при первом запуске
            log.info("No settings file found at {}. Creating default settings.", settingsFilePath);
            SettingsData defaults = SettingsData.defaults();
            saveSettings(defaults); // Сохраняем настройки по умолчанию
            return defaults;
        } catch (JsonSyntaxException | JsonIOException e) {
            // Файл поврежден
            log.error("Failed to parse settings.json. Returning defaults.", e);
            return SettingsData.defaults();
        }
        // (IOException пробрасывается выше)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveSettings(SettingsData settings) throws IOException {
        Objects.requireNonNull(settings, "SettingsData cannot be null");

        // Убедимся, что директория существует
        Path parentDir = settingsFilePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (Writer writer = Files.newBufferedWriter(settingsFilePath)) {
            gson.toJson(settings, writer);
            log.info("Settings saved to {}", settingsFilePath);
        }
        // (IOException пробрасывается выше)
    }
}