package hivens.ui;

import hivens.core.api.model.ServerProfile;
import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import hivens.launcher.ManifestProcessorService;
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
    private SessionData currentSession;

    @Override
    public void init() {
        this.container = new LauncherDI();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        showLoginScene();
    }

    public void showLoginScene() throws IOException {
        FXMLLoader loader = loadFXML("LoginForm.fxml");
        // Фабрика для LoginController
        loader.setControllerFactory(clz -> new LoginController(container, this));

        Parent root = loader.load();
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Применяем тему
        ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public void showAuraMain() {
        try {
            FXMLLoader loader = loadFXML("AuraMain.fxml");
            // В AuraMainController нужно добавить конструктор с DI
            // loader.setControllerFactory(clz -> new AuraMainController(container));
            // Пока оставим стандартный, если контроллер простой

            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());

            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            log.error("Failed to show Aura Main", e);
        }
    }

    // --- ОТКРЫТИЕ НАСТРОЕК СЕРВЕРА ---
    public void showServerSettings(ServerProfile server) {
        try {
            FXMLLoader loader = loadFXML("ServerSettings.fxml");
            ServerSettingsController controller = new ServerSettingsController();
            loader.setController(controller);

            Stage settingsStage = new Stage();
            settingsStage.initOwner(primaryStage);
            settingsStage.initModality(Modality.WINDOW_MODAL);
            settingsStage.initStyle(StageStyle.TRANSPARENT);

            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());
            settingsStage.setScene(scene);

            // ЗАГРУЗКА ДАННЫХ
            InstanceProfile profile = container.getProfileManager().getProfile(server.getAssetDir());

            // Используем наш новый метод в ManifestProcessorService
            List<OptionalMod> mods = container.getManifestProcessorService()
                    .getOptionalModsForClient(server.getVersion());

            controller.setup(profile, container.getProfileManager(), mods, settingsStage);

            settingsStage.showAndWait();

        } catch (IOException e) {
            log.error("Failed to open settings", e);
        }
    }

    // --- ЗАПУСК (ПРОГРЕСС) ---
    public void showProgressScene(SessionData session, ServerProfile server) throws IOException {
        FXMLLoader loader = loadFXML("Progress.fxml");
        SettingsData globalSettings = container.getSettingsService().getSettings();

        UpdateAndLaunchTask task = new UpdateAndLaunchTask(
                container,
                session,
                server,
                container.getDataDirectory().resolve("clients").resolve(server.getAssetDir()),
                // Если Java нет в глобалках, передаем null, LauncherService сам найдет
                (globalSettings.getJavaPath() != null) ? Paths.get(globalSettings.getJavaPath()) : null,
                globalSettings.getMemoryMB()
        );

        loader.setControllerFactory(clz -> new ProgressController(this));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());

        ProgressController controller = loader.getController();
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        controller.startProcess(task, thread);

        primaryStage.setScene(scene);
    }

    private FXMLLoader loadFXML(String fxml) {
        return new FXMLLoader(getClass().getResource("/fxml/" + fxml));
    }

    /**
     * Скрывает главное окно (вызывается при успешном старте игры).
     */
    public void hideWindow() {
        Platform.runLater(() -> {
            if (primaryStage != null) {
                primaryStage.hide();
            }
        });
    }

    public void setSession(SessionData session) {
        this.currentSession = session;
    }

}
