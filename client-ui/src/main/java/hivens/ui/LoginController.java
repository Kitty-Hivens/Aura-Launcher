package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.core.data.AuthStatus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

public class LoginController {

    private final LauncherDI di;
    private final Main mainApp;

    // СОВПАДАЮТ С FXML fx:id
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
        // Загрузка серверов
        di.getServerListService().fetchProfiles().thenAccept(profiles ->
                Platform.runLater(() -> {
                    serversField.getItems().setAll(profiles);
                    if (!profiles.isEmpty()) serversField.getSelectionModel().selectFirst();
                })
        );

        // Вход по Enter
        loginField.setOnKeyPressed(e -> { if(e.getCode() == KeyCode.ENTER) passwordField.requestFocus(); });
        passwordField.setOnKeyPressed(e -> { if(e.getCode() == KeyCode.ENTER) onLogin(); });
    }

    @FXML
    private void onLogin() {
        String u = loginField.getText().trim();
        String p = passwordField.getText();
        ServerProfile server = serversField.getValue();

        if (u.isEmpty() || p.isEmpty() || server == null) {
            statusLabel.setText("Заполните все поля");
            shake(loginField);
            return;
        }

        loginButton.setDisable(true);
        statusLabel.setText("Авторизация...");

        new Thread(() -> {
            try {
                // Используем authService.authenticate или login (в зависимости от твоей реализации)
                // Предположим, что возвращает AuthStatus (или SessionData через исключения)
                AuthStatus status = di.getAuthService().authenticate(u, p);

                Platform.runLater(() -> {
                    if (status == AuthStatus.SUCCESS) {
                        statusLabel.setText("Успех!");
                        // Создаем сессию вручную или берем из сервиса, если он её хранит
                        SessionData session = new SessionData("token", "uuid", u); // Заглушка
                        launchGame(session, server);
                    } else {
                        statusLabel.setText("Ошибка входа");
                        shake(passwordField);
                        loginButton.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка сети: " + e.getMessage());
                    loginButton.setDisable(false);
                });
            }
        }).start();
    }

    private void launchGame(SessionData session, ServerProfile server) {
        try {
            mainApp.showProgressScene(session, server);
        } catch (Exception e) {
            statusLabel.setText("Ошибка запуска: " + e.getMessage());
        }
    }

    @FXML private void onSettings() {
        // mainApp.showSettingsScene();
    }

    @FXML private void onClose() {
        Platform.exit();
        System.exit(0);
    }

    private void shake(javafx.scene.Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0); tt.setByX(5); tt.setCycleCount(6); tt.setAutoReverse(true);
        tt.play();
    }
}
