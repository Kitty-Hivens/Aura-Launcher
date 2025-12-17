package hivens.ui;

import hivens.core.api.IManifestProcessorService;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import hivens.launcher.ProfileManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ServerSettingsController {

    private static final Logger log = LoggerFactory.getLogger(ServerSettingsController.class);

    private final LauncherDI di;
    private final DashboardController dashboard;

    @FXML private Label serverTitleLabel;
    @FXML private VBox modsContainer; // Контейнер для чекбоксов из FXML

    private ServerProfile server;
    private InstanceProfile localProfile;
    
    // Храним ссылки на чекбоксы, чтобы управлять ими (для конфликтов)
    private final Map<String, CheckBox> modCheckboxes = new HashMap<>();

    public ServerSettingsController(LauncherDI di, DashboardController dashboard) {
        this.di = di;
        this.dashboard = dashboard;
    }

    public void setServer(ServerProfile server) {
        this.server = server;
        if (server != null) {
            serverTitleLabel.setText(server.getTitle().toUpperCase());
            
            // 1. Загружаем локальный профиль (где хранятся галочки)
            ProfileManager pm = di.getProfileManager();
            this.localProfile = pm.getProfile(server.getAssetDir());
            
            // 2. Строим список модов
            loadOptionalMods();
        }
    }

    private void loadOptionalMods() {
        if (modsContainer == null) return;
        modsContainer.getChildren().clear();
        modCheckboxes.clear();

        IManifestProcessorService mps = di.getManifestProcessorService();
        // Получаем список модов, доступных для этого сервера
        List<OptionalMod> mods = mps.getOptionalModsForClient(server);

        if (mods.isEmpty()) {
            Label placeholder = new Label("Нет доступных модификаций");
            placeholder.setStyle("-fx-text-fill: #888; -fx-padding: 10;");
            modsContainer.getChildren().add(placeholder);
            return;
        }

        for (OptionalMod mod : mods) {
            CheckBox cb = new CheckBox(mod.getName());
            cb.getStyleClass().add("mod-checkbox"); // Для CSS
            
            // Добавляем описание, если есть
            if (mod.getDescription() != null && !mod.getDescription().isEmpty()) {
                cb.setTooltip(new Tooltip(mod.getDescription()));
            }

            // Определяем состояние: 
            // 1. Смотрим выбор пользователя в InstanceProfile
            // 2. Если нет, берем дефолтное значение с сервера
            boolean isEnabled = localProfile.getOptionalModsState()
                    .getOrDefault(mod.getId(), mod.isDefault());
            
            cb.setSelected(isEnabled);

            // Логика переключения
            cb.selectedProperty().addListener((obs, oldV, newV) -> {
                handleModToggle(mod, newV);
            });

            modCheckboxes.put(mod.getId(), cb);
            modsContainer.getChildren().add(cb);
        }
    }

    private void handleModToggle(OptionalMod mod, boolean isEnabled) {
        // 1. Сохраняем выбор
        localProfile.getOptionalModsState().put(mod.getId(), isEnabled);
        
        // 2. Обработка конфликтов (excludings)
        // Если мод включили, нужно выключить те, с которыми он конфликтует
        if (isEnabled && mod.getExcludings() != null) {
            for (String conflictId : mod.getExcludings()) {
                CheckBox conflictCb = modCheckboxes.get(conflictId);
                if (conflictCb != null && conflictCb.isSelected()) {
                    log.info("Auto-disabling {} due to conflict with {}", conflictId, mod.getId());
                    conflictCb.setSelected(false); // Это триггернет этот же метод рекурсивно для conflictId
                }
            }
        }

        // 3. Сохраняем профиль на диск немедленно
        try {
            di.getProfileManager().saveProfile(localProfile);
        } catch (Exception e) {
            log.error("Failed to save profile settings", e);
        }
    }

    @FXML
    private void onOpenFolder() {
        if (server == null) return;
        Path clientDir = di.getDataDirectory().resolve("clients").resolve(server.getAssetDir());
        if (!Files.exists(clientDir)) {
            try { Files.createDirectories(clientDir); } catch (IOException e) { e.printStackTrace(); }
        }
        new Thread(() -> {
            try { Desktop.getDesktop().open(clientDir.toFile()); } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    @FXML
    private void onReinstall() {
        if (server == null) return;
        Path clientDir = di.getDataDirectory().resolve("clients").resolve(server.getAssetDir());
        if (Files.exists(clientDir)) {
            try (Stream<Path> walk = Files.walk(clientDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Deleted client folder: {}", clientDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Сбрасываем состояние модов при переустановке? Можно, но лучше сохранить выбор игрока.
        onClose();
    }

    @FXML
    private void onClose() {
        Platform.runLater(() -> {
            if (serverTitleLabel.getScene() != null) {
                dashboard.showHome();
            }
        });
    }
}