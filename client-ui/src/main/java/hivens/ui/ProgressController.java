package hivens.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Контроллер для Progress.fxml.
 * Отвечает за привязку UI к фоновой задаче UpdateAndLaunchTask.
 */
public class ProgressController {

    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    @FXML private ProgressBar progressBar;
    @FXML private Label master;
    @FXML private Label description;
    @FXML private Label progress;

    private UpdateAndLaunchTask task;
    private final Main mainApp;

    // ИСПРАВЛЕНИЕ: Добавлен конструктор, принимающий Main
    public ProgressController(Main mainApp) {
        this.mainApp = mainApp;
    }

    /**
     * Метод, вызываемый Main для запуска всего процесса.
     * @param task Сконфигурированная фоновая задача (Оркестратор).
     * @param taskThread Поток, в котором будет выполняться задача (для прерывания).
     */
    public void startProcess(UpdateAndLaunchTask task, Thread taskThread) {
        this.task = task;

        // Привязываем UI к свойствам задачи
        description.textProperty().bind(task.messageProperty());
        master.textProperty().bind(task.titleProperty()); // Используем 'master' для главного статуса
        progressBar.progressProperty().bind(task.progressProperty());

        // (progress (Label) будет обновляться через Platform.runLater из задачи)

        task.setOnSucceeded(e -> {
            log.info("UpdateAndLaunchTask Succeeded. Process started.");
            unbindProperties();
            description.setText("Клиент запущен.");
            mainApp.hideWindow(); // Скрываем окно
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.error("UpdateAndLaunchTask Failed", ex);

            unbindProperties();

            master.textProperty().unbind(); // Также отвязываем
            master.setText("Ошибка!");
            description.setText("Ошибка: " + ex.getMessage());
            // TODO: Показать кнопку "Назад"
        });

        // Запускаем задачу в потоке, управляемом Main
        taskThread.start();
    }

    /**
     * Во избежание утечек памяти.
     */
    private void unbindProperties() {
        description.textProperty().unbind();
        master.textProperty().unbind();
        progress.textProperty().unbind();
        progressBar.progressProperty().unbind();
    }
}
