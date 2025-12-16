package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSettingsController {

    private final LauncherDI di;
    private final DashboardController dashboard;
    private ServerProfile serverProfile;
    private InstanceProfile instanceProfile;

    @FXML private Label titleLabel;
    @FXML private Slider ramSlider;
    @FXML private Label ramValueLabel;
    @FXML private TextField javaPathField;
    @FXML private TextField jvmArgsField;
    @FXML private VBox modsContainer;

    private final Map<String, CheckBox> modCheckboxes = new HashMap<>();

    public ServerSettingsController(LauncherDI di, DashboardController dashboard) {
        this.di = di;
        this.dashboard = dashboard;
    }

    public void setServer(ServerProfile server) {
        this.serverProfile = server;
        this.titleLabel.setText("НАСТРОЙКИ: " + server.getName().toUpperCase());

        // Загружаем профиль игрока (локальные настройки)
        this.instanceProfile = di.getProfileManager().getProfile(server.getAssetDir());

        // 1. Настраиваем UI системы
        int ram = (instanceProfile.getMemoryMb() != null && instanceProfile.getMemoryMb() > 0) 
                  ? instanceProfile.getMemoryMb() : 4096;
        ramSlider.setValue(ram);
        ramValueLabel.setText(ram + " MB");

        javaPathField.setText(instanceProfile.getJavaPath() != null ? instanceProfile.getJavaPath() : "");
        jvmArgsField.setText(instanceProfile.getJvmArgs() != null ? instanceProfile.getJvmArgs() : "");

        // 2. Настраиваем список модов
        // Получаем СПИСОК ДОСТУПНЫХ модов из манифеста (ManifestProcessor)
        List<OptionalMod> availableMods = di.getManifestProcessorService()
                .getOptionalModsForClient(server.getVersion());
        
        buildModsList(availableMods);
    }

    @FXML
    public void initialize() {
        ramSlider.valueProperty().addListener((obs, old, val) -> {
            // Округляем до 512
            int value = (val.intValue() / 512) * 512;
            ramValueLabel.setText(value + " MB");
        });
    }

    private void buildModsList(List<OptionalMod> mods) {
        modsContainer.getChildren().clear();
        modCheckboxes.clear();
        
        // Текущее состояние (что игрок выбрал ранее)
        Map<String, Boolean> userState = instanceProfile.getOptionalModsState();

        if (mods.isEmpty()) {
            Label placeholder = new Label("Нет доступных опциональных модов для этой версии.");
            placeholder.setStyle("-fx-text-fill: #888;");
            modsContainer.getChildren().add(placeholder);
            return;
        }

        for (OptionalMod mod : mods) {
            CheckBox box = new CheckBox(mod.getName());
            
            // Если есть описание, добавляем Tooltip
            if (mod.getDescription() != null && !mod.getDescription().isEmpty()) {
                Tooltip tt = new Tooltip(mod.getDescription());
                box.setTooltip(tt);
                // Можно добавить описание прямо в текст чекбокса
                box.setText(mod.getName() + " - " + mod.getDescription());
            }
            
            box.getStyleClass().add("aura-checkbox");
            box.setStyle("-fx-text-fill: #ddd; -fx-font-size: 14px;");

            // Определяем состояние: профиль -> дефолт
            boolean isActive = userState.getOrDefault(mod.getId(), mod.isDefault());
            box.setSelected(isActive);

            modCheckboxes.put(mod.getId(), box);
            modsContainer.getChildren().add(box);
        }
    }

    // --- ДЕЙСТВИЯ ---

    @FXML
    private void autoDetectJava() {
        // [FIX] Используем JavaManagerService
        try {
            Path bestJava = di.getJavaManagerService().getJavaPath(serverProfile.getVersion());
            javaPathField.setText(bestJava.toAbsolutePath().toString());
        } catch (Exception e) {
            // Если не нашли, можно показать алерт
            javaPathField.setText("Не удалось найти подходящую Java :(");
        }
    }

    @FXML
    private void browseJava() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите исполняемый файл Java");
        File f = fc.showOpenDialog(dashboard.getStage()); // Используем геттер окна из Dashboard
        if (f != null) {
            javaPathField.setText(f.getAbsolutePath());
        }
    }

    @FXML
    private void save() {
        // Сохраняем системные
        instanceProfile.setMemoryMb(((int) ramSlider.getValue() / 512) * 512);
        
        String jPath = javaPathField.getText().trim();
        instanceProfile.setJavaPath(jPath.isEmpty() ? null : jPath);
        
        String jArgs = jvmArgsField.getText().trim();
        instanceProfile.setJvmArgs(jArgs.isEmpty() ? null : jArgs);

        // Сохраняем моды
        Map<String, Boolean> newState = instanceProfile.getOptionalModsState();
        modCheckboxes.forEach((id, box) -> newState.put(id, box.isSelected()));

        // Пишем на диск
        di.getProfileManager().saveProfile(instanceProfile);
        
        // Возвращаемся на главную
        back();
    }
    
    @FXML
    private void reset() {
        // Перезагружаем профиль (сбрасываем несохраненные изменения)
        setServer(serverProfile);
    }

    @FXML
    private void back() {
        dashboard.showHome();
    }
}
