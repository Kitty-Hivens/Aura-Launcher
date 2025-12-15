package hivens.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hivens.core.api.*;
import hivens.launcher.*;
import lombok.Getter;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
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
        this.dataDirectory = Paths.get(userHome, ".aura");
        this.configDirectory = this.dataDirectory;
        this.settingsFilePath = this.configDirectory.resolve("settings.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        Authenticator proxyAuthenticator = (route, response) -> {
            String credential = Credentials.basic("proxyuser", "proxyuserproxyuser");
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };

        this.httpClient = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.smartycraft.ru", 1080)))
                .proxyAuthenticator(proxyAuthenticator)
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