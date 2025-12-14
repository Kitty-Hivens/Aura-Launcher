package hivens.ui;

import hivens.launcher.SettingsService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Контроллер экрана настроек.
 * Привязывается к fxml/Settings.fxml (замени fx:controller="ae" на этот класс!).
 */
public class SettingsController {

    // === Элементы интерфейса (fx:id совпадают с FXML) ===
    @FXML private Slider memorySlider;
    @FXML private CheckBox memoryAutoCheck;
    @FXML private Label memoryAutoTitle; // Текст "Автоматически" или статус

    @FXML private CheckBox fullScreenCheck;
    @FXML private CheckBox autoConnectCheck;
    @FXML private CheckBox oneClickCheck;
    @FXML private CheckBox offlineCheck;
    @FXML private CheckBox testModeCheck; // Debug mode

    @FXML private Hyperlink folderPath;
    @FXML private Button folderChoose;
    @FXML private Button folderReset;
    @FXML private Button back;

    // Шаги слайдера памяти (1Gb, 1.5Gb ... 8Gb) - копируем логику оригинала для удобства
    private final List<Integer> ramSteps = Arrays.asList(
            1024, 1536, 2048, 2560, 3072, 3584, 4096, 5120, 6144, 8192
    );

    @FXML
    public void initialize() {
        setupSlider();
        loadSettingsToUi();

        // Добавляем слушатели на чекбоксы для авто-сохранения
        setupAutoSave();
    }

    private void setupSlider() {
        memorySlider.setMin(0);
        memorySlider.setMax(ramSteps.size() - 1);
        memorySlider.setMajorTickUnit(1);
        memorySlider.setMinorTickCount(0);
        memorySlider.setSnapToTicks(true);

        // При перетаскивании слайдера обновляем настройки
        memorySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            saveCurrentState();
            // Тут можно добавить обновление текста лейбла, если нужно
        });
    }

    /** Загрузка данных из SettingsService в UI. */
    private void loadSettingsToUi() {
        var global = SettingsService.getGlobal();

        fullScreenCheck.setSelected(global.fullScreen);
        autoConnectCheck.setSelected(global.autoConnect);
        if (testModeCheck != null) testModeCheck.setSelected(global.debugMode);
        if (offlineCheck != null) offlineCheck.setSelected(global.offlineMode);

        // Память
        if (memoryAutoCheck != null) {
            memoryAutoCheck.setSelected(global.autoMemory);
            memorySlider.setDisable(global.autoMemory);
        }

        // Находим ближайшую позицию на слайдере для сохраненного RAM
        int savedRam = global.ramMb;
        int closestIndex = 0;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < ramSteps.size(); i++) {
            int diff = Math.abs(ramSteps.get(i) - savedRam);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }
        memorySlider.setValue(closestIndex);

        // Путь
        if (folderPath != null) folderPath.setText(global.clientDir);
    }

    private void setupAutoSave() {
        // Сохраняем при любом клике
        fullScreenCheck.setOnAction(e -> saveCurrentState());
        autoConnectCheck.setOnAction(e -> saveCurrentState());
        if (testModeCheck != null) testModeCheck.setOnAction(e -> saveCurrentState());
        if (offlineCheck != null) offlineCheck.setOnAction(e -> saveCurrentState());
        if (memoryAutoCheck != null) memoryAutoCheck.setOnAction(e -> {
            memorySlider.setDisable(memoryAutoCheck.isSelected());
            saveCurrentState();
        });
    }

    /** Сохранение состояния UI в конфиг. */
    private void saveCurrentState() {
        var global = SettingsService.getGlobal();

        global.fullScreen = fullScreenCheck.isSelected();
        global.autoConnect = autoConnectCheck.isSelected();
        if (testModeCheck != null) global.debugMode = testModeCheck.isSelected();
        if (offlineCheck != null) global.offlineMode = offlineCheck.isSelected();

        if (memoryAutoCheck != null) {
            global.autoMemory = memoryAutoCheck.isSelected();
        }

        // Если авто выключено - берем значение со слайдера
        if (!global.autoMemory) {
            int stepIndex = (int) memorySlider.getValue();
            if (stepIndex >= 0 && stepIndex < ramSteps.size()) {
                global.ramMb = ramSteps.get(stepIndex);
            }
        }

        SettingsService.save();
    }

    // === FXML Actions (методы, вызываемые из fxml) ===

    @FXML
    private void clickBack(MouseEvent event) {
        saveCurrentState();
        // Возврат назад (скрываем текущее окно)
        back.getScene().getWindow().hide();
    }

    // Дублирующие методы для совместимости со старым FXML (там методы назывались clickSettings...)
    @FXML private void clickSettingsFullscreen() { saveCurrentState(); }
    @FXML private void clickSettingsAutoConnect() { saveCurrentState(); }
    @FXML private void clickSettingsTestMode() { saveCurrentState(); }
    @FXML private void clickSettingsOffline() { saveCurrentState(); }
    @FXML private void clickSettingsMemoryAuto() {
        memorySlider.setDisable(memoryAutoCheck.isSelected());
        saveCurrentState();
    }
    @FXML private void slideMemory(MouseEvent event) { saveCurrentState(); }

    @FXML
    private void clickChoose(MouseEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку клиента");
        File file = chooser.showDialog(folderPath.getScene().getWindow());
        if (file != null) {
            SettingsService.getGlobal().clientDir = file.getAbsolutePath();
            folderPath.setText(file.getAbsolutePath());
            SettingsService.save();
        }
    }

    @FXML
    private void clickClientFolderReset(MouseEvent event) {
        String def = new File(System.getProperty("user.home"), ".SCOL/updates").getAbsolutePath();
        SettingsService.getGlobal().clientDir = def;
        folderPath.setText(def);
        SettingsService.save();
    }
}
