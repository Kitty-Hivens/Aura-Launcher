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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class AuthService implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final OkHttpClient client;
    private final Gson gson;
    // Это хеш SmartyCraft.jar, который требует Legacy-скрипт
    private static final String CLIENT_CHECKSUM = "5515a4bdd5f532faf0db61b8263d1952";

    public AuthService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client);
        this.gson = Objects.requireNonNull(gson);
    }

    @Override
    public SessionData login(String username, String password, String serverId) throws AuthException, IOException {
        logger.info("Starting Hybrid Auth...");

        // 1. V3 Login (получаем Manifest и данные для fallback)
        SessionData sessionV3 = loginV3(username, password, serverId);
        logger.info("V3 Login OK. Got Manifest.");

        // 2. Legacy Login (получаем гарантированно рабочий токен)
        try {
            SessionData sessionV2 = loginLegacy(username, password, serverId);
            logger.info("Legacy Login OK. Token: " + sessionV2.accessToken());

            // 3. Собираем сессию (Token V2, Files V3)
            return new SessionData(
                    sessionV3.status(),
                    sessionV3.playerName(),
                    sessionV2.uuid(),
                    sessionV2.accessToken(),
                    sessionV3.fileManifest()
            );

        } catch (Exception e) {
            logger.error("Legacy login failed (" + e.getMessage() + "). Using V3 token as fallback. This may cause Bad Session.");
            // Если Legacy сломался, отдаем V3 (с полным Hex-токеном) как запасной вариант
            return sessionV3;
        }
    }

    // Метод V3 (Launcher2): для получения манифеста. Токен используем как V3/Full Hex.
    private SessionData loginV3(String username, String password, String serverId) throws AuthException, IOException {
        String passwordEncoded = getMD5(password);
        String randomSession = getMD5(Double.toString(Math.random()));
        String macAddress = generateRandomMac();
        String hwid = getHWID();

        Map<String, String> payload = new HashMap<>();
        payload.put("login", username);
        payload.put("password", passwordEncoded);
        payload.put("server", serverId.toLowerCase());
        payload.put("session", randomSession);
        payload.put("mac", macAddress);
        payload.put("hwid", hwid);

        payload.put("osName", System.getProperty("os.name"));
        payload.put("osBitness", System.getProperty("os.arch").contains("64") ? "64" : "32");
        payload.put("javaVersion", System.getProperty("java.version"));
        payload.put("javaBitness", System.getProperty("sun.arch.data.model", "64"));
        payload.put("javaHome", System.getProperty("java.home"));
        payload.put("classPath", "smartycraft.jar");
        payload.put("version", "3.0.0");
        payload.put("cheksum", CLIENT_CHECKSUM);
        payload.put("format", "jar");

        RequestBody formBody = new FormBody.Builder().add("action", "login").add("json", gson.toJson(payload)).build();
        Request request = new Request.Builder().url(ServiceEndpoints.AUTH_LOGIN).post(formBody).header("User-Agent", "SMARTYlauncher/3.0.0").build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new AuthException(AuthStatus.INTERNAL_ERROR, "HTTP " + response.code());
            String raw = response.body().string();

            int s = raw.indexOf("{"), e = raw.lastIndexOf("}");
            if (s != -1 && e != -1) raw = raw.substring(s, e + 1);
            SessionData session = gson.fromJson(raw, SessionData.class);

            // Токен Base64 -> Full Hex (64 chars) для V3 fallback
            String token = session.accessToken();
            if (token != null && (token.endsWith("=") || token.length() > 32)) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(token);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : decoded) hex.append(String.format("%02x", b));
                    token = hex.toString();
                    logger.info("Converted V3 token to Full HEX (" + token.length() + " chars)");
                } catch (IllegalArgumentException a) {
                    logger.warn("Failed to decode token");
                }
            }

            String uuid = session.uuid();
            if (uuid != null) uuid = uuid.replace("-", "");

            return new SessionData(session.status(), session.playerName(), uuid, token, session.fileManifest());
        }
    }

    // Метод Legacy (Старый протокол): для получения рабочего 32-символьного токена.
    private SessionData loginLegacy(String username, String password, String serverId) throws IOException, AuthException {
        // Мы предполагаем, что скрипт - auth.php
        String url = "http://www.smartycraft.ru/launcher/auth.php";

        // ДОБАВЛЕНЫ: hwid, server, cheksum - недостающие параметры
        RequestBody body = new FormBody.Builder()
                .add("user", username)
                .add("password", getMD5(password)) // <-- ПАРОЛЬ В MD5
                .add("version", "3.0.0")
                .add("hwid", getHWID())
                .add("server", serverId.toLowerCase())
                .add("cheksum", CLIENT_CHECKSUM) // <-- ХЕШ ЛАУНЧЕРА
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "SMARTYlauncher/3.0.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                // Если auth.php не найден, пробуем index.php
                response.close();
                request = request.newBuilder().url("http://www.smartycraft.ru/launcher/index.php").post(body).build();
                try (Response r2 = client.newCall(request).execute()) { return parseLegacy(r2); }
            }
            return parseLegacy(response);
        }
    }

    private SessionData parseLegacy(Response response) throws IOException, AuthException {
        String resp = response.body().string().trim();
        logger.info("LEGACY AUTH RESPONSE: " + resp);

        if (resp.startsWith("OK")) {
            String[] parts = resp.split(":");
            if (parts.length >= 4) {
                // Формат: OK:User:Token:UUID
                // Токен V2 - 32 символа
                return new SessionData(AuthStatus.OK, parts[1], parts[3].replace("-", ""), parts[2], null);
            }
        }
        throw new AuthException(AuthStatus.INTERNAL_ERROR, "Legacy fail: " + resp);
    }

    private String getMD5(String input) { try { MessageDigest md = MessageDigest.getInstance("MD5"); byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); for (byte b : hash) sb.append(String.format("%02x", b)); return sb.toString(); } catch (Exception e) { return ""; } }
    private String getHWID() { try { String s = System.getProperty("os.name") + System.getProperty("user.name") + Runtime.getRuntime().availableProcessors(); return getMD5(s); } catch (Exception e) { return "fallback_hwid"; } }
    private String generateRandomMac() { Random rand = new Random(); byte[] mac = new byte[6]; rand.nextBytes(mac); mac[0] = (byte)(mac[0] & (byte)254); StringBuilder sb = new StringBuilder(18); for (byte b : mac) { if (!sb.isEmpty()) sb.append("-"); sb.append(String.format("%02X", b)); } return sb.toString(); }
}
