package hivens.core.api;

import com.google.gson.Gson;
import hivens.core.api.dto.SmartyResponse;
import hivens.core.api.dto.SmartyServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

public class SmartyNetworkService {

    private static final String API_URL = "https://www.smartycraft.ru/launcher2/index.php";
    private static final String OFFICIAL_JAR_URL = "https://www.smartycraft.ru/downloads/smartycraft.jar";
    private static final String CACHED_HASH_FILE = "smarty_hash.cache";

    // Дефолтный хеш (можно обновлять вручную при релизах)
    private static String currentHash = "5515a4bdd5f532faf0db61b8263d1952";

    // Прокси настройки
    private static final Proxy SMARTY_PROXY = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.smartycraft.ru", 1080));

    static {
        // Установка аутентификатора для URLConnection
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("proxyuser", "proxyuserproxyuser".toCharArray());
            }
        });
    }

    public SmartyNetworkService() {
        // Пытаемся загрузить кэшированный хеш с диска
        File cache = new File(CACHED_HASH_FILE);
        if (cache.exists()) {
            try {
                currentHash = Files.readString(cache.toPath()).trim();
            } catch (IOException ignored) {}
        }
    }

    public List<SmartyServer> getServers() {
        try {
            // Попытка 1: С текущим хешем
            List<SmartyServer> servers = tryGetServers(currentHash);

            if (servers == null) {
                System.out.println("Сервер требует обновления хеша...");
                String newHash = downloadAndCalculateHash();
                if (newHash != null) {
                    currentHash = newHash;
                    // Сохраняем новый хеш на будущее
                    try {
                        Files.writeString(Path.of(CACHED_HASH_FILE), newHash);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    servers = tryGetServers(newHash);
                }
            }
            return servers != null ? servers : Collections.emptyList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<SmartyServer> tryGetServers(String hash) throws IOException {
        String jsonBody = "{" +
                "\"version\": \"3.6.2\"," + // Версия которую мы эмулируем
                "\"cheksum\": \"" + hash + "\"," +
                "\"format\": \"jar\"," +
                "\"testModeKey\": \"false\"," +
                "\"debug\": \"false\"" +
                "}";

        String responseJson = sendPostRequest("loader", jsonBody);

        // Очистка ответа
        int jsonStart = responseJson.indexOf("{");
        if (jsonStart != -1) {
            responseJson = responseJson.substring(jsonStart);
        }

        try {
            Gson gson = new Gson();
            SmartyResponse response = gson.fromJson(responseJson, SmartyResponse.class);

            if ("UPDATE".equals(response.status)) {
                return null; // Триггер для пересчета хеша
            }
            if ("OK".equals(response.status)) {
                return response.servers;
            }
        } catch (Exception e) {
            System.err.println("Ошибка парсинга: " + responseJson);
        }
        return Collections.emptyList();
    }

    private String downloadAndCalculateHash() {
        try {
            Path tempJar = Files.createTempFile("smarty_temp", ".jar");
            System.out.println("Скачивание официального лаунчера для обхода защиты...");

            URL url = new URL(OFFICIAL_JAR_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(SMARTY_PROXY);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tempJar, StandardCopyOption.REPLACE_EXISTING);
            }

            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream fis = Files.newInputStream(tempJar)) {
                byte[] byteArray = new byte[1024];
                int bytesCount;
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
            }
            Files.deleteIfExists(tempJar);

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Не удалось получить новый хеш: " + e.getMessage());
            return null;
        }
    }

    private String sendPostRequest(String action, String jsonBody) throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(SMARTY_PROXY);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        // Важные заголовки для маскировки
        conn.setRequestProperty("User-Agent", "SMARTYlauncher/3.6.2");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String urlParameters = "action=" + action + "&json=" + URLEncoder.encode(jsonBody, StandardCharsets.UTF_8);

        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.writeBytes(urlParameters);
            wr.flush();
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            return response.toString();
        }
    }
}