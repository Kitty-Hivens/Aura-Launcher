package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private LauncherDI container;
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

        // Настройка окна для Hyprland/Tiling
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setResizable(false);
        primaryStage.setTitle("Aura Launcher");

        // Важно: не закрывать приложение неявно, если мы скрываем окна
        // (хотя в нашем случае мы будем проверять настройку)
        Platform.setImplicitExit(true);

        showLoginScene();
    }

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

    public void showGlobalSettings() {
        try {
            FXMLLoader loader = loadFXML("Settings.fxml");
            loader.setControllerFactory(c -> new SettingsController(container));

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
        } catch (IOException e) {
            log.error("Failed to show global settings", e);
        }
    }

    public void showServerSettings(ServerProfile server) {
        try {
            FXMLLoader loader = loadFXML("ServerSettings.fxml");
            ServerSettingsController controller = new ServerSettingsController();
            loader.setController(controller);

            Stage settingsStage = new Stage();
            settingsStage.initOwner(primaryStage);
            settingsStage.initModality(Modality.WINDOW_MODAL);
            settingsStage.initStyle(StageStyle.TRANSPARENT);
            settingsStage.setResizable(false);

            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());
            makeDraggable(scene, root);

            settingsStage.setScene(scene);

            InstanceProfile profile = container.getProfileManager().getProfile(server.getAssetDir());
            List<OptionalMod> mods = container.getManifestProcessorService()
                    .getOptionalModsForClient(server.getVersion());

            controller.setup(profile, container.getProfileManager(), mods, settingsStage);

            settingsStage.showAndWait();
        } catch (IOException e) {
            log.error("Failed to show server settings", e);
        }
    }

    // Вспомогательный метод для загрузки сцены
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
