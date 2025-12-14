package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import hivens.core.data.AuthStatus;
import hivens.core.data.FileManifest;
import hivens.core.data.SessionData;
import hivens.config.ServiceEndpoints;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class AuthService implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final OkHttpClient client;
    private final Gson gson;

    // Внутренний класс для парсинга ответа (нам нужен uid, которого нет в SessionData)
    private static class AuthResponse {
        @SerializedName("status") AuthStatus status;
        @SerializedName("playername") String playername;
        @SerializedName("uid") String uid;       // <--- Критически важно для генерации токена
        @SerializedName("uuid") String uuid;
        @SerializedName("session") String session; // <--- Это зашифрованный V3 токен
        @SerializedName("client") FileManifest client;
    }

    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client);
        this.gson = Objects.requireNonNull(gson);
    }

    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        logger.info("Logging in via V3 API (with token decryption)...");

        String passwordEncoded = getMD5(password);
        String clientSessionId = UUID.randomUUID().toString().replace("-", "");

        // Payload как в оригинале
        Map<String, Object> payload = new HashMap<>();
        payload.put("login", username);
        payload.put("password", passwordEncoded);
        payload.put("server", serverId);
        payload.put("session", clientSessionId);
        payload.put("mac", generateRandomMac());
        payload.put("osName", System.getProperty("os.name"));
        payload.put("osBitness", System.getProperty("os.arch").contains("64") ? 64 : 32);
        payload.put("javaVersion", System.getProperty("java.version"));
        payload.put("javaBitness", Integer.getInteger("sun.arch.data.model", 64));
        payload.put("javaHome", System.getProperty("java.home"));
        payload.put("classPath", "smartycraft.jar");
        payload.put("rtCheckSum", "d41d8cd98f00b204e9800998ecf8427e");

        RequestBody formBody = new FormBody.Builder()
                .add("action", "login")
                .add("json", gson.toJson(payload))
                .build();

        Request request = new Request.Builder()
                .url(ServiceEndpoints.AUTH_LOGIN)
                .post(formBody)
                .header("User-Agent", "SMARTYlauncher/3.6.2")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new AuthException(AuthStatus.INTERNAL_ERROR, "HTTP " + response.code());

            String rawResponse = response.body().string();
            // Чистка JSON
            int start = rawResponse.indexOf("{");
            int end = rawResponse.lastIndexOf("}");
            if (start != -1 && end != -1) rawResponse = rawResponse.substring(start, end + 1);

            try {
                AuthResponse authResp = gson.fromJson(rawResponse, AuthResponse.class);
                if (authResp == null) throw new AuthException(AuthStatus.INTERNAL_ERROR, "Empty response");
                if (authResp.status != null && authResp.status != AuthStatus.OK) {
                    throw new AuthException(authResp.status, "API Error: " + authResp.status);
                }

                // === МАГИЯ РАСШИФРОВКИ ТОКЕНА ===
                // Берем зашифрованный токен (session) и UID, и превращаем в игровой токен
                String finalGameToken = generateGameToken(authResp.uid, authResp.session);
                logger.info("Generated Game Token: " + finalGameToken);

                String uuid = authResp.uuid;
                if (uuid != null) uuid = uuid.replace("-", "");

                return new SessionData(
                        authResp.status,
                        authResp.playername,
                        uuid,
                        finalGameToken, // <-- ОТДАЕМ РАСШИФРОВАННЫЙ ТОКЕН
                        authResp.client
                );

            } catch (JsonSyntaxException e) {
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "JSON Error");
            }
        }
    }

    // Алгоритм из ru.smartycraft.h.c()
    private String generateGameToken(String uid, String sessionV3) {
        try {
            if (sessionV3 == null || uid == null) return sessionV3;

            // 1. Генерируем ключ: MD5(uid + соль).substring(0, 16)
            String salt = "sdgsdfhgosd8dfrg";
            String keyHash = getMD5(uid + salt);
            String key = keyHash.substring(0, 16);

            // 2. Расшифровываем токен (AES)
            String decrypted = decryptAES(sessionV3, key);

            // 3. Хешируем результат (MD5)
            String hash1 = getMD5(decrypted);

            // 4. Финальный хеш: MD5(hash1 + hash1.substring(length-3))
            // Это логика из ce.a(var, 3)
            String suffix = hash1.length() >= 3 ? hash1.substring(hash1.length() - 3) : "";
            return getMD5(hash1 + suffix);

        } catch (Exception e) {
            logger.error("Failed to generate game token", e);
            // Если расшифровка не удалась, возвращаем оригинал (на всякий случай)
            return sessionV3;
        }
    }

    private String decryptAES(String base64Cipher, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedBytes = Base64.getDecoder().decode(base64Cipher);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
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
