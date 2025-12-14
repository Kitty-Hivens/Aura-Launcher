package hivens.utils;

import hivens.launcher.SettingsService;
import java.io.File;
import java.nio.file.Paths;

/**
 * Утилита для поиска и валидации Java Runtime Environment (JRE).
 * Помогает найти правильный исполняемый файл java/javaw для запуска клиента.
 */
public class JavaHelper {

    /**
     * Возвращает оптимальный путь к исполняемому файлу Java.
     * <p>
     * Порядок поиска:
     * 1. Путь, заданный пользователем в настройках (если валиден).
     * 2. Переменная окружения JAVA_HOME.
     * 3. Системная команда из PATH (java/javaw).
     * </p>
     *
     * @return Абсолютный путь к файлу или имя команды для PATH.
     */
    public static String getJavaPath() {
        // 1. Проверяем настройку пользователя
        String userPath = SettingsService.getGlobal().javaPath;
        if (isValid(userPath)) {
            return userPath;
        }

        // 2. Проверяем JAVA_HOME
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isEmpty()) {
            // Пробуем найти бинарник в JAVA_HOME/bin
            String binPath = Paths.get(javaHome, "bin", getExecutableName()).toString();
            if (isValid(binPath)) {
                return binPath;
            }
        }

        // 3. Возвращаем дефолтную команду для запуска из PATH
        return getExecutableName();
    }

    /**
     * Определяет имя исполняемого файла в зависимости от ОС.
     * * @return "javaw.exe" для Windows (чтобы не было лишней консоли), "java" для Unix.
     */
    private static String getExecutableName() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "javaw.exe" : "java";
    }

    /**
     * Проверяет, существует ли файл по заданному пути.
     */
    private static boolean isValid(String path) {
        return path != null && !path.isEmpty() && new File(path).exists() && new File(path).isFile();
    }
}
