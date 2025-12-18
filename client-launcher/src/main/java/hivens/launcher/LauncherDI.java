package hivens.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hivens.core.api.*;
import lombok.Getter;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
public class LauncherDI {

    private final Path dataDirectory;
    private final OkHttpClient httpClient;
    private final Gson gson;

    // Сервисы
    private final IAuthService authService;
    private final IFileIntegrityService integrityService;
    private final IFileDownloadService downloadService;
    private final IManifestProcessorService manifestProcessorService;
    private final ILauncherService launcherService;
    private final IServerListService serverListService;
    private final ISettingsService settingsService;
    private final ProfileManager profileManager;
    private final JavaManagerService javaManagerService;
    private final CredentialsManager credentialsManager;

    public LauncherDI() {
        String userHome = System.getProperty("user.home");
        this.dataDirectory = Paths.get(userHome, ".aura");
        
        // Gson
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // HTTP & Proxy
        Authenticator proxyAuthenticator = (route, response) -> {
            String credential = Credentials.basic("proxyuser", "proxyuserproxyuser");
            return response.request().newBuilder().header("Proxy-Authorization", credential).build();
        };

        this.httpClient = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.smartycraft.ru", 1080)))
                .proxyAuthenticator(proxyAuthenticator)
                .build();

        // Базовые сервисы
        this.authService = new AuthService(httpClient, gson);
        this.integrityService = new FileIntegrityService();
        this.downloadService = new FileDownloadService(httpClient, gson);
        this.manifestProcessorService = new ManifestProcessorService(gson);
        this.serverListService = new ServerListService();
        this.settingsService = new SettingsService(gson, dataDirectory.resolve("settings.json"));
        this.credentialsManager = new CredentialsManager(dataDirectory, gson);
        
        // 1. Менеджер профилей (InstanceProfile)
        this.profileManager = new ProfileManager(dataDirectory, gson);
        
        // 2. Менеджер Java (Скачивание JDK)
        this.javaManagerService = new JavaManagerService(dataDirectory, httpClient);

        // 3. Лаунчер Сервис
        this.launcherService = new LauncherService(
            manifestProcessorService, 
            profileManager, 
            javaManagerService
        );
    }

}