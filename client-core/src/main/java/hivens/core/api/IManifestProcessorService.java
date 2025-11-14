package hivens.core.api;

import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import java.util.Map;

/**
 * Контракт для сервиса, преобразующего древовидный FileManifest
 * в плоскую карту (Map) для служб целостности и загрузки.
 */
public interface IManifestProcessorService {

    /**
     * Рекурсивно "выпрямляет" (flattens) древовидный манифест файлов.
     *
     * @param manifest Корневой объект FileManifest, полученный из SessionData.
     * @return Плоская карта (Map), где Ключ - относительный путь
     * (например, "mods/mod.jar"), а Значение - FileData (md5, size).
     */
    Map<String, FileData> flattenManifest(FileManifest manifest);
}