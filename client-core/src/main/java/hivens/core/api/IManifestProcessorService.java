package hivens.core.api;

import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.OptionalMod;

import java.util.List;
import java.util.Map;

public interface IManifestProcessorService {

    /**
     * Рекурсивно "выпрямляет" (flattens) древовидный манифест файлов.
     * @return Плоская карта путь -> данные.
     */
    Map<String, FileData> flattenManifest(FileManifest manifest);

    FileManifest processManifest(String version);

    /**
     * Возвращает список опциональных модификаций (из манифеста или конфига).
     */
    List<OptionalMod> getOptionalModsForClient(String version);
}