package hivens.ui;

import hivens.core.data.InstanceProfile;
import hivens.launcher.ProfileManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;

public class ServerSettingsController {

    @FXML private Label titleLabel;
    @FXML private Slider ramSlider;
    @FXML private Label ramValueLabel;
    @FXML private TextField javaPathField;
    @FXML private TextField jvmArgsField;
    @FXML private VBox modsContainer;
    @FXML private StackPane root;

    private InstanceProfile currentProfile;
    private ProfileManager profileManager;
    private Stage stage;

    public void initialize() {
        // Логика слайдера памяти: обновляем текст при движении
        ramSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            ramValueLabel.setText(newVal.intValue() + " MB");
        });
    }

    /**
     * Метод инициализации данными (вызывается из AuraMainController)
     */
    public void setup(InstanceProfile profile, ProfileManager manager, Stage stage) {
        this.currentProfile = profile;
        this.profileManager = manager;
        this.stage = stage;

        // 1. Заполняем UI данными из профиля
        titleLabel.setText("НАСТРОЙКИ: " + profile.getServerId().toUpperCase());
        
        // Память (если 0 или null - ставим дефолт 2048)
        int ram = (profile.getMemoryMb() > 0) ? profile.getMemoryMb() : 2048;
        ramSlider.setValue(ram);
        ramValueLabel.setText(ram + " MB");

        // Java и аргументы
        javaPathField.setText(profile.getJavaPath() != null ? profile.getJavaPath() : "");
        jvmArgsField.setText(profile.getJvmArgs() != null ? profile.getJvmArgs() : "");

        // 2. Генерируем список модов (пока заглушка, позже подключим ModManifest)
        generateModList();
    }

    private void generateModList() {
        modsContainer.getChildren().clear();
        
        // В будущем тут будет цикл по manifest.groups
        // А пока пример ручного добавления:
        
        addCheckbox("Шейдеры (Iris + Sodium)", "graphics_shaders");
        addCheckbox("Мини-карта (Xaero's)", "mod_minimap");
        addCheckbox("Waila (Подсказки)", "mod_waila");
    }

    private void addCheckbox(String title, String modId) {
        CheckBox cb = new CheckBox(title);
        cb.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        boolean isSelected = currentProfile.getSelectedMods().containsKey(modId);
        cb.setSelected(isSelected);

        // Слушатель: меняем мапу сразу при клике (или можно при сохранении)
        cb.selectedProperty().addListener((obs, old, val) -> {
            if (val) {
                currentProfile.getSelectedMods().put(modId, "enabled");
            } else {
                currentProfile.getSelectedMods().remove(modId);
            }
        });

        modsContainer.getChildren().add(cb);
    }

    @FXML
    private void browseJava() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку с Java (JDK/JRE)");
        File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected != null) {
            javaPathField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void save() {
        currentProfile.setMemoryMb((int) ramSlider.getValue());
        
        String path = javaPathField.getText().trim();
        currentProfile.setJavaPath(path.isEmpty() ? null : path);
        
        String args = jvmArgsField.getText().trim();
        currentProfile.setJvmArgs(args.isEmpty() ? null : args);

        profileManager.saveProfile(currentProfile);

        close();
    }

    @FXML
    private void close() {
        if (stage != null) stage.close();
    }
}