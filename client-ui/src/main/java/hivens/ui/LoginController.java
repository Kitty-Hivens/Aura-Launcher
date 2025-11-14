package hivens.ui;

import hivens.core.api.AuthException;
import hivens.core.api.IAuthService;
import hivens.core.api.IServerListService;
import hivens.core.api.ISettingsService;
import hivens.core.data.ServerData;
import hivens.core.data.ServerListResponse;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Контроллер для LoginForm.fxml.
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField login;
    @FXML private PasswordField password;
    @FXML private Button enter;
    @FXML private Button settings;
    @FXML private Label status;
    @FXML private ComboBox<ServerData> servers;

    private final IAuthService authService;
    private final IServerListService serverListService;
    private final ISettingsService settingsService;
    private final Main mainApp;
    private SettingsData currentSettings;

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

        new Thread(settingsTask).start();
    }

    private void loadServerList() {
        status.setText("Загрузка списка серверов...");
        Task<ServerListResponse> task = new Task<>() {
            @Override
            protected ServerListResponse call() throws Exception {
                return serverListService.getServerList();
            }
        };

        task.setOnFailed(e -> {
            updateStatus("Ошибка загрузки списка серверов.", true);
            log.error("Failed to load server list", task.getException());
        });

        task.setOnSucceeded(e -> {
            ServerListResponse response = task.getValue();
            servers.getItems().setAll(response.servers());
            servers.getSelectionModel().selectFirst();
            setControlsDisabled(false);
            updateStatus("Готов к входу.", false);
        });

        new Thread(task).start();
    }

    @FXML
    private void onLoginClick() {
        String username = login.getText();
        String pass = password.getText();
        ServerData selectedServer = servers.getSelectionModel().getSelectedItem();

        if (username.isEmpty() || pass.isEmpty() || selectedServer == null) {
            updateStatus("Заполните все поля и выберите сервер.", true);
            return;
        }

        Task<SessionData> loginTask = new Task<>() {
            @Override
            protected SessionData call() throws Exception {
                return authService.login(username, pass, selectedServer.name());
            }
        };

        loginTask.setOnRunning(e -> setControlsDisabled(true));
        loginTask.setOnFailed(e -> handleLoginFailure(loginTask.getException()));

        loginTask.setOnSucceeded(e -> {
            SessionData session = loginTask.getValue();
            Platform.runLater(() -> {
                try {
                    mainApp.showProgressScene(session, selectedServer, currentSettings);
                } catch (IOException ex) {
                    log.error("Failed to switch to Progress scene", ex);
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
        String errorMsg = "Ошибка сети.";
        if (exception instanceof AuthException authEx) {
            log.warn("AuthException: {}", authEx.getStatus());
            errorMsg = "Ошибка: " + authEx.getStatus();
        } else {
            log.error("Network or IO error", exception);
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
    }
}