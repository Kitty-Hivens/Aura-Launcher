package hivens.ui;

import hivens.core.data.SettingsData;
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

    @FXML private TextField javaPathField;
    @FXML private TextField memoryField;
    @FXML private CheckBox closeAfterStart;
    @FXML private ComboBox<String> themeSelector;

    public SettingsController(LauncherDI di) {
        this.di = di;
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
        if (themeSelector != null) settings.setTheme(themeSelector.getValue());

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
        // Получаем Stage через любой элемент сцены
        Stage stage = (Stage) javaPathField.getScene().getWindow();
        stage.close();
    }
}
