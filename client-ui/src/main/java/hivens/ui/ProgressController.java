package hivens.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Контроллер для Progress.fxml.
 * Отвечает за привязку UI к фоновой задаче UpdateAndLaunchTask.
 * (Аналог aa.class)
 */
public class ProgressController {

    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    // FXML Поля (ID должны совпадать с Progress.fxml)
    @FXML private ProgressBar progressBar;
    @FXML private Label master; // (На основе aa.class: "prepare.checkingFiles")
    @FXML private Label description; // (На основе aa.class: "prepare.updateClient.downloading")
    @FXML private Label progress; // (На основе aa.class: "progress.master.checkClient")

    private UpdateAndLaunchTask task;

    /**
     * Метод, вызываемый LoginController (или Main) для запуска всего процесса.
     *
     * @param task Сконфигурированная фоновая задача (Оркестратор).
     */
    public void startProcess(UpdateAndLaunchTask task) {
        this.task = task;

        // Привязываем UI к свойствам задачи (Task)
        // (Используем description и progress, как в aa.class)
        description.textProperty().bind(task.messageProperty()); 
        progress.textProperty().bind(task.titleProperty()); // (Или наоборот, в зависимости от FXML)
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            // Успешный запуск (аналог case 7 в aa.class)
            log.info("UpdateAndLaunchTask Succeeded. Process started.");
            description.textProperty().unbind();
            description.setText("Клиент запущен.");
            // TODO: Скрыть окно (Launcher.a.n() в aa.class)
        });

        task.setOnFailed(e -> {
            // Ошибка (аналог case 9 в aa.class)
            Throwable ex = task.getException();
            log.error("UpdateAndLaunchTask Failed", ex);
            description.textProperty().unbind();
            description.setText("Ошибка: " + ex.getMessage());
            // TODO: Показать кнопку "Назад" или "Отправить отчет"
        });

        // Запускаем задачу в новом потоке
        new Thread(task).start();
    }
}