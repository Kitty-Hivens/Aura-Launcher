package hivens.ui;

import hivens.core.data.SettingsData;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class SettingsController {

    private final LauncherDI di;
    private final Main mainApp;
    private final SettingsData settings;
    public StackPane root;

    @FXML private TextField javaPathField;
    @FXML private TextField memoryField;
    @FXML private ComboBox<String> themeSelector; // Выбор темы
    @FXML private CheckBox closeAfterStart;

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

        // Темы
        themeSelector.getItems().addAll("Ice", "Warm", "Dark");
        themeSelector.setValue(settings.getTheme() != null ? settings.getTheme() : "Warm");

        // Живое переключение темы
        themeSelector.setOnAction(e -> {
            settings.setTheme(themeSelector.getValue());
            if (javaPathField.getScene() != null) {
                ThemeManager.applyTheme(javaPathField.getScene(), settings);
            }
        });
    }

    @FXML
    private void onSave() throws IOException {
        settings.setJavaPath(javaPathField.getText());
        try {
            settings.setMemoryMB(Integer.parseInt(memoryField.getText()));
        } catch (NumberFormatException e) {
            settings.setMemoryMB(4096);
        }
        settings.setCloseAfterStart(closeAfterStart.isSelected());
        settings.setTheme(themeSelector.getValue());

        di.getSettingsService().saveSettings(settings);
        onClose();
    }

    @FXML
    private void onBrowseJava() {
        DirectoryChooser dc = new DirectoryChooser();
        File f = dc.showDialog(javaPathField.getScene().getWindow());
        if (f != null) javaPathField.setText(f.getAbsolutePath() + "/bin/java");
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) javaPathField.getScene().getWindow();
        stage.close();
    }
}
