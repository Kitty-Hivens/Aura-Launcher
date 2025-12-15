package hivens.ui;

import hivens.core.api.AuthException;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;

public class LoginController {

    private final LauncherDI di;
    private final Main mainApp;

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<ServerProfile> serversField;
    @FXML private Label statusLabel;
    @FXML private Button loginButton;

    public LoginController(LauncherDI di, Main mainApp) {
        this.di = di;
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        // Подгружаем сохраненный логин, если есть (можно добавить в SettingsData)
        // loginField.setText(di.getSettingsService().getSettings().getLastLogin());

        // Загрузка списка серверов
        loginButton.setDisable(true);
        statusLabel.setText("Загрузка серверов...");

        CompletableFuture.runAsync(() -> {
            try {
                var profiles = di.getServerListService().fetchProfiles().join();
                Platform.runLater(() -> {
                    serversField.getItems().setAll(profiles);
                    if (!profiles.isEmpty()) {
                        serversField.getSelectionModel().selectFirst();
                    }
                    statusLabel.setText("");
                    loginButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка загрузки списка серверов");
                    loginButton.setDisable(false); // Даем возможность попробовать снова или зайти в настройки
                });
            }
        });

        // Горячие клавиши
        loginField.setOnKeyPressed(e -> { if(e.getCode() == KeyCode.ENTER) passwordField.requestFocus(); });
        passwordField.setOnKeyPressed(e -> { if(e.getCode() == KeyCode.ENTER) onLogin(); });
    }

    @FXML
    private void onLogin() {
        String u = loginField.getText().trim();
        String p = passwordField.getText();
        ServerProfile server = serversField.getValue();

        if (u.isEmpty() || p.isEmpty()) {
            statusLabel.setText("Введите логин и пароль");
            shake(loginField);
            return;
        }
        if (server == null) {
            statusLabel.setText("Выберите сервер");
            shake(serversField);
            return;
        }

        setControlsDisabled(true);
        statusLabel.setText("Авторизация...");

        // Запуск в отдельном потоке, чтобы не морозить UI
        new Thread(() -> {
            try {
                // Используем AssetDir как ID сервера для авторизации (стандарт SmartyCraft)
                String serverId = server.getAssetDir();

                SessionData session = di.getAuthService().login(u, p, serverId);

                Platform.runLater(() -> {
                    statusLabel.setText("Успех! Запуск...");
                    launchGame(session, server);
                });

            } catch (AuthException e) {
                Platform.runLater(() -> {
                    // Исправленный switch под ваш AuthStatus.java
                    String msg = switch (e.getStatus()) {
                        case BAD_LOGIN -> "Пользователь не найден";      // Исправлено (было WRONG_LOGIN)
                        case BAD_PASSWORD -> "Неверный пароль";          // Исправлено (было WRONG_PASSWORD)
                        case SERVER, NO_SERVER -> "Ошибка: Неверный ID сервера";
                        case INTERNAL_ERROR -> "Внутренняя ошибка сервера";
                        // BANNED и ACTIVATE_ACCOUNT убраны, так как их нет в AuthStatus
                        default -> "Ошибка входа: " + e.getStatus();
                    };

                    statusLabel.setText(msg);
                    shake(passwordField);
                    setControlsDisabled(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка сети/клиента");
                    e.printStackTrace();
                    setControlsDisabled(false);
                });
            }
        }).start();
    }

    private void launchGame(SessionData session, ServerProfile server) {
        try {
            mainApp.showProgressScene(session, server);
        } catch (Exception e) {
            statusLabel.setText("Не удалось инициализировать запуск");
            e.printStackTrace();
            setControlsDisabled(false);
        }
    }

    @FXML
    private void onSettings() {
        // Вызываем метод открытия глобальных настроек (нужно добавить в Main)
        mainApp.showGlobalSettings();
    }

    @FXML
    private void onClose() {
        Platform.exit();
        System.exit(0);
    }

    private void setControlsDisabled(boolean disabled) {
        loginField.setDisable(disabled);
        passwordField.setDisable(disabled);
        serversField.setDisable(disabled);
        loginButton.setDisable(disabled);
    }

    private void shake(javafx.scene.Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0); tt.setByX(5);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.play();
    }
}
