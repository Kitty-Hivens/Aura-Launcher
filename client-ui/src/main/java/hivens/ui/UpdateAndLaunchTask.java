package hivens.ui;

import hivens.core.api.IFileDownloadService;
import hivens.core.api.ILauncherService;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.launcher.FileDownloadService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * ФОНОВАЯ ЗАДАЧА (Оркестратор).
 * Выполняет: Загрузку файлов (через processSession) -> Запуск игры.
 */
public class UpdateAndLaunchTask extends Task<Process> {

    private static final Logger log = LoggerFactory.getLogger(UpdateAndLaunchTask.class);

    private final IFileDownloadService downloadService;
    private final ILauncherService launcherService;
    private final SessionData sessionData;
    private final ServerProfile serverProfile;
    private final Path clientRootPath;
    private final Path javaPath;
    private final int memoryMB;

    public UpdateAndLaunchTask(
            LauncherDI di,
            SessionData sessionData,
            ServerProfile serverProfile,
            Path clientRoot, // Это папка ~/.SCOL/updates/Industrial
            Path javaPath,
            int memory
    ) {
        this.downloadService = di.getDownloadService();
        this.launcherService = di.getLauncherService();
        this.sessionData = sessionData;
        this.serverProfile = serverProfile;
        this.clientRootPath = clientRoot;
        this.javaPath = javaPath;
        this.memoryMB = memory;
    }

    @Override
    protected Process call() throws Exception {
        // --- Шаг 1: Скачивание и Проверка ---
        updateTitle("Обновление клиента...");
        updateProgress(-1, 100); // Неопределенный прогресс пока анализируем

        // Мы используем конкретную реализацию FileDownloadService,
        // так как метод processSession специфичен для этого лаунчера и не входит в общий интерфейс.
        if (downloadService instanceof FileDownloadService concreteService) {
            // Передаем callback (msg -> updateMessage(msg)) для обновления текста в UI
            concreteService.processSession(sessionData, serverProfile.getName(), this::updateMessage);
        } else {
            log.warn("DownloadService is not an instance of hivens.launcher.FileDownloadService! Skipping download logic.");
        }

        // --- Шаг 2: Запуск ---
        updateTitle("Запуск клиента...");
        updateMessage("Подготовка JVM...");
        updateProgress(100, 100);

        Process process = launcherService.launchClient(
                sessionData,
                serverProfile,
                clientRootPath,
                javaPath,
                memoryMB
        );

        updateTitle("Клиент запущен.");
        return process;
    }
}
