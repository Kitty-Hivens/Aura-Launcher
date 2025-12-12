package hivens.ui;

import hivens.core.api.IFileDownloadService;
import hivens.core.api.IFileIntegrityService;
import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.ServerData;
import hivens.core.data.SessionData;
import javafx.application.Platform;
import javafx.concurrent.Task;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ФОНОВАЯ ЗАДАЧА (Оркестратор).
 * Выполняет весь процесс: Проверка -> Загрузка -> Запуск.
 */
public class UpdateAndLaunchTask extends Task<Process> {

    private static final Logger log = LoggerFactory.getLogger(UpdateAndLaunchTask.class);

    // ... (Зависимости и Данные) ...
    private final IManifestProcessorService manifestProcessor;
    private final IFileIntegrityService integrityService;
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
            Path clientRoot,
            Path javaPath,
            int memory
    ) {
        // ... (Получаем сервисы из DI) ...
        this.manifestProcessor = di.getManifestProcessorService();
        this.integrityService = di.getIntegrityService();
        this.downloadService = di.getDownloadService();
        this.launcherService = di.getLauncherService();

        // ... (Получаем данные) ...
        this.sessionData = sessionData;
        this.serverProfile = serverProfile;
        this.clientRootPath = clientRoot;
        this.javaPath = javaPath;
        this.memoryMB = memory;
    }

    @Override
    protected Process call() throws Exception {

        // --- Шаг 1: Обработка манифеста ---
        updateTitle("Обработка манифеста..."); // (Используем Title для главного статуса)
        updateProgress(0, 100);

        FileManifest manifest = sessionData.fileManifest();
        Map<String, FileData> flatManifest = manifestProcessor.flattenManifest(manifest);
        log.debug("Manifest flattened. Total files: {}", flatManifest.size());

        // --- Шаг 2: Проверка целостности ---
        updateTitle("Проверка целостности...");
        updateProgress(20, 100);

        Map<String, String> requiredFiles = flatManifest.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().md5()
                ));

        Map<String, String> filesToDownload = integrityService.verifyIntegrity(clientRootPath, requiredFiles);

        // --- Шаг 3: Загрузка файлов ---
        if (!filesToDownload.isEmpty()) {
            log.info("Starting download of {} files.", filesToDownload.size());
            updateTitle("Загрузка " + filesToDownload.size() + " файлов...");
            updateProgress(40, 100);

            final int totalFiles = filesToDownload.size();
            final int[] downloadedCount = {0};

            downloadService.downloadMissingFiles(clientRootPath, filesToDownload, (fileName) -> {
                Platform.runLater(() -> {
                    int count = downloadedCount[0]++;
                    // Обновляем дополнительное сообщение (description)
                    updateMessage("Загрузка: " + fileName);
                    // Обновляем главный прогресс-бар
                    updateProgress(40 + (count * 50.0 / totalFiles), 100);
                });
            });
        } else {
            log.info("No files to download. Integrity check passed.");
        }

        // --- Шаг 4: Запуск ---
        updateTitle("Запуск клиента...");
        updateProgress(90, 100);

        Process process = launcherService.launchClient(
                sessionData,
                serverProfile,
                clientRootPath,
                javaPath,
                memoryMB
        );

        updateTitle("Клиент запущен.");
        updateProgress(100, 100);
        return process;
    }
}