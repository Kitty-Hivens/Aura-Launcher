package hivens.ui;

import hivens.core.api.IFileDownloadService;
import hivens.core.api.IFileIntegrityService;
import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.ServerData;
import hivens.core.data.SessionData;
import javafx.concurrent.Task;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ФОНОВАЯ ЗАДАЧА (Оркестратор).
 * Выполняет весь процесс: Проверка -> Загрузка -> Запуск.
 * (Аналог bw.class + ai.class)
 */
public class UpdateAndLaunchTask extends Task<Process> {

    private static final Logger log = LoggerFactory.getLogger(UpdateAndLaunchTask.class);

    // --- Зависимости (из DI) ---
    private final IManifestProcessorService manifestProcessor;
    private final IFileIntegrityService integrityService;
    private final IFileDownloadService downloadService;
    private final ILauncherService launcherService;
    
    // --- Данные для запуска ---
    private final SessionData sessionData;
    private final ServerData serverData;
    private final Path clientRootPath; // (e.g., /home/haru/.smarty)
    private final Path javaPath;       // (e.g., /usr/bin/java)
    private final int memoryMB;        // (e.g., 4096)

    public UpdateAndLaunchTask(
            LauncherDI di, 
            SessionData sessionData, 
            ServerData serverData, 
            Path clientRoot, 
            Path javaPath, 
            int memory
    ) {
        // Получаем сервисы из DI
        this.manifestProcessor = di.getManifestProcessorService();
        this.integrityService = di.getIntegrityService();
        this.downloadService = di.getDownloadService();
        this.launcherService = di.getLauncherService();
        
        // Получаем данные
        this.sessionData = sessionData;
        this.serverData = serverData;
        this.clientRootPath = clientRoot;
        this.javaPath = javaPath;
        this.memoryMB = memory;
    }

    /**
     * Главный метод, выполняемый в фоновом потоке.
     */
    @Override
    protected Process call() throws Exception {
        
        // --- Шаг 1: Обработка манифеста (Issue #15) ---
        updateMessage("Обработка манифеста...");
        updateProgress(0, 100);
        FileManifest manifest = sessionData.fileManifest();
        
        Map<String, FileData> flatManifest = manifestProcessor.flattenManifest(manifest);
        log.debug("Manifest flattened. Total files: {}", flatManifest.size());

        // --- Шаг 2: Проверка целостности (Issue #7) ---
        updateMessage("Проверка целостности файлов...");
        updateProgress(20, 100);
        
        // (Преобразуем Map<String, FileData> в Map<String, String(md5)> для Issue #7)
        Map<String, String> requiredFiles = flatManifest.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().md5()
                ));
        
        Map<String, String> filesToDownload = integrityService.verifyIntegrity(clientRootPath, requiredFiles);

        // --- Шаг 3: Загрузка файлов (Issue #8) ---
        if (!filesToDownload.isEmpty()) {
            log.info("Starting download of {} files.", filesToDownload.size());
            updateMessage("Загрузка " + filesToDownload.size() + " файлов...");
            updateProgress(40, 100);
            
            // TODO: Реализовать Consumer<String> для обновления progress bar
            downloadService.downloadMissingFiles(clientRootPath, filesToDownload, (fileName) -> {
                // (Этот callback будет вызываться для каждого файла)
            });
        } else {
            log.info("No files to download. Integrity check passed.");
        }

        // --- Шаг 4: Парсинг Конфига (Issue #16) ---
        // (Нам все еще нужно реализовать IConfigParsingService (Issue #16), 
        // чтобы найти client.json и получить ClientData)
        // ClientData clientData = configParsingService.parseClientConfig(...);
        // (ПРОПУЩЕНО - ТЕХНИЧЕСКИЙ ДОЛГ)

        // --- Шаг 5: Запуск ---
        updateMessage("Запуск клиента...");
        updateProgress(90, 100);

        Process process = launcherService.launchClient(
                sessionData,
                serverData,
                clientRootPath,
                javaPath,
                memoryMB
        );

        updateMessage("Клиент запущен.");
        updateProgress(100, 100);
        return process;
    }
}