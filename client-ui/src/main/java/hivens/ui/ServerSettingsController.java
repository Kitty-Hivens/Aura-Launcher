package hivens.ui;

import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import hivens.launcher.ProfileManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSettingsController {

    @FXML private Label titleLabel;
    @FXML private Slider ramSlider;
    @FXML private Label ramValueLabel;
    @FXML private TextField javaPathField;
    @FXML private TextField jvmArgsField;
    @FXML private VBox modsContainer;

    private InstanceProfile profile;
    private ProfileManager profileManager;
    private Stage stage;

    // Храним ссылки на чекбоксы для сохранения
    private final Map<String, CheckBox> modCheckboxes = new HashMap<>();

    public void setup(InstanceProfile profile, ProfileManager manager, List<OptionalMod> mods, Stage stage) {
        this.profile = profile;
        this.profileManager = manager;
        this.stage = stage;

        if (profile != null) {
            titleLabel.setText("НАСТРОЙКИ: " + profile.getServerId().toUpperCase());

            // Память
            int ram = (profile.getMemoryMb() != null && profile.getMemoryMb() > 0) ? profile.getMemoryMb() : 4096;
            ramSlider.setValue(ram);
            ramValueLabel.setText(ram + " MB");

            // Java и аргументы
            javaPathField.setText(profile.getJavaPath() != null ? profile.getJavaPath() : "");
            jvmArgsField.setText(profile.getJvmArgs() != null ? profile.getJvmArgs() : "");

            buildModsList(mods);
        }
    }

    @FXML
    public void initialize() {
        // Обновление лейбла при движении слайдера
        ramSlider.valueProperty().addListener((obs, old, val) -> {
            // Округляем до 512
            int value = (val.intValue() / 512) * 512;
            ramValueLabel.setText(value + " MB");
        });
    }

    private void buildModsList(List<OptionalMod> mods) {
        modsContainer.getChildren().clear();
        modCheckboxes.clear();

        // Получаем текущее состояние модов из профиля
        Map<String, Boolean> userState = profile.getOptionalModsState();

        if (mods.isEmpty()) {
            Label placeholder = new Label("Для этого сервера нет опциональных модов.");
            placeholder.setStyle("-fx-text-fill: #888;");
            modsContainer.getChildren().add(placeholder);
            return;
        }

        for (OptionalMod mod : mods) {
            CheckBox box = new CheckBox(mod.getName() + " (" + mod.getDescription() + ")");
            box.setWrapText(true);
            box.setStyle("-fx-text-fill: #ddd; -fx-font-size: 13px; -fx-padding: 5;");

            // Определяем состояние: если в профиле нет записи, берем дефолтное значение мода
            boolean isActive = userState.getOrDefault(mod.getId(), mod.isDefault());
            box.setSelected(isActive);

            // Если мод обязательный (например), можно задизейблить чекбокс
            // if (mod.isForced()) box.setDisable(true);

            modCheckboxes.put(mod.getId(), box);
            modsContainer.getChildren().add(box);
        }
    }

    @FXML
    private void save() {
        if (profile != null) {
            // 1. Сохраняем системные настройки
            // Округляем RAM
            int ram = (int) ramSlider.getValue();
            ram = (ram / 512) * 512;

            profile.setMemoryMb(ram);

            String javaPath = javaPathField.getText().trim();
            profile.setJavaPath(javaPath.isEmpty() ? null : javaPath);

            String jvmArgs = jvmArgsField.getText().trim();
            profile.setJvmArgs(jvmArgs.isEmpty() ? null : jvmArgs);

            // 2. Сохраняем состояние модов
            Map<String, Boolean> newState = profile.getOptionalModsState();
            modCheckboxes.forEach((modId, box) -> newState.put(modId, box.isSelected()));

            // 3. Пишем на диск
            profileManager.saveProfile(profile);
        }
        close();
    }

    @FXML
    private void close() {
        if (stage != null) stage.close();
    }

    @FXML
    private void browseJava() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Выберите папку с Java (JDK/JRE)");
        File f = dc.showDialog(stage);
        if (f != null) {
            // Пытаемся угадать путь к бинарнику
            File binJava = new File(f, "bin/java.exe"); // Windows
            if (!binJava.exists()) binJava = new File(f, "bin/java"); // Unix

            if (binJava.exists()) {
                javaPathField.setText(binJava.getAbsolutePath());
            } else {
                // Если выбрали саму папку bin или файл java
                if (f.getName().equals("java") || f.getName().equals("java.exe")) {
                    javaPathField.setText(f.getAbsolutePath());
                } else {
                    javaPathField.setText(f.getAbsolutePath()); // Просто путь, пусть LauncherService разбирается
                }
            }
        }
    }
}
