package hivens.ui;

import hivens.core.data.SessionData;
import hivens.core.api.AuthException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

import java.io.IOException;

public class LoginController {

    private final LauncherDI di;
    private final Main mainApp;

    // ID должны совпадать с fx:id в LoginForm.fxml
    @FXML private TextField login;
    @FXML private PasswordField password;
    @FXML private Label status; // Было statusLabel, исправили на status
    @FXML private Button enter;
    @FXML private Button settings;
    @FXML private ComboBox<?> servers; // Заглушка, если список серверов пока не нужен

    public LoginController(LauncherDI di, Main mainApp) {
        this.di = di;
        this.mainApp = mainApp;
    }

    @FXML
    public void initialize() {
        // Вход по Enter
        login.setOnKeyPressed(e -> { if(e.getCode() == KeyCode.ENTER) password.requestFocus(); });
        password.setOnKeyPressed(e -> { if(e.getCode() == KeyCode.ENTER) onLoginClick(); });

        // Очистка статуса при вводе
        login.textProperty().addListener((obs, o, n) -> status.setText(""));
        password.textProperty().addListener((obs, o, n) -> status.setText(""));
    }

    @FXML
    private void onLoginClick() {
        String username = login.getText().trim();
        String pass = password.getText();

        if (username.isEmpty() || pass.isEmpty()) {
            status.setText("Введите логин и пароль");
            status.setStyle("-fx-text-fill: #ff5e62;");
            shakeNode(login);
            return;
        }

        setControlsDisabled(true);
        status.setText("Авторизация...");
        status.setStyle("-fx-text-fill: #aaaaaa;");

        new Thread(() -> {
            try {
                // "launcher" - дефолтный serverId
                SessionData session = di.getAuthService().login(username, pass, "launcher");

                Platform.runLater(() -> {
                    status.setText("Успешно!");
                    status.setStyle("-fx-text-fill: #00ff00;");
                    // Сохраняем сессию и идем дальше
                    mainApp.setSession(session);
                    // TODO: Переход к выбору сервера или сразу запуск
                    // mainApp.showAuraMain();
                    // Или временно, для теста:
                    // mainApp.showServerSettings(someProfile);
                });

            } catch (AuthException e) {
                Platform.runLater(() -> {
                    status.setText("Ошибка: " + e.getMessage());
                    status.setStyle("-fx-text-fill: #ff5e62;");
                    setControlsDisabled(false);
                    shakeNode(password);
                    password.clear();
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    status.setText("Ошибка сети");
                    status.setStyle("-fx-text-fill: #ff5e62;");
                    e.printStackTrace();
                    setControlsDisabled(false);
                });
            }
        }).start();
    }

    @FXML
    private void onSettingsClick() {
        // Логика открытия настроек
        // mainApp.showSettingsScene();
    }

    private void setControlsDisabled(boolean disabled) {
        login.setDisable(disabled);
        password.setDisable(disabled);
        enter.setDisable(disabled);
        if (settings != null) settings.setDisable(disabled);
    }

    private void shakeNode(javafx.scene.Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0); tt.setByX(5); tt.setCycleCount(6); tt.setAutoReverse(true);
        tt.play();
    }
}
