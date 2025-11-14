package hivens.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hivens.core.api.*;
import hivens.launcher.FileDownloadService;
import hivens.launcher.FileIntegrityService;
import hivens.launcher.LauncherService;
import hivens.launcher.ManifestProcessorService;
import lombok.Getter;
import okhttp3.OkHttpClient;

import java.time.Duration;

/**
 * Контейнер Dependency Injection (DI).
 * Отвечает за создание и предоставление экземпляров сервисов (синглтонов).
 */
@Getter
public class LauncherDI {

    // Потокобезопасные синглтоны
    private final OkHttpClient httpClient;
    private final Gson gson;

    // Интерфейсы Сервисов
    private final IAuthService authService;
    private final IFileIntegrityService integrityService;
    private final IFileDownloadService downloadService;
    private final IManifestProcessorService manifestProcessorService;
    private final ILauncherService launcherService;

    public LauncherDI() {
        // 1. Инициализация базовых зависимостей
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
        
        this.gson = new GsonBuilder().create();

        // 2. Инициализация сервисов (внедрение зависимостей)
        this.authService = new AuthService(httpClient, gson);
        this.integrityService = new FileIntegrityService();
        this.downloadService = new FileDownloadService(httpClient);
        this.manifestProcessorService = new ManifestProcessorService();
        this.launcherService = new LauncherService(manifestProcessorService);
    }
}