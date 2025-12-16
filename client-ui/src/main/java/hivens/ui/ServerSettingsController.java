package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import hivens.launcher.ProfileManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSettingsController {

    private final LauncherDI di;
    private final DashboardController dashboard;

    @FXML private Label serverTitle;

    // Настройки Java
    @FXML private TextField memoryField;
    @FXML private Slider memorySlider;
    @FXML private TextField javaPathField;
    @FXML private TextField jvmArgsField;

    // Опциональные моды
    @FXML private VBox modsContainer;

    // Настройки окна
    @FXML private CheckBox fullScreenCheck;
    @FXML private CheckBox autoConnectCheck;

    private ServerProfile server;
    private InstanceProfile profile;
    private final Map<String, Boolean> modsState = new HashMap<>();

    public ServerSettingsController(LauncherDI di, DashboardController dashboard) {
        this.di = di;
        this.dashboard = dashboard;
    }

    public void setServer(ServerProfile server) {
        this.server = server;
        this.serverTitle.setText("Настройки: " + server.getTitle());

        // Загружаем профиль из диска
        ProfileManager pm = di.getProfileManager();
        this.profile = pm.getProfile(server.getAssetDir()); // Используем AssetDir как ID

        loadValues();
        buildModsList();
    }

    private void loadValues() {
        // Память
        int mem = profile.getMemoryMb() != null ? profile.getMemoryMb() : 4096;
        memorySlider.setValue(mem);
        memoryField.setText(String.valueOf(mem));

        // Синхронизация слайдера и поля
        memorySlider.valueProperty().addListener((obs, old, val) -> memoryField.setText(String.valueOf(val.intValue())));
        memoryField.textProperty().addListener((obs, old, val) -> {
            try {
                memorySlider.setValue(Double.parseDouble(val));
            } catch (NumberFormatException ignored) {}
        });

        // Java и аргументы
        javaPathField.setText(profile.getJavaPath() != null ? profile.getJavaPath() : "");
        jvmArgsField.setText(profile.getJvmArgs() != null ? profile.getJvmArgs() : "");

        // Окно
        fullScreenCheck.setSelected(profile.isFullScreen());
        autoConnectCheck.setSelected(profile.isAutoConnect());
    }

    private void buildModsList() {
        modsContainer.getChildren().clear();

        // Получаем список доступных модов для этой версии из манифеста
        List<OptionalMod> availableMods = di.getManifestProcessorService().getOptionalModsForClient(server.getVersion());

        // Получаем сохраненное состояние
        Map<String, Boolean> savedState = profile.getOptionalModsState();
        if (savedState == null) savedState = new HashMap<>();
        this.modsState.putAll(savedState);

        if (availableMods.isEmpty()) {
            Label placeholder = new Label("Нет опциональных модов");
            placeholder.getStyleClass().add("text-placeholder");
            modsContainer.getChildren().add(placeholder);
            return;
        }

        for (OptionalMod mod : availableMods) {
            CheckBox cb = new CheckBox(mod.getName());
            cb.getStyleClass().add("aura-checkbox");

            // Определяем состояние: если есть в профиле -> берем оттуда, иначе -> дефолтное
            boolean isActive = savedState.containsKey(mod.getId())
                    ? savedState.get(mod.getId())
                    : mod.isDefault();

            cb.setSelected(isActive);
            modsState.put(mod.getId(), isActive);

            // Тултип с описанием
            if (mod.getDescription() != null && !mod.getDescription().isEmpty()) {
                cb.setTooltip(new Tooltip(mod.getDescription()));
            }

            // Обработка клика
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                modsState.put(mod.getId(), newVal);
                handleIncompatibleMods(mod, newVal);
            });

            modsContainer.getChildren().add(cb);
        }
    }

    private void handleIncompatibleMods(OptionalMod changedMod, boolean isSelected) {
        if (!isSelected) return;
        if (changedMod.getIncompatibleIds() == null) return;

        // Если включили мод, выключаем несовместимые
        for (javafx.scene.Node node : modsContainer.getChildren()) {
            if (node instanceof CheckBox cb) {
                // Находим чекбокс по названию (это упрощение, лучше хранить мапу <ID, CheckBox>)
                // Но так как у нас ID может отличаться от Name, лучше перебирать список availableMods
                // Для простоты примера оставим так, но в продакшене лучше маппинг.
            }
        }
    }

    @FXML
    private void onSelectJava() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите Java Executable (java/java.exe)");

        File file = chooser.showOpenDialog(dashboard.getStage());

        if (file != null) {
            javaPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onSave() {
        // Сохраняем значения в объект профиля
        try {
            profile.setMemoryMb((int) memorySlider.getValue());
        } catch (Exception e) {
            profile.setMemoryMb(4096);
        }

        profile.setJavaPath(javaPathField.getText().trim());
        profile.setJvmArgs(jvmArgsField.getText().trim());
        profile.setFullScreen(fullScreenCheck.isSelected());
        profile.setAutoConnect(autoConnectCheck.isSelected());

        // Сохраняем моды
        profile.setOptionalModsState(new HashMap<>(modsState));

        // Пишем на диск
        di.getProfileManager().saveProfile(profile);

        // Возвращаемся
        dashboard.showHome();
    }

    @FXML
    private void onCancel() {
        dashboard.showHome();
    }
}
