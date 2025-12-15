package hivens.ui;

import hivens.core.api.ISettingsService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProgressController {

    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    @FXML private ProgressBar progressBar;
    @FXML private Label master;
    @FXML private Label description;
    @FXML private Label progress;

    private UpdateAndLaunchTask task;
    private final Main mainApp;
    private final ISettingsService settingsService;

    public ProgressController(Main mainApp, ISettingsService settingsService) {
        this.mainApp = mainApp;
        this.settingsService = settingsService;
    }

    public void startProcess(UpdateAndLaunchTask task, Thread taskThread) {
        this.task = task;

        description.textProperty().bind(task.messageProperty());
        master.textProperty().bind(task.titleProperty());
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            log.info("UpdateAndLaunchTask Succeeded. Process started.");
            unbindProperties();

            // Проверка настройки "Закрывать после запуска"
            boolean shouldClose = settingsService.getSettings().isCloseAfterStart();

            if (shouldClose) {
                description.setText("Клиент запущен. Закрытие...");
                mainApp.hideWindow();
            } else {
                master.setText("ИГРА ЗАПУЩЕНА");
                description.setText("Окно лаунчера оставлено открытым.");
                // Тут можно добавить кнопку "Вернуться в меню", если нужно
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.error("UpdateAndLaunchTask Failed", ex);
            unbindProperties();
            master.textProperty().unbind();
            master.setText("ОШИБКА ЗАПУСКА");
            description.setText(ex != null ? ex.getMessage() : "Неизвестная ошибка");
        });

        taskThread.start();
    }

    private void unbindProperties() {
        description.textProperty().unbind();
        master.textProperty().unbind();
        progress.textProperty().unbind();
        progressBar.progressProperty().unbind();
    }
}