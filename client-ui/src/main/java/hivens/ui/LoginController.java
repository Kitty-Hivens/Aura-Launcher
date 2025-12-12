package hivens.ui;

import hivens.core.api.AuthException;
import hivens.core.api.IAuthService;
import hivens.core.api.IServerListService;
import hivens.core.api.ISettingsService;
import hivens.core.api.model.ServerProfile; // ВАЖНО: Используем Profile, а не Data
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField login;
    @FXML private PasswordField password;
    @FXML private Button enter;
    @FXML private Button settings;
    @FXML private Label status;

    // ВАЖНО: Тип теперь ServerProfile
    @FXML private ComboBox<ServerProfile> servers;

    private final IAuthService authService;
    private final IServerListService serverListService;
    private final ISettingsService settingsService;
    private final Main mainApp;
    private SettingsData currentSettings;

    private final AtomicBoolean serverListLoaded = new AtomicBoolean(false);

    public LoginController(LauncherDI di, Main mainApp) {
        this.authService = di.getAuthService();
        this.serverListService = di.getServerListService();
        this.settingsService = di.getSettingsService();
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        setControlsDisabled(true);
        status.setText("Загрузка настроек...");

        // Converter больше НЕ НУЖЕН, так как ServerProfile.toString() возвращает красивое имя.
        // servers.setConverter(...); <--- УДАЛЕНО

        Task<SettingsData> settingsTask = getSettingsTask();
        new Thread(settingsTask).start();
    }

    @NotNull
    private Task<SettingsData> getSettingsTask() {
        Task<SettingsData> settingsTask = new Task<>() {
            @Override
            protected SettingsData call() throws Exception {
                return settingsService.loadSettings();
            }
        };

        settingsTask.setOnFailed(e -> {
            log.error("Failed to load settings! Using defaults.", settingsTask.getException());
            this.currentSettings = SettingsData.defaults();
            loadServerList();
        });

        settingsTask.setOnSucceeded(e -> {
            this.currentSettings = settingsTask.getValue();
            loadServerList();
        });
        return settingsTask;
    }

    private void loadServerList() {
        if (!serverListLoaded.compareAndSet(false, true)) {
            return;
        }

        status.setText("Загрузка списка серверов...");

        // Новая логика через CompletableFuture
        serverListService.fetchProfiles()
                .thenAccept(profiles -> Platform.runLater(() -> {
                    if (profiles.isEmpty()) {
                        updateStatus("Список серверов пуст (ошибка сети?)", true);
                        setControlsDisabled(false); // Даем доступ к настройкам
                    } else {
                        servers.getItems().setAll(profiles);
                        servers.getSelectionModel().selectFirst();
                        setControlsDisabled(false);
                        updateStatus("Готов к входу.", false);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        log.error("Failed to load server list", ex);
                        updateStatus("Ошибка загрузки серверов.", true);
                        setControlsDisabled(false);
                    });
                    return null;
                });
    }

    @FXML
    private void onLoginClick() {
        String username = login.getText();
        String pass = password.getText();
        ServerProfile selectedServer = servers.getSelectionModel().getSelectedItem();

        if (username.isBlank() || pass.isEmpty() || selectedServer == null) {
            updateStatus("Заполните все поля.", true);
            return;
        }

        Task<SessionData> loginTask = new Task<>() {
            @Override
            protected SessionData call() throws Exception {
                // Передаем имя сервера для авторизации
                return authService.login(username, pass, selectedServer.getName());
            }
        };

        loginTask.setOnRunning(e -> setControlsDisabled(true));
        loginTask.setOnFailed(e -> handleLoginFailure(loginTask.getException()));

        loginTask.setOnSucceeded(e -> {
            SessionData session = loginTask.getValue();
            Platform.runLater(() -> {
                try {
                    // ВАЖНО: Тут нужно будет обновить Main, чтобы он принимал ServerProfile
                    // Пока передаем null или адаптируем, если Main еще требует ServerData
                    // Но лучше обновить Main.showProgressScene под ServerProfile.
                    // mainApp.showProgressScene(session, selectedServer, currentSettings);

                    log.info("Login success! Session: {}", session.playerName());
                    status.setText("Вход выполнен! (Запуск...)");

                    // ВРЕМЕННО: Просто логируем, пока Main не обновлен
                    // mainApp.showProgressScene(...);

                } catch (Exception ex) {
                    log.error("Failed to switch scene", ex);
                    handleLoginFailure(ex);
                }
            });
        });

        new Thread(loginTask).start();
    }

    @FXML
    private void onSettingsClick() {
        try {
            mainApp.showSettingsScene();
        } catch (IOException e) {
            log.error("Failed to open Settings scene", e);
        }
    }

    private void handleLoginFailure(Throwable exception) {
        setControlsDisabled(false);
        String errorMsg = "Ошибка входа.";
        if (exception instanceof AuthException authEx) {
            errorMsg = "Ошибка: " + authEx.getStatus();
        }
        updateStatus(errorMsg, true);
    }

    private void setControlsDisabled(boolean disabled) {
        login.setDisable(disabled);
        password.setDisable(disabled);
        enter.setDisable(disabled);
        settings.setDisable(disabled);
        servers.setDisable(disabled);
    }

    private void updateStatus(String message, boolean isError) {
        status.setText(message);
        if (isError) {
            status.setStyle("-fx-text-fill: #FF5555;");
        } else {
            status.setStyle("-fx-text-fill: #FFFFFF;"); // Или стиль по умолчанию
        }
    }
}