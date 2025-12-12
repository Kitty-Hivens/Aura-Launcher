package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hivens.core.data.AuthStatus;
import hivens.core.data.SessionData;
// Импортируем ваш конфиг с ссылками
import hivens.config.ServiceEndpoints;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AuthService implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final OkHttpClient client;
    private final Gson gson;

    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client, "Client cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
    }

    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        logger.info("Attempting login for user: " + username);

        // 1. Кодируем пароль
        String passwordEncoded = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));

        // 2. Собираем JSON
        Map<String, String> payload = new HashMap<>();
        payload.put("login", username);
        payload.put("password", passwordEncoded);
        payload.put("server", serverId);
        payload.put("osName", System.getProperty("os.name"));
        payload.put("version", "3.6.2");

        String jsonBody = gson.toJson(payload);

        // ИСПОЛЬЗУЕМ ПРАВИЛЬНЫЙ URL ИЗ ВАШЕГО КОНФИГА
        Request request = new Request.Builder()
                .url(ServiceEndpoints.AUTH_LOGIN)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBody))
                .build();

        // 3. Выполняем запрос
        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "HTTP Error: " + response.code());
            }

            if (response.body() == null) {
                throw new IOException("Empty response from server");
            }

            // 4. Читаем сырой ответ
            String rawResponse = response.body().string();
            logger.info("RAW SERVER RESPONSE: " + rawResponse);

            // 5. ФИКС: Очистка от мусора "Array ( )"
            String cleanJson = rawResponse;
            if (!rawResponse.trim().startsWith("{")) {
                int jsonStart = rawResponse.indexOf("{");
                int jsonEnd = rawResponse.lastIndexOf("}");

                if (jsonStart != -1 && jsonEnd != -1) {
                    cleanJson = rawResponse.substring(jsonStart, jsonEnd + 1);
                    logger.info("SANITIZED JSON: " + cleanJson);
                } else {
                    // Если JSON не найден вообще
                    throw new AuthException(AuthStatus.INTERNAL_ERROR, "Server sent invalid data (Not JSON)");
                }
            }

            // 6. Парсим чистый JSON
            try {
                SessionData session = gson.fromJson(cleanJson, SessionData.class);

                if (session == null) {
                    throw new AuthException(AuthStatus.INTERNAL_ERROR, "Authentication failed (Parsed null)");
                }

                return session;

            } catch (JsonSyntaxException e) {
                logger.error("JSON Parsing failed. Content: " + cleanJson);
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "JSON Syntax Error");
            }
        }
    }
}