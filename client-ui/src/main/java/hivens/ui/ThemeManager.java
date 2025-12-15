package hivens.ui;

import hivens.core.data.SettingsData;
import javafx.scene.Scene;
import java.net.URL;

public class ThemeManager {

    public static void applyTheme(Scene scene, SettingsData settings) {
        if (scene == null) return;

        scene.getStylesheets().clear();

        // 1. Выбираем файл цветов
        String themeName = (settings.getTheme() != null) ? settings.getTheme() : "Warm";
        String cssFile = switch (themeName) {
            case "Ice" -> "theme-ice.css";
            case "Dark" -> "theme-dark.css";
            default -> "theme-warm.css";
        };

        // 2. Подключаем цвета
        addCss(scene, cssFile);

        // 3. Подключаем базовую структуру (шрифты, отступы)
        addCss(scene, "aura-base.css");
        
        // 4. Подключаем специфичные стили контролов (если они не в aura-base)
        addCss(scene, "aura-theme.css"); 
    }

    private static void addCss(Scene scene, String name) {
        URL resource = ThemeManager.class.getResource("/fxml/" + name);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        } else {
            System.err.println("ThemeManager: CSS file not found: " + name);
        }
    }
}