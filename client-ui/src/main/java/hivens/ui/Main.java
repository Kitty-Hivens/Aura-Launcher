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
        // Загружаем первый FXML (LoginForm)
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/LoginForm.fxml")
        ));

        // Внедряем DI-контейнер в контроллер (будет создан в следующем коммите)
        loader.setControllerFactory(controllerClass -> {
            // (Пример того, как мы будем внедрять зависимости в контроллеры)
            // if (controllerClass == LoginController.class) {
            //     return new LoginController(container.getAuthService());
            // }
            // return new controllerClass(); // Временная заглушка
            
            // TODO: Реализовать setControllerFactory в Коммите #3
            return null; 
        });

        // Временная заглушка (удалить, когда FXML будет готов)
        // Parent root = loader.load(); 
        
        // primaryStage.setTitle("SCOL");
        // primaryStage.setScene(new Scene(root, 800, 600));
        // primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}