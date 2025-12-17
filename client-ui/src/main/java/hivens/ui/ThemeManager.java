package hivens.ui;

import hivens.core.data.SettingsData;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    // Путь к базовому CSS (геометрия, шрифты)
    private static final String BASE_CSS = "/fxml/aura-base.css";

    // Папка для пользовательских тем (на будущее)
    private static final String CUSTOM_THEMES_DIR = System.getProperty("user.home") + "/.aura/themes/";

    /**
     * Перечисление встроенных тем.
     * Хранит название файла, чтобы не хардкодить строки в логике.
     */
    public enum Theme {
        WARM("Warm", "theme-warm.css"),
        ICE("Ice", "theme-ice.css"),
        DARK("Dark", "theme-dark.css"),
        CUSTOM("Custom", null); // Задел на будущее

        public final String displayName;
        public final String fileName;

        Theme(String displayName, String fileName) {
            this.displayName = displayName;
            this.fileName = fileName;
        }

        // Поиск темы по названию (из настроек)
        public static Theme fromName(String name) {
            for (Theme t : values()) {
                if (t.displayName.equalsIgnoreCase(name)) return t;
            }
            return WARM; // Дефолтная тема
        }
    }

    /**
     * Применяет тему на основе настроек.
     */
    public static void applyTheme(Scene scene, SettingsData settings) {
        if (scene == null) return;

        String themeName = (settings != null && settings.getTheme() != null)
                ? settings.getTheme()
                : Theme.WARM.displayName;

        applyTheme(scene, themeName);
    }

    /**
     * Прямой метод применения (если нужно сменить тему на лету без SettingsData).
     */
    public static void applyTheme(Scene scene, String themeName) {
        if (scene == null) return;

        // 1. Очищаем старые стили
        scene.getStylesheets().clear();

        // 2. Сначала всегда грузим базу (шрифты, отступы)
        addResourceCss(scene, BASE_CSS);

        // 3. Определяем, какую тему грузить
        Theme builtIn = Theme.fromName(themeName);

        // Логика поиска CSS файла
        String cssUrl = null;

        // А) Проверяем, может это пользовательский файл (Future-proof)
        // Если имя темы заканчивается на .css или это полный путь
        if (themeName.endsWith(".css")) {
            cssUrl = getCustomThemeUrl(themeName);
        }

        // Б) Если не кастомная, берем из Enum (Встроенная)
        if (cssUrl == null) {
            cssUrl = getResourceUrl("/fxml/" + builtIn.fileName);
        }

        // 4. Применяем
        if (cssUrl != null) {
            log.info("Applying theme: {} ({})", themeName, cssUrl);
            scene.getStylesheets().add(cssUrl);
        } else {
            log.error("Theme not found: {}", themeName);
            // Фолбэк на Warm, чтобы интерфейс не был "голым"
            addResourceCss(scene, "/fxml/" + Theme.WARM.fileName);
        }
    }

    // --- Вспомогательные методы ---

    private static void addResourceCss(Scene scene, String resourcePath) {
        String url = getResourceUrl(resourcePath);
        if (url != null) {
            scene.getStylesheets().add(url);
        } else {
            log.error("Base CSS not found: {}", resourcePath);
        }
    }

    private static String getResourceUrl(String path) {
        URL url = ThemeManager.class.getResource(path);
        return (url != null) ? url.toExternalForm() : null;
    }

    /**
     * Умный поиск кастомных тем.
     * Ищет файл в папке .aura/themes/
     */
    private static String getCustomThemeUrl(String filename) {
        try {
            Path customPath = Paths.get(CUSTOM_THEMES_DIR, filename);
            if (Files.exists(customPath)) {
                return customPath.toUri().toURL().toExternalForm();
            }
        } catch (MalformedURLException e) {
            log.error("Invalid custom theme path", e);
        }
        return null;
    }
}
