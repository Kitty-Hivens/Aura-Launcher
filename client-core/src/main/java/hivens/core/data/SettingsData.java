package hivens.core.data;

import java.io.File;

/**
 * Модель данных (DTO) для хранения настроек лаунчера.
 * (Будет сериализован в .json файл).
 */
public record SettingsData(
    
    /** Путь к исполняемому файлу Java (e.g., /usr/bin/java). */
    String javaPath,

    /** Выделенная память в МБ (e.g., 4096). */
    int memoryMB,

    /** (Для Issue #11) Путь к кастомному CSS-файлу темы (может быть null). */
    String customThemePath
) {
    /**
     * Предоставляет настройки по умолчанию.
     */
    public static SettingsData defaults() {
        return new SettingsData(
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java", // Попытка авто-определения
            4096, // 4GB по умолчанию
            null
        );
    }
}