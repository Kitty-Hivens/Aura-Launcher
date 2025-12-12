package hivens.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hivens.core.api.*;
import hivens.launcher.*;
import lombok.Getter;
import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Контейнер Dependency Injection (DI).
 */
@Getter
public class LauncherDI {

    // --- Пути Приложения ---
    /** (e.g., /home/haru/.config/SCOL) */
    private final Path configDirectory;
    /** (e.g., /home/haru/.local/share/SCOL) */
    private final Path dataDirectory; // (Будет использоваться для clientRootPath)
    /** (e.g., .../SCOL/settings.json) */
    private final Path settingsFilePath;

    // --- Синглтоны ---
    private final OkHttpClient httpClient;
    private final Gson gson;

    // --- Сервисы ---
    private final IAuthService authService;
    private final IFileIntegrityService integrityService;
    private final IFileDownloadService downloadService;
    private final IManifestProcessorService manifestProcessorService;
    private final ILauncherService launcherService;
    private final IServerListService serverListService;
    private final ISettingsService settingsService; // (Добавлено)

    public LauncherDI() {
        // 1. Определение путей (Кроссплатформенно)
        String userHome = System.getProperty("user.home");
        // (Используем .SCOL в домашней директории для простоты)
        this.dataDirectory = Paths.get(userHome, ".SCOL");
        this.configDirectory = this.dataDirectory; // (Или .config/SCOL)
        this.settingsFilePath = this.configDirectory.resolve("settings.json");

        // 2. Инициализация базовых зависимостей
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        // (Включаем prettyPrinting для читаемого settings.json)
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // 3. Инициализация сервисов
        this.authService = new AuthService(httpClient, gson);
        this.integrityService = new FileIntegrityService();
        this.downloadService = new FileDownloadService(httpClient, gson);
        this.manifestProcessorService = new ManifestProcessorService();
        this.launcherService = new LauncherService(manifestProcessorService);
        this.serverListService = new ServerListService();
        this.settingsService = new SettingsService(gson, settingsFilePath);
    }
}