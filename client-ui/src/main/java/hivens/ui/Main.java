package hivens.ui;

import hivens.core.api.model.ServerProfile;
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
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;

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

        // 1. Настраиваем размеры окна под новый Dashboard (1050x700)
        primaryStage.setWidth(1050);
        primaryStage.setHeight(700);

        // 2. Устанавливаем прозрачный стиль (без рамок ОС) и фиксируем размер
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setResizable(false);
        primaryStage.setTitle("Aura Launcher");

        // Настройка неявного выхода (зависит от того, как вы хотите обрабатывать закрытие)
        Platform.setImplicitExit(true);

        // 3. Загружаем новый FXML файл (MainDashboard вместо LoginForm)
        FXMLLoader loader = loadFXML("MainDashboard.fxml");

        // Передаем зависимости (DI контейнер и ссылку на Main) в контроллер
        loader.setControllerFactory(clz -> new DashboardController(container, this));

        Parent root = loader.load();

        // 4. Создаем сцену с прозрачной заливкой (важно для скругленных углов CSS)
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // 5. Применяем сохраненную тему пользователя (Warm/Ice/Dark)
        ThemeManager.applyTheme(scene, container.getSettingsService().getSettings());

        // 6. Включаем логику перетаскивания окна мышкой
        makeDraggable(scene, root);

        // 7. Показываем окно
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen(); // Центрируем на мониторе
        primaryStage.show();
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
            // [FIX] Передаем 'this' (Main), чтобы контроллер мог менять тему главного окна
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
