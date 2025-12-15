package hivens.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

/**
 * Глобальные настройки лаунчера.
 * (Используем Class вместо Record, чтобы можно было менять поля через сеттеры в UI)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettingsData {

    // --- Система ---
    private String javaPath;         // Путь к Java
    private int memoryMB = 4096;     // Память (по умолчанию 4GB)

    // --- Визуал ---
    private String theme = "Warm";   // Тема: "Ice", "Warm", "Dark"

    // --- Поведение ---
    private boolean closeAfterStart = true;
    private boolean saveCredentials = true;

    /**
     * Создает дефолтные настройки
     */
    public static SettingsData defaults() {
        SettingsData data = new SettingsData();
        // Пытаемся угадать системную Java
        data.setJavaPath(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        data.setMemoryMB(4096);
        data.setTheme("Warm");
        return data;
    }
}