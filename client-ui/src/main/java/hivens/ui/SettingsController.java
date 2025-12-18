package hivens.ui;

import hivens.core.data.SettingsData;
import hivens.launcher.LauncherDI;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import java.io.File;

public class SettingsController {

    private final LauncherDI di;
    private final SettingsData settings;
    private final Main mainApp;

    @FXML private TextField javaPathField;
    @FXML private TextField memoryField;
    @FXML private CheckBox closeAfterStart;
    @FXML private ComboBox<String> themeSelector;

    // [FIX] Теперь принимаем Main
    public SettingsController(LauncherDI di, Main mainApp) {
        this.di = di;
        this.mainApp = mainApp;
        this.settings = di.getSettingsService().getSettings();
    }

    @FXML
    public void initialize() {
        if (settings.getJavaPath() != null) javaPathField.setText(settings.getJavaPath());
        memoryField.setText(String.valueOf(settings.getMemoryMB()));
        closeAfterStart.setSelected(settings.isCloseAfterStart());

        if (themeSelector != null) {
            themeSelector.getItems().addAll("Warm", "Ice", "Dark");
            themeSelector.setValue(settings.getTheme());

            // [FIX] ЖИВОЙ ПРЕДПРОСМОТР (LIVE PREVIEW)
            themeSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    // Создаем временный объект настроек для предпросмотра
                    SettingsData preview = new SettingsData();
                    preview.setTheme(newVal);

                    // 1. Применяем к окну настроек
                    if (javaPathField.getScene() != null) {
                        ThemeManager.applyTheme(javaPathField.getScene(), preview);
                    }

                    // 2. Применяем к ГЛАВНОМУ окну
                    if (mainApp.getPrimaryStage() != null && mainApp.getPrimaryStage().getScene() != null) {
                        ThemeManager.applyTheme(mainApp.getPrimaryStage().getScene(), preview);
                    }
                }
            });
        }
    }

    @FXML
    private void onSave() {
        settings.setJavaPath(javaPathField.getText());
        try {
            settings.setMemoryMB(Integer.parseInt(memoryField.getText()));
        } catch (NumberFormatException e) {
            settings.setMemoryMB(4096);
        }
        settings.setCloseAfterStart(closeAfterStart.isSelected());

        if (themeSelector != null) {
            settings.setTheme(themeSelector.getValue());
        }

        di.getSettingsService().saveSettings(settings);
        onClose();
    }

    @FXML
    private void onBrowseJava() {
        DirectoryChooser dc = new DirectoryChooser();
        File f = dc.showDialog(null);
        if (f != null) javaPathField.setText(f.getAbsolutePath() + "/bin/java");
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) javaPathField.getScene().getWindow();
        stage.close();
    }
}
