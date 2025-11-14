package hivens.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Главный класс приложения (точка входа JavaFX).
 * Отвечает за запуск UI и инициализацию DI.
 */
public class Main extends Application {

    private LauncherDI container;

    @Override
    public void init() throws Exception {
        // Инициализируем DI-контейнер перед запуском UI
        this.container = new LauncherDI();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/LoginForm.fxml")
        ));

        // Внедряем DI-контейнер в контроллер
        loader.setControllerFactory(controllerClass -> {
            if (controllerClass == LoginController.class) {
                // Внедряем DI в конструктор контроллера
                return new LoginController(this.container);
            }
            try {
                // Стандартное поведение для FXML без DI
                return controllerClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create controller: " + controllerClass.getName(), e);
            }
        });

        // Загружаем FXML (теперь он найдет контроллер)
        Parent root = loader.load();

        primaryStage.setTitle("SCOL");
        primaryStage.setScene(new Scene(root)); // (Размеры возьмутся из FXML)
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}