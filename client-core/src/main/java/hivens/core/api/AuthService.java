package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import hivens.config.ServiceEndpoints;
import hivens.core.data.AuthStatus;
import hivens.core.data.FileManifest;
import hivens.core.data.SessionData;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AuthService implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    // Данные прокси SmartyCraft
    private static final String PROXY_HOST = "proxy.smartycraft.ru";
    private static final int PROXY_PORT = 1080;
    private static final String PROXY_USER = "proxyuser";
    private static final String PROXY_PASS = "proxyuserproxyuser";

    private final OkHttpClient client;
    private final Gson gson;

    private static class AuthResponse {
        @SerializedName("status")
        AuthStatus status;
        @SerializedName("playername")
        String playername;
        @SerializedName("uid")
        String uid;
        @SerializedName("uuid")
        String uuid;
        @SerializedName("session")
        String session;
        @SerializedName("client")
        FileManifest client;
    }

    public AuthService(OkHttpClient baseClient, Gson gson) {
        this.gson = Objects.requireNonNull(gson);

        // ВАЖНО: Мы создаем новый инстанс клиента на основе переданного,
        // но с ПРИНУДИТЕЛЬНЫМ добавлением их прокси.
        // Обычный OkHttpClient не подхватывает Authenticator.setDefault.
        this.client = baseClient.newBuilder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(PROXY_HOST, PROXY_PORT)))
                .proxyAuthenticator((route, response) -> {
                    String credential = Credentials.basic(PROXY_USER, PROXY_PASS);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                })
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        logger.info("Logging in via V3 API (with SmartyProxy)...");

        String passwordEncoded = getMD5(password);
        String clientSessionId = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> payload = new HashMap<>();
        payload.put("login", username);
        payload.put("password", passwordEncoded);
        payload.put("server", serverId);
        payload.put("session", clientSessionId);
        payload.put("mac", generateRandomMac());
        payload.put("osName", System.getProperty("os.name"));
        // Исправлена логика определения битности
        boolean is64 = System.getProperty("os.arch").contains("64");
        payload.put("osBitness", is64 ? 64 : 32);
        payload.put("javaVersion", System.getProperty("java.version"));
        payload.put("javaBitness", is64 ? 64 : 32);
        payload.put("javaHome", System.getProperty("java.home"));
        // Эмуляция оригинального клиента
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

            assert response.body() != null;
            String rawResponse = response.body().string();

            // SmartyCraft API иногда возвращает мусор перед JSON
            int start = rawResponse.indexOf("{");
            int end = rawResponse.lastIndexOf("}");
            if (start != -1 && end != -1) rawResponse = rawResponse.substring(start, end + 1);

            try {
                AuthResponse authResp = gson.fromJson(rawResponse, AuthResponse.class);
                if (authResp == null) throw new AuthException(AuthStatus.INTERNAL_ERROR, "Empty response");

                if (authResp.status != null && authResp.status != AuthStatus.OK) {
                    throw new AuthException(authResp.status, "API Error: " + authResp.status);
                }

                // Генерация токена для запуска игры
                String finalGameToken = generateGameToken(authResp.uid, authResp.session);
                logger.info("Generated Game Token for UUID: {}", authResp.uuid);

                String uuid = authResp.uuid;
                if (uuid != null) uuid = uuid.replace("-", "");

                return new SessionData(
                        authResp.status,
                        authResp.playername,
                        uuid,
                        finalGameToken,
                        authResp.client
                );

            } catch (JsonSyntaxException e) {
                logger.error("JSON Error: " + rawResponse);
                throw new AuthException(AuthStatus.INTERNAL_ERROR, "JSON Error");
            }
        }
    }

    private String generateGameToken(String uid, String sessionV3) {
        try {
            if (sessionV3 == null || uid == null) return sessionV3;

            String salt = "sdgsdfhgosd8dfrg"; // Соль из оригинального лаунчера
            String keyHash = getMD5(uid + salt);
            String key = keyHash.substring(0, 16);

            String decrypted = decryptAES(sessionV3, key);
            String hash1 = getMD5(decrypted);

            // Логика "соления" хеша из Launcher.java (подразумевается)
            String suffix = hash1.length() >= 3 ? hash1.substring(hash1.length() - 3) : "";
            return getMD5(hash1 + suffix);

        } catch (Exception e) {
            logger.error("Failed to generate game token", e);
            return sessionV3; // Fallback
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
        } catch (Exception e) {
            return "";
        }
    }

    private String generateRandomMac() {
        Random rand = new Random();
        byte[] mac = new byte[6];
        rand.nextBytes(mac);
        mac[0] = (byte) (mac[0] & (byte) 254);
        StringBuilder sb = new StringBuilder(18);
        for (byte b : mac) {
            if (!sb.isEmpty()) sb.append("-");
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}