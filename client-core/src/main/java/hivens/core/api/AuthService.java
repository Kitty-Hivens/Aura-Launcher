package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hivens.core.data.AuthStatus;
import hivens.core.data.SessionData;
import hivens.config.ServiceEndpoints;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class AuthService implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final OkHttpClient client;
    private final Gson gson;

    // Хеш (из SmartyNetworkService)
    private static final String CLIENT_CHECKSUM = "5515a4bdd5f532faf0db61b8263d1952";

    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client, "Client cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
    }

    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        logger.info("Attempting login for user: " + username);

        // 1. Пароль - MD5
        String passwordEncoded = getMD5(password);

        // 2. Генерируем рандомные данные
        String randomSession = getMD5(Double.toString(Math.random()));
        String macAddress = generateRandomMac();
        String hwid = getHWID();

        // 3. Собираем ПОЛНЫЙ пакет данных
        Map<String, String> payload = new HashMap<>();

        payload.put("login", username);
        payload.put("password", passwordEncoded);
        payload.put("server", serverId.toLowerCase());

        payload.put("session", randomSession);
        payload.put("mac", macAddress);
        payload.put("hwid", hwid);

        // Поля из лога официального лаунчера
        payload.put("osName", System.getProperty("os.name"));
        payload.put("osBitness", System.getProperty("os.arch").contains("64") ? "64" : "32");
        payload.put("javaVersion", System.getProperty("java.version"));
        payload.put("javaBitness", System.getProperty("sun.arch.data.model", "64"));
        payload.put("javaHome", System.getProperty("java.home"));
        payload.put("classPath", "smartycraft.jar");

        payload.put("version", "3.6.2");
        payload.put("cheksum", CLIENT_CHECKSUM);
        payload.put("format", "jar");

        String jsonPayload = gson.toJson(payload);

        // 4. Запрос
        RequestBody formBody = new FormBody.Builder()
                .add("action", "login")
                .add("json", jsonPayload)
                .build();

        Request request = new Request.Builder()
                .url(ServiceEndpoints.AUTH_LOGIN)
                .post(formBody)
                .header("User-Agent", "SMARTYlauncher/3.6.2")
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "HTTP Error: " + response.code());
            }

            String rawResponse = response.body() != null ? response.body().string() : "";
            // Логируем только начало ответа, чтобы не засорять консоль гигабайтами текста
            logger.info("RAW SERVER RESPONSE (First 500 chars): '" +
                    (rawResponse.length() > 500 ? rawResponse.substring(0, 500) + "..." : rawResponse) + "'");

            if (rawResponse.isBlank()) {
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "Server returned EMPTY body.");
            }

            // --- ИСПРАВЛЕННАЯ ОЧИСТКА ---
            // Ищем ПЕРВУЮ скобку, а не последнюю.
            String cleanJson = rawResponse;
            int jsonStart = rawResponse.indexOf("{"); // <--- БЫЛО lastIndexOf, СТАЛО indexOf
            int jsonEnd = rawResponse.lastIndexOf("}");

            if (jsonStart != -1 && jsonEnd != -1 && jsonStart < jsonEnd) {
                cleanJson = rawResponse.substring(jsonStart, jsonEnd + 1);
            }

            // logger.info("SANITIZED JSON: " + cleanJson); // Можно раскомментировать для отладки

            try {
                SessionData session = gson.fromJson(cleanJson, SessionData.class);

                if (session == null) throw new AuthException(AuthStatus.INTERNAL_ERROR, "Parsed null");

                if (session.status() != null && session.status() != AuthStatus.OK) {
                    throw new AuthException(session.status(), "API Error: " + session.status());
                }

                // ВАЖНО: В успешном ответе сервер может не вернуть accessToken в явном виде,
                // а вернуть "session", "uuid" и "playername".
                // Проверяем хотя бы одно поле успешного входа.
                if (session.uuid() == null && session.accessToken() == null) {
                    // Если сервер ответил OK, но токена нет - возможно, он в другом поле?
                    // Но судя по логу, там есть "uuid" и "playername". Считаем это успехом.
                    if (session.playerName() != null) {
                        return session;
                    }
                    throw new AuthException(AuthStatus.INTERNAL_ERROR, "No token/uuid. Raw: " + cleanJson.substring(0, Math.min(100, cleanJson.length())));
                }

                return session;

            } catch (JsonSyntaxException e) {
                logger.error("JSON Error. Start of content: " + cleanJson.substring(0, Math.min(100, cleanJson.length())));
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "JSON Syntax Error");
            }
        }
    }

    private String getMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String getHWID() {
        try {
            String s = System.getProperty("os.name") + System.getProperty("user.name") + Runtime.getRuntime().availableProcessors();
            return getMD5(s);
        } catch (Exception e) { return "fallback_hwid"; }
    }

    private String generateRandomMac() {
        Random rand = new Random();
        byte[] mac = new byte[6];
        rand.nextBytes(mac);
        mac[0] = (byte)(mac[0] & (byte)254);
        StringBuilder sb = new StringBuilder(18);
        for (byte b : mac) {
            if (!sb.isEmpty()) sb.append("-");
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}