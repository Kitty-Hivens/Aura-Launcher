package hivens.launcher;

import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Реализация сервиса обработки манифеста.
 * Отвечает за преобразование древовидного FileManifest в плоскую карту.
 */
public class ManifestProcessorService implements IManifestProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ManifestProcessorService.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, FileData> flattenManifest(FileManifest manifest) {
        if (manifest == null) {
            log.warn("Cannot flatten null manifest.");
            return Collections.emptyMap();
        }

        Map<String, FileData> flatMap = new HashMap<>();
        // Запускаем рекурсивный обход, начиная с корневого пути ""
        processDirectory(manifest, "", flatMap);
        
        log.debug("Manifest flattened. Total files: {}", flatMap.size());
        return flatMap;
    }

    /**
     * Рекурсивный вспомогательный метод для обхода FileManifest.
     *
     * @param manifest Текущий узел манифеста.
     * @param currentPath Относительный путь к этому узлу (e.g., "mods" или "config/sub").
     * @param flatMap Карта для сбора результатов (относительный путь -> FileData).
     */
    private void processDirectory(FileManifest manifest, String currentPath, Map<String, FileData> flatMap) {
        Objects.requireNonNull(currentPath, "Current path cannot be null");
        Objects.requireNonNull(flatMap, "Map cannot be null");

        if (manifest == null) {
            return;
        }

        // 1. Обработка файлов в текущей директории
        if (manifest.files() != null) {
            for (Map.Entry<String, FileData> fileEntry : manifest.files().entrySet()) {
                String fileName = fileEntry.getKey();
                FileData fileData = fileEntry.getValue();
                
                // (Используем '/' как стандартный разделитель для путей в манифесте)
                String relativePath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;
                
                flatMap.put(relativePath, fileData);
            }
        }

        // 2. Рекурсивная обработка поддиректорий
        if (manifest.directories() != null) {
            for (Map.Entry<String, FileManifest> dirEntry : manifest.directories().entrySet()) {
                String dirName = dirEntry.getKey();
                FileManifest subManifest = dirEntry.getValue();
                
                String subDirectoryPath = currentPath.isEmpty() ? dirName : currentPath + "/" + dirName;
                
                // Рекурсивный вызов
                processDirectory(subManifest, subDirectoryPath, flatMap);
            }
        }
    }
}