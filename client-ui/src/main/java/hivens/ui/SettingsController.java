package hivens.ui;

import hivens.core.api.ISettingsService;
import hivens.core.data.SettingsData;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final ISettingsService settingsService;
    private final Main mainApp;
    private SettingsData currentSettings;

    public SettingsController(LauncherDI di, Main mainApp) {
        this.settingsService = di.getSettingsService();
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        saveButton.setOnAction(event -> onSaveClick());
        cancelButton.setOnAction(event -> onCancelClick());

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

    private void onSaveClick() {
        try {
            int memory;
            try {
                memory = Integer.parseInt(memoryField.getText());
                // Устанавливаем разумный минимум (512MB)
                if (memory < 512) {
                    log.warn("Invalid memory value entered: {}MB. Setting to 512.", memory);
                    memory = 512;
                    memoryField.setText("512");
                }
                // (Можно добавить и максимум, e.g., 32768)
            } catch (NumberFormatException e) {
                log.warn("Invalid memory value entered (Not a number). Using default.", e);
                memory = SettingsData.defaults().memoryMB(); // 4096
                memoryField.setText(String.valueOf(memory));
            }

            SettingsData newSettings = new SettingsData(
                    javaPathField.getText(),
                    memory, // Используем проверенное значение
                    themePathField.getText()
            );
            settingsService.saveSettings(newSettings);
            this.currentSettings = newSettings;

            mainApp.showLoginScene(); // (Возврат к главному экрану)

        } catch (IOException e) {
            log.error("Failed to save settings", e);
        }
    }

    private void onCancelClick() {
        try {
            mainApp.showLoginScene(); // (Возврат к главному экрану)
        } catch (IOException e) {
            log.error("Failed to return to Login scene", e);
        }
    }
}