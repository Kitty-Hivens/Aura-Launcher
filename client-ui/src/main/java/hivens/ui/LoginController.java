package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final LauncherDI di;
    private final Main mainApp;

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheck;
    @FXML private Label statusLabel;
    @FXML private Button loginButton;

    public LoginController(LauncherDI di, Main mainApp) {
        this.di = di;
        this.mainApp = mainApp;
    }

    @FXML
    private void initialize() {
        SettingsData settings = di.getSettingsService().getSettings();
        if (settings.getSavedUsername() != null) {
            loginField.setText(settings.getSavedUsername());
        }
        rememberMeCheck.setSelected(settings.isSaveCredentials());
    }

    @FXML
    private void onLogin() {
        String login = loginField.getText();
        String pass = passwordField.getText();

        if (login.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("Введите логин и пароль");
            animateError(loginField);
            return;
        }

        setBusy(true);
        statusLabel.setText("Получение списка серверов...");

        // 1. Сначала получаем список серверов, чтобы узнать валидный ID
        di.getServerListService().fetchProfiles().thenAccept(profiles -> {

            if (profiles == null || profiles.isEmpty()) {
                Platform.runLater(() -> {
                    log.error("Server list is empty or failed to load");
                    statusLabel.setText("Ошибка: Не удалось получить список серверов");
                    setBusy(false);
                });
                return;
            }

            // Берем ID первого сервера (например "HiTech" или "Sandbox")
            // Это нужно только для получения сессии, сам сервер выберем потом в Дашборде
            String validServerId = profiles.get(0).getAssetDir();
            log.info("Using server ID for initial login: {}", validServerId);

            // 2. Теперь выполняем вход с валидным ID
            performAuth(login, pass, validServerId);

        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                log.error("Failed to fetch profiles", ex);
                statusLabel.setText("Ошибка сети: " + ex.getMessage());
                setBusy(false);
            });
            return null;
        });
    }

    private void performAuth(String login, String pass, String serverId) {
        Platform.runLater(() -> statusLabel.setText("Авторизация..."));

        try {
            // Выполняем синхронный запрос (внутри фонового потока CompletableFuture это ок,
            // но лучше обернуть в отдельный task, если fetchProfiles возвращает поток UI.
            // ServerListService.fetchProfiles возвращает future из supplyAsync, так что мы в воркере.

            SessionData session = di.getAuthService().login(login, pass, serverId);

            Platform.runLater(() -> {
                log.info("Login successful for user: {}", session.playerName());
                saveCredentials(session);
                openDashboard(session);
            });

        } catch (Exception e) {
            Platform.runLater(() -> {
                log.error("Auth failed", e);
                // Показываем красивое сообщение ошибки (например "Неверный пароль")
                statusLabel.setText(translateError(e.getMessage()));
                setBusy(false);
                animateError(statusLabel);
            });
        }
    }

    private String translateError(String error) {
        if (error.contains("BAD_LOGIN")) return "Пользователь не найден";
        if (error.contains("BAD_PASSWORD")) return "Неверный пароль";
        if (error.contains("SERVER")) return "Ошибка сервера (ID)";
        return "Ошибка: " + error;
    }

    private void saveCredentials(SessionData session) {
        SettingsData settings = di.getSettingsService().getSettings();
        boolean save = rememberMeCheck.isSelected();

        settings.setSaveCredentials(save);
        if (save) {
            settings.setSavedUsername(session.playerName());
            settings.setSavedUuid(session.uuid());
            settings.setSavedAccessToken(session.accessToken());
            settings.setSavedFileManifest(session.fileManifest());
        } else {
            settings.setSavedAccessToken(null);
            settings.setSavedFileManifest(null);
        }
        di.getSettingsService().saveSettings(settings);
    }

    private void openDashboard(SessionData session) {
        try {
            mainApp.showDashboard(session);
        } catch (Exception e) {
            log.error("Failed to show dashboard", e);
            statusLabel.setText("Critical Error: UI Switch Failed");
        }
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        loginField.setDisable(busy);
        passwordField.setDisable(busy);
    }

    private void animateError(javafx.scene.Node node) {
        javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(50), node);
        tt.setFromX(0);
        tt.setByX(10);
        tt.setCycleCount(4);
        tt.setAutoReverse(true);
        tt.play();
    }

    @FXML private void onMinimize() {
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML private void onExit() {
        Platform.exit();
        System.exit(0);
    }
}
