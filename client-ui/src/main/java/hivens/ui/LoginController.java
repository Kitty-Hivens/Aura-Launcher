package hivens.ui;

import hivens.core.api.AuthException;
import hivens.core.api.IAuthService;
import hivens.core.api.IServerListService;
import hivens.core.api.ISettingsService;
import hivens.core.api.model.ServerProfile;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField login;
    @FXML private PasswordField password;
    @FXML private Button enter;
    @FXML private Button settings;
    @FXML private Label status;
    @FXML private ComboBox<ServerProfile> servers;

    private final LauncherDI di; // Сохраняем DI для передачи в таск
    private final IAuthService authService;
    private final IServerListService serverListService;
    private final ISettingsService settingsService;
    private final Main mainApp;

    private SettingsData currentSettings;
    private final AtomicBoolean serverListLoaded = new AtomicBoolean(false);

    public LoginController(LauncherDI di, Main mainApp) {
        this.di = di; // Сохраняем ссылку
        this.authService = di.getAuthService();
        this.serverListService = di.getServerListService();
        this.settingsService = di.getSettingsService();
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        setControlsDisabled(true);
        status.setText("Загрузка настроек...");

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
        if (!serverListLoaded.compareAndSet(false, true)) return;

        status.setText("Загрузка списка серверов...");

        serverListService.fetchProfiles()
                .thenAccept(profiles -> Platform.runLater(() -> {
                    if (profiles.isEmpty()) {
                        updateStatus("Список серверов пуст", true);
                        setControlsDisabled(false);
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

        // Блокируем интерфейс
        setControlsDisabled(true);
        status.setText("Авторизация...");

        Task<SessionData> loginTask = new Task<>() {
            @Override
            protected SessionData call() throws Exception {
                // ВАЖНО: Используем имя из профиля (Industrial), а не Title (Industrial 1.12.2)
                return authService.login(username, pass, selectedServer.getName());
            }
        };

        loginTask.setOnFailed(e -> handleLoginFailure(loginTask.getException()));

        loginTask.setOnSucceeded(e -> {
            SessionData session = loginTask.getValue();
            log.info("Login success! Session: {}", session.playerName());
            status.setText("Успех! Запуск обновления...");

            // --- ЗАПУСК ЗАГРУЗКИ И ИГРЫ ---
            startUpdateAndLaunch(session, selectedServer);
        });

        new Thread(loginTask).start();
    }

    private void startUpdateAndLaunch(SessionData session, ServerProfile serverProfile) {
        // Определяем пути
        Path userHome = Paths.get(System.getProperty("user.home"));
        // Папка клиента: ~/.SCOL/updates/Industrial
        Path clientRoot = userHome.resolve(".SCOL").resolve("updates").resolve(serverProfile.getName());

        // Java Path берем из настроек или дефолтный "java"
        Path javaPath = Paths.get(currentSettings.javaPath() != null && !currentSettings.javaPath().isEmpty()
                ? currentSettings.javaPath() : "java");

        UpdateAndLaunchTask launchTask = new UpdateAndLaunchTask(
                di,
                session,
                serverProfile,
                clientRoot,
                javaPath,
                currentSettings.memoryMB()
        );

        // Связываем UI с задачей
        status.textProperty().bind(launchTask.messageProperty());

        launchTask.setOnFailed(event -> {
            status.textProperty().unbind();
            log.error("Launch failed", launchTask.getException());
            updateStatus("Ошибка запуска: " + launchTask.getException().getMessage(), true);
            setControlsDisabled(false);
        });

        launchTask.setOnSucceeded(event -> {
            status.textProperty().unbind();
            status.setText("Игра запущена!");
            // Тут можно свернуть лаунчер: mainApp.minimize();
            setControlsDisabled(false);
        });

        new Thread(launchTask).start();
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
        } else if (exception != null) {
            errorMsg = "Ошибка: " + exception.getMessage();
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
            status.setStyle("-fx-text-fill: #FFFFFF;");
        }
    }
}
