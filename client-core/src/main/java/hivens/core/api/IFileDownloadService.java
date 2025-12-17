package hivens.core.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Контракт для сервиса загрузки файлов клиента.
 * Отвечает за загрузку файлов, отсутствующих или не прошедших проверку целостности.
 */
public interface IFileDownloadService {

    /**
     * Загружает один файл из удаленного источника (CDN) в локальный файл.
     *
     * @param relativePath Относительный путь к файлу (например, "mods/mod.jar").
     * @param destinationPath Полный путь к месту сохранения файла.
     * @throws IOException в случае сетевых ошибок или ошибок I/O при записи.
     */
    void downloadFile(String relativePath, Path destinationPath) throws IOException;

}
