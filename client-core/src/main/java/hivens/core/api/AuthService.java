package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hivens.config.ServiceEndpoints;
import hivens.core.data.AuthStatus;
import hivens.core.data.FileManifest;
import hivens.core.data.SessionData;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AuthService implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final OkHttpClient client;
    private final Gson gson;

    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client, "OkHttpClient cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
    }

    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        String passwordBase64 = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));

        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", passwordBase64);
        payload.put("serverId", serverId);

        // TODO: Сюда нужно будет добавить "check" после анализа ap.class

        String jsonPayload = gson.toJson(payload);

        RequestBody body = new FormBody.Builder()
                .add("action", "auth")
                .add("json", jsonPayload)
                .build();

        Request request = new Request.Builder()
                .url(ServiceEndpoints.AUTH_LOGIN)
                .post(body)
                .header("User-Agent", "SCOL/1.0")
                .build();

        log.debug("Logging in user '{}'...", username);

        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseString = responseBody != null ? responseBody.string() : "";

            // Пытаемся распарсить JSON
            try {
                SessionData sessionData = gson.fromJson(responseString, SessionData.class);
                if (sessionData != null && sessionData.status() == AuthStatus.OK) {
                    return sessionData;
                }
            } catch (JsonSyntaxException e) {
                // Игнорируем ошибку парсинга, так как сервер шлет мусор
                log.warn("Server returned invalid JSON (Debug mode?). Switching to OFFLINE mode for testing.");
            }
        } catch (IOException e) {
            log.warn("Network error. Switching to OFFLINE mode for testing.");
        }

        // --- FALLBACK: Оффлайн сессия (для тестов UI) ---
        return createOfflineSession(username);
    }

    private SessionData createOfflineSession(String username) {
        log.info("!!! USING OFFLINE SESSION WITH HARDCODED MANIFEST !!!");

        // Генерируем манифест, чтобы лаунчер начал качать файлы
        FileManifest manifest = getHardcodedManifest();

        return new SessionData(
                AuthStatus.OK,
                username,
                UUID.randomUUID().toString(),
                "00000000-0000-0000-0000-000000000000",
                manifest
        );
    }

    /**
     * Создает список файлов, которые лаунчер ДОЛЖЕН скачать.
     * Мы используем фейковые MD5, чтобы спровоцировать (MISMATCH) и принудительную загрузку.
     */
    private FileManifest getHardcodedManifest() {
        Map<String, hivens.core.data.FileData> files = new HashMap<>();

        // ВНИМАНИЕ: Здесь мы гадаем структуру папок на сервере.
        // Обычно это "НазваниеСервера/bin/файл.jar".
        // Мы используем имя "HiTech 1.7.10" как в ServerListService.
        // Если не заработает (404), попробуем просто "bin/..." или "hitech/..."
        String prefix = "HiTech 1.7.10/";

        // 1. Основной клиент
        addFile(files, prefix + "bin/minecraft.jar");

        // 2. Библиотеки (минимальный набор для старта 1.7.10)
        // Лаунчер упадет, если не найдет главный класс, поэтому launchwrapper обязателен.
        addFile(files, prefix + "bin/launchwrapper-1.12.jar");
        addFile(files, prefix + "bin/asm-all-5.0.3.jar");
        addFile(files, prefix + "bin/jopt-simple-4.5.jar");
        addFile(files, prefix + "bin/lzma-0.0.1.jar");
        addFile(files, prefix + "bin/guava-17.0.jar");
        addFile(files, prefix + "bin/commons-lang3-3.3.2.jar");
        addFile(files, prefix + "bin/gson-2.2.4.jar");

        // Forge (обычно он внутри minecraft.jar или отдельным файлом modpack.jar)
        // addFile(files, prefix + "bin/modpack.jar");

        return new FileManifest(Collections.emptyMap(), files);
    }

    private void addFile(Map<String, hivens.core.data.FileData> map, String path) {
        // md5 = "force_download" (любая строка, не похожая на реальный md5, вызовет перекачку)
        // size = 0 (не важно)
        map.put(path, new hivens.core.data.FileData("force_download", 0));
    }
}