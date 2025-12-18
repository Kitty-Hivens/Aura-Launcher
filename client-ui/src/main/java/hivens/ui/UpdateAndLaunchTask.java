package hivens.ui;

import hivens.core.api.IAuthService; // [NEW]
import hivens.core.api.IFileDownloadService;
import hivens.core.api.ILauncherService;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import hivens.launcher.FileDownloadService;
import hivens.launcher.LauncherDI;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class UpdateAndLaunchTask extends Task<Process> {

    private static final Logger log = LoggerFactory.getLogger(UpdateAndLaunchTask.class);

    private final IFileDownloadService downloadService;
    private final ILauncherService launcherService;
    private final IAuthService authService; // [NEW] Для ре-логина

    // sessionData не final, так как мы можем её обновить
    private SessionData sessionData;

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
        this.downloadService = di.getDownloadService();
        this.launcherService = di.getLauncherService();
        this.authService = di.getAuthService(); // [NEW] Получаем сервис авторизации
        this.sessionData = sessionData;
        this.serverProfile = serverProfile;
        this.clientRootPath = clientRoot;
        this.javaPath = javaPath;
        this.memoryMB = memory;
    }

    @Override
    protected Process call() throws Exception {
        // ==========================================
        // ШАГ 0: ЛЕНИВАЯ ПЕРЕАВТОРИЗАЦИЯ (Lazy Re-auth)
        // ==========================================
        String targetServerId = serverProfile.getAssetDir(); // Куда хотим зайти (напр. "RPG")
        String currentTokenServerId = sessionData.serverId(); // Куда залогинены (напр. "Industrial")

        // Если мы не знаем текущий ID или он не совпадает с целевым
        if (currentTokenServerId == null || !currentTokenServerId.equals(targetServerId)) {
            log.info("Смена сервера: Текущий={}, Целевой={}", currentTokenServerId, targetServerId);

            if (sessionData.cachedPassword() != null) {
                updateTitle("Авторизация на " + serverProfile.getTitle() + "...");
                try {
                    // ПЕРЕЗАХОД: Получаем новый манифест именно для RPG!
                    this.sessionData = authService.login(
                            sessionData.playerName(),
                            sessionData.cachedPassword(),
                            targetServerId
                    );
                    log.info("Успешная переавторизация на {}", targetServerId);
                } catch (Exception e) {
                    log.error("Ошибка смены сервера", e);
                    throw new Exception("Не удалось переключиться на сервер " + targetServerId);
                }
            } else {
                // Иначе скачается не то и игра крашнется.
                log.warn("Нет пароля для смены сервера. Требуется ручной вход.");

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Требуется авторизация");
                    alert.setHeaderText("Смена сервера");
                    alert.setContentText("Вы пытаетесь зайти на сервер '" + serverProfile.getTitle() + "',\n" +
                            "но сохраненная сессия привязана к другому серверу.\n\n" +
                            "Пожалуйста, выйдите из аккаунта и войдите снова (с паролем), чтобы мы могли загрузить правильные файлы.");
                    alert.showAndWait();
                });

                throw new Exception("Требуется перезаход в аккаунт (Log Out -> Log In).");
            }
        }

        // ==========================================
        // ШАГ 1: ФИЛЬТРАЦИЯ И ЗАГРУЗКА
        // ==========================================
        updateTitle("Обновление клиента...");
        updateProgress(0, 100);

        // 1. Подготовка черного списка модов (Модуль 2)
        Set<String> ignoredFiles = new HashSet<>();
        // Здесь можно использовать ManifestProcessorService для парсинга, если он доступен
        // Пока простая логика: если в профиле есть данные, парсим их
        // (Для полной реализации нужен ManifestProcessorService, но начнем с малого)

        if (downloadService instanceof FileDownloadService concreteService) {
            concreteService.processSession(
                    sessionData, // Теперь здесь правильный манифест RPG!
                    serverProfile.getAssetDir(), // ID для логов
                    clientRootPath,
                    serverProfile.getExtraCheckSum(),
                    ignoredFiles, // Передаем список игнора (пока пустой, если не реализовали парсинг)
                    this::updateMessage,
                    this::updateProgress
            );
        }

        // ==========================================
        // ШАГ 2: ЗАПУСК
        // ==========================================
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
