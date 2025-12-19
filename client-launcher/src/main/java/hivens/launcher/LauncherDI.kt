package hivens.launcher

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import hivens.core.api.*
import hivens.core.api.interfaces.*
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.nio.file.Path
import java.nio.file.Paths

class LauncherDI {

    val dataDirectory: Path
    val httpClient: OkHttpClient
    val gson: Gson

    // Сервисы
    val authService: IAuthService
    val integrityService: IFileIntegrityService
    val downloadService: IFileDownloadService
    val manifestProcessorService: IManifestProcessorService
    val launcherService: ILauncherService
    val serverListService: IServerListService
    val settingsService: ISettingsService
    val profileManager: ProfileManager
    val javaManagerService: JavaManagerService
    val credentialsManager: CredentialsManager

    init {
        val userHome = System.getProperty("user.home")
        this.dataDirectory = Paths.get(userHome, ".aura")

        // Gson
        this.gson = GsonBuilder().setPrettyPrinting().create()

        // --- ВАЖНОЕ ИСПРАВЛЕНИЕ ДЛЯ SOCKS ---
        // Для SOCKS прокси OkHttp полагается на системный Authenticator Java.
        // Без этого он не отправляет логин/пароль прокси-серверу на уровне сокета.
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication("proxyuser", "proxyuserproxyuser".toCharArray())
            }
        })

        // Настройка HTTP клиента
        // Дополнительно оставляем proxyAuthenticator для HTTP-уровня (на всякий случай)
        val okHttpProxyAuthenticator = okhttp3.Authenticator { _, response ->
            val credential = Credentials.basic("proxyuser", "proxyuserproxyuser")
            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }

        this.httpClient = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("proxy.smartycraft.ru", 1080)))
            .proxyAuthenticator(okHttpProxyAuthenticator)
            .build()

        // Базовые сервисы
        this.authService = AuthService(httpClient, gson)
        this.integrityService = FileIntegrityService()
        this.downloadService = FileDownloadService(httpClient, gson)
        this.manifestProcessorService = ManifestProcessorService(gson)
        this.settingsService = SettingsService(gson, dataDirectory.resolve("settings.json"))
        this.credentialsManager = CredentialsManager(dataDirectory, gson)

        // 1. Создаем NetworkService и внедряем его в ServerListService
        val networkService = SmartyNetworkService(httpClient, gson)
        this.serverListService = ServerListService(networkService)

        // 2. Менеджер профилей
        this.profileManager = ProfileManager(dataDirectory, gson)

        // 3. Менеджер Java
        this.javaManagerService = JavaManagerService(dataDirectory, httpClient)

        // 4. Лаунчер Сервис
        this.launcherService = LauncherService(
            manifestProcessorService,
            profileManager,
            javaManagerService
        )
    }
}
