package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import hivens.launcher.LauncherDI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private LauncherDI container;

    @Getter
    private Stage primaryStage;
    private double xOffset = 0;
    private double yOffset = 0;

    // Временный рут для отображения лоадера авто-входа
    private StackPane rootLayout;

    // Ссылки на контроллеры (необязательно хранить, но полезно для отладки)
    private DashboardController dashboardController;
    private LoginController loginController;

    @Override
    public void init() {
        this.container = new LauncherDI();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;

        // Настройка стиля окна
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setResizable(false);
        primaryStage.setTitle("Aura Launcher");

        try {
            if (getClass().getResource("/images/favicon.png") != null) {
                primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/favicon.png"))));
            }
        } catch (Exception ignored) {}

        Platform.setImplicitExit(true);

        // Создаем базовую сцену (пустую или с лоадером)
        rootLayout = new StackPane();
        Scene scene = new Scene(rootLayout, 1050, 700); // Размер как у дашборда
        scene.setFill(Color.TRANSPARENT);

        ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());

        primaryStage.setScene(scene);
        makeDraggable(scene, rootLayout);

        // Инициализация контроллеров (ленивая или предварительная)
        // Важно: DashboardController требует Main в конструкторе
        this.dashboardController = new DashboardController(container, this);
        this.loginController = new LoginController(container, this);

        // 1. Проверяем, есть ли сохраненный пароль
        var creds = container.getCredentialsManager().load();

        if (creds != null && creds.decryptedPassword != null) {
            log.info("Auto-login: Found credentials for user '{}'", creds.username);

            // Показываем окно и лоадер
            primaryStage.show();
            showLoadingScreen("Подключение к серверу...");

            // 2. Получаем список профилей, чтобы узнать валидный ID сервера
            container.getServerListService().fetchProfiles().thenAccept(profiles -> {

                String targetServerId = "Industrial"; // Fallback на случай проблем

                if (profiles != null && !profiles.isEmpty()) {
                    // Пытаемся найти последний запущенный сервер
                    String lastId = container.getProfileManager().getLastServerId();

                    // Если последний сервер есть в списке - используем его
                    if (lastId != null && profiles.stream().anyMatch(p -> p.getAssetDir().equals(lastId))) {
                        targetServerId = lastId;
                    } else {
                        // Иначе берем первый попавшийся
                        targetServerId = profiles.get(0).getAssetDir();
                    }
                } else {
                    log.warn("Auto-login warning: Profiles list is empty, using default ID");
                }

                // 3. Выполняем вход с найденным ID
                performAutoLogin(creds.username, creds.decryptedPassword, targetServerId);

            }).exceptionally(ex -> {
                // Если не удалось получить список серверов — идем на экран входа
                log.error("Auto-login failed: could not fetch server list", ex);
                Platform.runLater(() -> {
                    try { showLoginScene(); } catch (IOException e) { e.printStackTrace(); }
                });
                return null;
            });

        } else {
            // Данных нет — показываем логин
            showLoginScene();
        }
    }

    // Вспомогательный метод для выполнения входа
    private void performAutoLogin(String username, String password, String serverId) {
        new Thread(() -> {
            try {
                // Реальный вход через API
                SessionData session = container.getAuthService().login(username, password, serverId);

                Platform.runLater(() -> {
                    log.info("Auto-login successful!");
                    try {
                        showDashboard(session);
                    } catch (IOException e) {
                        log.error("Failed to show dashboard after autologin", e);
                        try { showLoginScene(); } catch (IOException ex) {}
                    }
                });
            } catch (Exception e) {
                log.error("Auto-login failed (invalid credentials or network error)", e);

                // Если пароль не подошел — удаляем его, чтобы не пытаться снова
                container.getCredentialsManager().clear();

                Platform.runLater(() -> {
                    try { showLoginScene(); } catch (IOException ex) {}
                });
            }
        }).start();
    }

    private void showLoadingScreen(String message) {
        Label label = new Label(message);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 10;");
        rootLayout.getChildren().setAll(pane);
    }

    // --- Методы навигации ---

    public void showDashboard(SessionData session) throws IOException {
        primaryStage.setWidth(1050);
        primaryStage.setHeight(700);

        FXMLLoader loader = loadFXML("MainDashboard.fxml");
        // Передаем Main в конструктор контроллера
        loader.setControllerFactory(clz -> new DashboardController(container, this));

        Parent root = loader.load();

        DashboardController controller = loader.getController();
        controller.initSession(session);

        applyScene(root);
    }

    public void showLoginScene() throws IOException {
        primaryStage.setWidth(925); // Размер окна логина
        primaryStage.setHeight(530);

        FXMLLoader loader = loadFXML("LoginForm.fxml");
        loader.setControllerFactory(clz -> new LoginController(container, this));

        showScene(loader);
    }

    public void showProgressScene(SessionData session, ServerProfile server) throws IOException {
        FXMLLoader loader = loadFXML("Progress.fxml");
        SettingsData globalSettings = container.getSettingsService().getSettings();

        UpdateAndLaunchTask task = new UpdateAndLaunchTask(
                container,
                session,
                server,
                container.getDataDirectory().resolve("clients").resolve(server.getAssetDir()),
                (globalSettings.getJavaPath() != null) ? Paths.get(globalSettings.getJavaPath()) : null,
                globalSettings.getMemoryMB()
        );

        loader.setControllerFactory(clz -> new ProgressController(this, container.getSettingsService()));
        showScene(loader);

        ProgressController controller = loader.getController();
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        controller.startProcess(task, thread);
    }

    // [FIX] Восстановленный метод hideWindow
    public void hideWindow() {
        Platform.runLater(() -> {
            if (primaryStage != null) primaryStage.hide();
        });
    }

    public void showGlobalSettings() {
        try {
            FXMLLoader loader = loadFXML("Settings.fxml");
            loader.setControllerFactory(c -> new SettingsController(container, this));

            Stage settingsStage = new Stage();
            settingsStage.initOwner(primaryStage);
            settingsStage.initModality(Modality.WINDOW_MODAL);
            settingsStage.initStyle(StageStyle.TRANSPARENT);
            settingsStage.setResizable(false);
            settingsStage.setTitle("Aura Settings");

            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());
            makeDraggable(scene, root);

            settingsStage.setScene(scene);
            settingsStage.showAndWait();

            // Обновляем тему главного окна после закрытия
            ThemeManager.applyTheme(primaryStage.getScene(), container.getSettingsService().getSettings());

        } catch (IOException e) {
            log.error("Failed to show global settings", e);
        }
    }

    // --- Вспомогательные методы ---

    private void applySceneToStage(Stage stage, Parent root) {
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());
        makeDraggable(scene, root);
        stage.setScene(scene);
    }

    private void applyScene(Parent root) {
        applySceneToStage(primaryStage, root);
        primaryStage.centerOnScreen();
        if (!primaryStage.isShowing()) primaryStage.show();
    }

    private void showScene(FXMLLoader loader) throws IOException {
        Parent root = loader.load();
        applyScene(root);
    }

    private FXMLLoader loadFXML(String fxml) {
        return new FXMLLoader(getClass().getResource("/fxml/" + fxml));
    }

    private void makeDraggable(Scene scene, Parent root) {
        scene.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        scene.setOnMouseDragged(event -> {
            if (!primaryStage.isFullScreen()) {
                primaryStage.setX(event.getScreenX() - xOffset);
                primaryStage.setY(event.getScreenY() - yOffset);
            }
        });
    }
}
