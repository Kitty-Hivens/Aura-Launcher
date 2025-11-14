package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hivens.config.ServiceEndpoints;
import hivens.core.data.AuthStatus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Синхронная реализация сервиса аутентификации.
 * Исправлено: Отправляет application/x-www-form-urlencoded.
 */
public class AuthService implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // "auth" - наиболее вероятное значение для авторизации.
    private static final String ACTION_NAME = "auth";

    private final OkHttpClient client;
    private final Gson gson;

    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client, "OkHttpClient cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Выполняет POST-запрос, сериализуя учетные данные в JSON
     * и помещая их в поле "json" формы (x-www-form-urlencoded).
     */
    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {

        // 1. Создание JSON-полезной нагрузки (внутренний JSON)
        Map<String, String> payload = new HashMap<>(3);
        payload.put("username", username);
        payload.put("password", password);
        payload.put("serverId", serverId);
        String jsonPayload = gson.toJson(payload);

        // 2. Создание FormBody (внешний контейнер)
        // (На основе as.java: action=...&json=...)
        RequestBody body = new FormBody.Builder()
                .add("action", ACTION_NAME) // (Предположение на основе bl/as.java)
                .add("json", jsonPayload)
                // .add("check", "...") // (Пропускаем 'check', так как не знаем, как он генерируется)
                .build();

        // 3. Сборка OkHttp Request
        Request request = new Request.Builder()
                .url(ServiceEndpoints.AUTH_LOGIN) // (AUTH_LOGIN должен быть "auth/login" или аналогичным)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build();

        log.debug("Executing POST to {} (FormUrlEncoded)", ServiceEndpoints.AUTH_LOGIN);

        // 4. Выполнение запроса
        try (Response response = client.newCall(request).execute()) {

            ResponseBody responseBody = response.body();

            if (!response.isSuccessful() || responseBody == null) {
                log.error("HTTP request failed: Code={}, Message={}", response.code(), response.message());
                throw new IOException("Unexpected HTTP response code: " + response.code());
            }

            // 5. Десериализация ответа
            String responseString = responseBody.string();
            log.debug("Received response body: {}", responseString);

            SessionData sessionData = gson.fromJson(responseString, SessionData.class);

            // 6. Проверка статуса (Бизнес-логика)
            if (sessionData == null) {
                throw new IOException("Failed to parse server response (null session data)");
            }

            if (sessionData.status() != AuthStatus.OK) {
                String msg = String.format("Authentication failed: Server returned status %s", sessionData.status());
                log.warn(msg);
                throw new AuthException(sessionData.status(), msg);
            }

            log.info("Authentication successful for user: {}", sessionData.playerName());
            return sessionData;

        } catch (JsonSyntaxException e) {
            log.error("Failed to parse server JSON response", e);
            throw new IOException("Invalid server response format.", e);
        }
    }
}