package hivens.core.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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

    /**
     * Загружает все файлы из предоставленной карты (обычно из IFileIntegrityService).
     * Реализация должна обеспечивать создание всех необходимых директорий.
     *
     * @param basePath Корневая директория клиента (например, /home/user/.smarty).
     * @param filesToDownload Карта (относительный путь -> хэш) файлов для загрузки.
     * @param progressConsumer (Опционально) Callback для отслеживания прогресса (например, обновление UI).
     * @return Количество успешно загруженных файлов.
     * @throws IOException в случае, если один из файлов не удалось загрузить (может быть заменено на сбор ошибок).
     */
    int downloadMissingFiles(Path basePath, String serverId, Map<String, String> filesToDownload, Consumer<String> progressConsumer) throws IOException;
}