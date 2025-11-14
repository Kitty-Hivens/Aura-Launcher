package hivens.ui;

import hivens.core.data.ServerData;
import hivens.core.data.SessionData;
import hivens.core.data.SettingsData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Главный класс приложения (точка входа JavaFX).
 * Отвечает за запуск UI, DI и управление сценами.
 */
public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private LauncherDI container;
    private Stage primaryStage;

    @Override
    public void init() {
        this.container = new LauncherDI();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        showLoginScene(); // Запускаем Login
    }

    /**
     * Загружает и отображает сцену Логина.
     */
    public void showLoginScene() throws IOException {
        FXMLLoader loader = loadFXML("LoginForm.fxml");

        loader.setControllerFactory(controllerClass -> {
            if (controllerClass == LoginController.class) {
                // Внедряем DI и 'this' (Main) для управления сценами
                return new LoginController(container, this);
            }
            return createController(controllerClass);
        });

        Parent root = loader.load();
        primaryStage.setTitle("SCOL - Вход");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /**
     * Запускает Оркестратор и переключается на сцену Прогресса.
     */
    public void showProgressScene(SessionData session, ServerData server, SettingsData settings) throws IOException {
        FXMLLoader loader = loadFXML("Progress.fxml");

        UpdateAndLaunchTask task = new UpdateAndLaunchTask(
                container,
                session,
                server,
                container.getDataDirectory(), // (e.g., /home/haru/.SCOL)
                Paths.get(settings.javaPath()),
                settings.memoryMB()
        );

        loader.setControllerFactory(controllerClass -> {
            if (controllerClass == ProgressController.class) {
                return new ProgressController(this); // (Передаем Main)
            }
            return createController(controllerClass);
        });

        Parent root = loader.load();
        ProgressController controller = loader.getController();
        controller.startProcess(task); // Запускаем!

        primaryStage.setTitle("SCOL - Запуск...");
        primaryStage.setScene(new Scene(root));
    }

    /**
     * Отображает сцену Настроек.
     */
    public void showSettingsScene() throws IOException {
        FXMLLoader loader = loadFXML("Settings.fxml");

        loader.setControllerFactory(controllerClass -> {
            if (controllerClass == SettingsController.class) {
                return new SettingsController(container, this);
            }
            return createController(controllerClass);
        });

        Parent root = loader.load();
        primaryStage.setTitle("SCOL - Настройки");
        primaryStage.setScene(new Scene(root));
    }

    /**
     * Скрывает окно лаунчера (когда игра запускается).
     */
    public void hideWindow() {
        Platform.runLater(primaryStage::hide);
    }

    // --- Вспомогательные методы ---
    private FXMLLoader loadFXML(String fxmlFile) {
        return new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/" + fxmlFile)
        ));
    }
    private Object createController(Class<?> controllerClass) {
        try {
            return controllerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("Failed to create controller: {}", controllerClass.getName(), e);
            throw new RuntimeException(e);
        }
    }
}