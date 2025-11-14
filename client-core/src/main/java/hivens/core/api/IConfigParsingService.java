package hivens.core.api;

import hivens.core.data.ClientData;
import hivens.core.data.FileData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Контракт для сервиса, отвечающего за парсинг конфигурационных
 * файлов клиента (например, client.json) после их загрузки.
 */
public interface IConfigParsingService {

    /**
     * Загружает и парсит JSON-конфигурацию клиента из указанного файла.
     *
     * @param configPath Абсолютный путь к JSON-файлу конфигурации.
     * @return Объект ClientData, содержащий mainClass, jvmArguments и т.д.
     * @throws IOException в случае ошибок I/O или сбоя парсинга JSON.
     */
    ClientData parseClientConfig(Path configPath) throws IOException;

    /**
     * (Вспомогательный метод) Находит путь к файлу конфигурации
     * на основе манифеста.
     *
     * @param rootPath Корневая директория клиента.
     * @param flatManifest Плоская карта файлов (из IManifestProcessorService).
     * @return Path к файлу конфигурации.
     * @throws IOException если файл конфигурации не найден (например, `client.json` отсутствует).
     */
    Path findConfigPath(Path rootPath, Map<String, FileData> flatManifest) throws IOException;
}