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

    private final Path configDirectory;
    private final Path dataDirectory;
    private final Path settingsFilePath;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final IAuthService authService;
    private final IFileIntegrityService integrityService;
    private final IFileDownloadService downloadService;
    private final IManifestProcessorService manifestProcessorService;
    private final ILauncherService launcherService;
    private final IServerListService serverListService;
    private final ISettingsService settingsService;

    public LauncherDI() {
        String userHome = System.getProperty("user.home");
        this.dataDirectory = Paths.get(userHome, ".SCOL");
        this.configDirectory = this.dataDirectory;
        this.settingsFilePath = this.configDirectory.resolve("settings.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();


        this.authService = new AuthService(httpClient, gson);
        this.integrityService = new FileIntegrityService();
        this.downloadService = new FileDownloadService(httpClient, gson);
        this.manifestProcessorService = new ManifestProcessorService();
        this.launcherService = new LauncherService(manifestProcessorService);
        this.serverListService = new ServerListService();
        this.settingsService = new SettingsService(gson, settingsFilePath);
    }
}