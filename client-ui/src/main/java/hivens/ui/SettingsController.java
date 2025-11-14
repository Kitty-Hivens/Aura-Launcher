package hivens.ui;

import hivens.core.api.ISettingsService;
import hivens.core.data.SettingsData;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Контроллер для Settings.fxml.
 * (Аналог ae.class).
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    @FXML private TextField javaPathField;
    @FXML private TextField memoryField;
    @FXML private TextField themePathField;

    private final ISettingsService settingsService;
    private SettingsData currentSettings;

    public SettingsController(LauncherDI di) {
        this.settingsService = di.getSettingsService();
    }

    @FXML
    public void initialize() {
        loadSettings();
    }

    private void loadSettings() {
        try {
            this.currentSettings = settingsService.loadSettings();
            javaPathField.setText(currentSettings.javaPath());
            memoryField.setText(String.valueOf(currentSettings.memoryMB()));
            themePathField.setText(currentSettings.customThemePath());
        } catch (IOException e) {
            log.error("Failed to load settings", e);
            // (Показать ошибку в UI)
        }
    }

    @FXML
    private void onSaveClick() {
        try {
            SettingsData newSettings = new SettingsData(
                javaPathField.getText(),
                Integer.parseInt(memoryField.getText()), // (Требуется валидация)
                themePathField.getText()
            );
            settingsService.saveSettings(newSettings);
            this.currentSettings = newSettings;
            // (Показать "Сохранено" в UI)
            
            // TODO: Вернуться к главному экрану (вызвать метод в Main)
            
        } catch (IOException e) {
            log.error("Failed to save settings", e);
        } catch (NumberFormatException e) {
            log.warn("Invalid memory value entered");
        }
    }

    @FXML
    private void onCancelClick() {
        // TODO: Вернуться к главному экрану (вызвать метод в Main)
    }
}