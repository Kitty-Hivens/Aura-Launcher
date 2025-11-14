package hivens.ui;

import hivens.core.api.AuthException;
import hivens.core.api.IAuthService;
import hivens.core.data.SessionData;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Контроллер для LoginForm.fxml.
 * Отвечает за обработку ввода пользователя и вызов IAuthService.
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    // FXML Поля (ID должны совпадать с LoginForm.fxml)
    @FXML private TextField login;
    @FXML private PasswordField password;
    @FXML private Button enter;
    @FXML private Label status;
    // @FXML private ComboBox servers; // (Будет добавлено позже)

    // Зависимости (DI)
    private final IAuthService authService;
    private final LauncherDI diContainer; // Для доступа к другим сервисам

    /**
     * Конструктор для внедрения зависимостей.
     */
    public LoginController(LauncherDI diContainer) {
        this.diContainer = diContainer;
        this.authService = diContainer.getAuthService();
    }

    /**
     * Вызывается JavaFX после FXML-инъекций.
     */
    @FXML
    public void initialize() {
        // (Здесь можно установить текст по умолчанию
        // this.login.setPromptText("Логин");
        // this.password.setPromptText("Пароль");
    }

    /**
     * Обработчик нажатия кнопки "Войти" (id="enter").
     */
    @FXML
    private void onLoginClick() {
        String username = login.getText();
        String pass = password.getText();
        
        // TODO: Добавить ComboBox для serverId
        String serverId = "Industrial"; // Временная заглушка

        if (username.isEmpty() || pass.isEmpty()) {
            updateStatus("Логин и пароль не могут быть пустыми.", true);
            return;
        }

        // 1. Создаем фоновую задачу (Task) для сетевого запроса
        Task<SessionData> loginTask = createLoginTask(username, pass, serverId);
        
        // 2. Настраиваем UI-реакции на задачу
        loginTask.setOnRunning(e -> setControlsDisabled(true));
        loginTask.setOnFailed(e -> handleLoginFailure(loginTask.getException()));
        loginTask.setOnSucceeded(e -> handleLoginSuccess(loginTask.getValue()));
        
        // 3. Запускаем задачу
        new Thread(loginTask).start();
    }

    /**
     * Создает Task для асинхронной аутентификации.
     */
    private Task<SessionData> createLoginTask(String username, String pass, String serverId) {
        return new Task<>() {
            @Override
            protected SessionData call() throws Exception {
                // Этот код выполняется в фоновом потоке
                return authService.login(username, pass, serverId);
            }
        };
    }

    /**
     * Обработка успешного входа (вызывается в UI-потоке).
     */
    private void handleLoginSuccess(SessionData sessionData) {
        log.info("Login successful for {}", sessionData.playerName());
        updateStatus("Успешный вход.", false);
        
        // TODO: Переключить сцену на Main.fxml / Progress.fxml
    }

    /**
     * Обработка ошибок входа (вызывается в UI-потоке).
     */
    private void handleLoginFailure(Throwable exception) {
        setControlsDisabled(false);
        if (exception instanceof AuthException authEx) {
            // Ошибка аутентификации (например, неверный пароль)
            log.warn("AuthException: {}", authEx.getStatus());
            updateStatus("Ошибка: " + authEx.getStatus(), true); // (Используем AuthStatus)
        } else {
            // Сетевая ошибка (IOException)
            log.error("Network or IO error", exception);
            updateStatus("Ошибка сети.", true);
        }
    }

    private void setControlsDisabled(boolean disabled) {
        login.setDisable(disabled);
        password.setDisable(disabled);
        enter.setDisable(disabled);
    }
    
    private void updateStatus(String message, boolean isError) {
        status.setText(message);
        // (Здесь можно добавить CSS-класс для ошибки)
        // status.getStyleClass().setAll(isError ? "status-error" : "status-success");
    }
}