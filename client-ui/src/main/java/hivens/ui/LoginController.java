package hivens.ui;

import hivens.core.api.AuthException;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import hivens.launcher.LauncherDI;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final LauncherDI di;
    private final Main mainApp; // Используем Main для переключения сцен

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
        // Предзаполняем логин, если он был сохранен ранее (из SettingsData)
        // Хотя теперь у нас есть CredentialsManager, старый механизм можно оставить для удобства
        SettingsData settings = di.getSettingsService().getSettings();
        if (settings.getSavedUsername() != null) {
            loginField.setText(settings.getSavedUsername());
        }
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

        // 1. Получаем список серверов (Ваша логика)
        di.getServerListService().fetchProfiles().thenAccept(profiles -> {

            if (profiles == null || profiles.isEmpty()) {
                Platform.runLater(() -> {
                    log.error("Server list is empty or failed to load");
                    statusLabel.setText("Ошибка: Не удалось получить список серверов");
                    setBusy(false);
                });
                return;
            }

            // Берем ID первого сервера для первичной авторизации
            String validServerId = profiles.get(0).getAssetDir();
            log.info("Using server ID for initial login: {}", validServerId);

            // 2. Выполняем вход
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

    private void performAuth(String login, String password, String serverId) {
        Platform.runLater(() -> statusLabel.setText("Авторизация..."));

        Task<SessionData> loginTask = new Task<>() {
            @Override
            protected SessionData call() throws Exception {
                // AuthService вернет сессию с serverId и cachedPassword (если вы обновили AuthService)
                return di.getAuthService().login(login, password, serverId);
            }
        };

        loginTask.setOnSucceeded(e -> {
            SessionData session = loginTask.getValue();
            log.info("Login successful for user: {}", session.playerName());

            // [NEW] Сохраняем пароль в зашифрованном файле
            if (rememberMeCheck.isSelected()) {
                di.getCredentialsManager().save(login, password);
            } else {
                di.getCredentialsManager().clear();
            }

            // Также обновляем старые настройки (для совместимости)
            saveLegacySettings(session);

            Platform.runLater(() -> {
                try {
                    // Переключаем сцену через Main
                    mainApp.showDashboard(session);
                } catch (Exception ex) {
                    log.error("Failed to show dashboard", ex);
                    statusLabel.setText("Ошибка UI: " + ex.getMessage());
                }
                setBusy(false);
                passwordField.clear();
            });
        });

        loginTask.setOnFailed(e -> {
            Throwable error = loginTask.getException();
            log.error("Login failed", error);
            Platform.runLater(() -> {
                String msg = error.getMessage();
                if (error instanceof AuthException) {
                    msg = translateError(msg);
                } else if (msg == null || msg.isBlank()) {
                    msg = "Ошибка соединения";
                }
                statusLabel.setText(msg);
                setBusy(false);
                animateError(statusLabel);
            });
        });

        new Thread(loginTask).start();
    }

    private void saveLegacySettings(SessionData session) {
        SettingsData settings = di.getSettingsService().getSettings();
        if (rememberMeCheck.isSelected()) {
            settings.setSaveCredentials(true);
            settings.setSavedUsername(session.playerName());
            // Токен сохранять не обязательно, так как мы теперь используем пароль,
            // но можно оставить для старой логики
            settings.setSavedAccessToken(session.accessToken());
        } else {
            settings.setSaveCredentials(false);
            settings.setSavedAccessToken(null);
        }
        di.getSettingsService().saveSettings(settings);
    }

    private String translateError(String error) {
        if (error.contains("BAD_LOGIN")) return "Пользователь не найден";
        if (error.contains("BAD_PASSWORD")) return "Неверный пароль";
        return "Ошибка: " + error;
    }

    private void setBusy(boolean busy) {
        if (loginButton != null) loginButton.setDisable(busy);
        if (loginField != null) loginField.setDisable(busy);
        if (passwordField != null) passwordField.setDisable(busy);
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
        if (mainApp.getPrimaryStage() != null) mainApp.getPrimaryStage().setIconified(true);
    }

    @FXML private void onExit() {
        Platform.exit();
        System.exit(0);
    }
}
