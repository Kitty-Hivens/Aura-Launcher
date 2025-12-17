package hivens.core.api;

import hivens.core.api.model.ServerProfile; // Добавляем импорт
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.OptionalMod;

import java.util.List;
import java.util.Map;

public interface IManifestProcessorService {

    FileManifest processManifest(String version);

    /**
     * Рекурсивно "выпрямляет" (flattens) древовидный манифест файлов.
     * @return Плоская карта путь -> данные.
     */
    Map<String, FileData> flattenManifest(FileManifest manifest);

    /**
     * Возвращает список опциональных модификаций для КОНКРЕТНОГО профиля.
     * [CHANGE] Теперь принимает ServerProfile, так как моды привязаны к серверу.
     */
    List<OptionalMod> getOptionalModsForClient(ServerProfile profile);

}
