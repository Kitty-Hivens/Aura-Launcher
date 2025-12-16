package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.AuthStatus;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
    // [NEW] Геттер для доступа к главному окну из контроллеров
    @Getter
    private Stage primaryStage;
    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void init() {
        this.container = new LauncherDI();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;

        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setResizable(false);
        primaryStage.setTitle("Aura Launcher");

        try {
            if (getClass().getResource("/images/favicon.png") != null) {
                primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/favicon.png"))));
            }
        } catch (Exception ignored) {}

        Platform.setImplicitExit(true);

        // --- [ВОССТАНОВЛЕННЫЙ БЛОК] ЛОГИКА АВТО-ВХОДА ---
        SettingsData settings = container.getSettingsService().getSettings();

        // Проверяем наличие токена (пароль нам не нужен)
        if (settings.isSaveCredentials() && settings.getSavedAccessToken() != null && !settings.getSavedAccessToken().isEmpty()) {
            log.info("Auto-login: restoring session for {}", settings.getSavedUsername());

            // Восстанавливаем полную сессию, включая манифест файлов
            SessionData restoredSession = new SessionData(
                    AuthStatus.OK,
                    settings.getSavedUsername(),
                    settings.getSavedUuid(),
                    settings.getSavedAccessToken(),
                    settings.getSavedFileManifest()
            );

            showDashboard(restoredSession);
        } else {
            showLoginScene();
        }
    }

    /**
     * Показывает основной дашборд и передает в него сессию
     */
    public void showDashboard(SessionData session) throws IOException {
        primaryStage.setWidth(1050);
        primaryStage.setHeight(700);

        FXMLLoader loader = loadFXML("MainDashboard.fxml");
        loader.setControllerFactory(clz -> new DashboardController(container, this));

        Parent root = loader.load();

        DashboardController controller = loader.getController();
        controller.initSession(session);

        applyScene(root);
    }

    /**
     * Показывает экран входа
     */
    public void showLoginScene() throws IOException {
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

            // Обновляем тему главного окна после закрытия (на случай отмены или сохранения)
            ThemeManager.applyTheme(primaryStage.getScene(), container.getSettingsService().getSettings());

        } catch (IOException e) {
            log.error("Failed to show global settings", e);
        }
    }

    private void showScene(FXMLLoader loader) throws IOException {
        Parent root = loader.load();
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());
        makeDraggable(scene, root);
        primaryStage.setScene(scene);
        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
    }

    private FXMLLoader loadFXML(String fxml) {
        return new FXMLLoader(getClass().getResource("/fxml/" + fxml));
    }

    public void hideWindow() {
        Platform.runLater(() -> {
            if (primaryStage != null) primaryStage.hide();
        });
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
