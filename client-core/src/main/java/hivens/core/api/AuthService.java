package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hivens.config.ServiceEndpoints;
import hivens.core.data.AuthStatus;
import hivens.core.data.SessionData;
import okhttp3.MediaType;
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
 * Синхронная реализация сервиса аутентификации на основе OkHttp и Gson.
 * Выполняет сетевой запрос и маппинг ответа на SessionData.
 */
public class AuthService implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Зависимости внедряются через конструктор (DI)
    private final OkHttpClient client;
    private final Gson gson;

    /**
     * Инициализирует сервис с необходимыми клиентами.
     * @param client Потокобезопасный OkHttp клиент (предпочтительно синглтон).
     * @param gson Потокобезопасный Gson (предпочтительно синглтон).
     */
    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client, "OkHttpClient cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Выполняет POST-запрос, сериализуя учетные данные в JSON.
     * В случае неуспешного HTTP-кода или статуса не-OK, выбрасывает AuthException.</p>
     */
    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        
        // 1. Создание JSON тела запроса (сухой, высокопроизводительный подход)
        // Использование Map обеспечивает правильную сериализацию ключей JSON
        Map<String, String> payload = new HashMap<>(3);
        payload.put("username", username);
        payload.put("password", password);
        payload.put("serverId", serverId);

        String jsonPayload = gson.toJson(payload);
        RequestBody body = RequestBody.create(jsonPayload, JSON);

        // 2. Сборка OkHttp Request
        Request request = new Request.Builder()
                .url(ServiceEndpoints.AUTH_LOGIN)
                .post(body)
                .header("Accept", "application/json")
                .build();
        
        log.debug("Executing POST to {}", ServiceEndpoints.AUTH_LOGIN);

        // 3. Выполнение запроса
        try (Response response = client.newCall(request).execute()) {
            
            // 4. Обработка ответа
            ResponseBody responseBody = response.body();

            // Критическая ошибка: нет тела ответа или неудачный HTTP-статус
            if (!response.isSuccessful() || responseBody == null) {
                log.error("HTTP request failed: Code={}, Message={}", response.code(), response.message());
                throw new IOException("Unexpected HTTP response code: " + response.code());
            }

            // 5. Десериализация ответа
            String responseString = responseBody.string();
            log.debug("Received response body: {}", responseString);

            SessionData sessionData = gson.fromJson(responseString, SessionData.class);

            // 6. Проверка статуса в теле ответа
            if (sessionData == null) {
                throw new IOException("Failed to parse server response (null session data)");
            }

            if (sessionData.getStatus() != AuthStatus.OK) {
                String msg = String.format("Authentication failed: Server returned status %s", sessionData.getStatus());
                log.warn(msg);
                throw new AuthException(sessionData.getStatus(), msg);
            }

            log.info("Authentication successful for user: {}", sessionData.getPlayerName());
            return sessionData;

        } catch (JsonSyntaxException e) {
            // Ошибка десериализации (ответ сервера не является валидным JSON)
            log.error("Failed to parse server JSON response", e);
            throw new IOException("Invalid server response format.", e);
        }
    }
}