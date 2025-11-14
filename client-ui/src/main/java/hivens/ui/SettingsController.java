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
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    @FXML private TextField javaPathField;
    @FXML private TextField memoryField;
    @FXML private TextField themePathField;

    private final ISettingsService settingsService;
    private final Main mainApp; // (Добавлено)
    private SettingsData currentSettings;

    // (Конструктор обновлен)
    public SettingsController(LauncherDI di, Main mainApp) {
        this.settingsService = di.getSettingsService();
        this.mainApp = mainApp;
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
        }
    }

    @FXML
    private void onSaveClick() {
        try {
            SettingsData newSettings = new SettingsData(
                    javaPathField.getText(),
                    Integer.parseInt(memoryField.getText()),
                    themePathField.getText()
            );
            settingsService.saveSettings(newSettings);
            this.currentSettings = newSettings;

            mainApp.showLoginScene(); // (Возврат к главному экрану)

        } catch (IOException e) {
            log.error("Failed to save settings", e);
        } catch (NumberFormatException e) {
            log.warn("Invalid memory value entered");
        }
    }

    @FXML
    private void onCancelClick() {
        try {
            mainApp.showLoginScene(); // (Возврат к главному экрану)
        } catch (IOException e) {
            log.error("Failed to return to Login scene", e);
        }
    }
}