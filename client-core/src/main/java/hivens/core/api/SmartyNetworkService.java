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

    private static final String PROXY_HOST = "proxy.smartycraft.ru";
    private static final int PROXY_PORT = 1080;
    private static final String PROXY_USER = "proxyuser";
    private static final String PROXY_PASS = "proxyuserproxyuser";

    private static final String API_URL = "https://www.smartycraft.ru/launcher2/index.php";
    // Ссылка на скачивание их лаунчера (на случай обновления)
    private static final String OFFICIAL_JAR_URL = "https://www.smartycraft.ru/downloads/smartycraft.jar";

    // Хеш актуальный на 12.12.2025. Вшиваем его, чтобы не качать файл зря.
    private static String currentHash = "5515a4bdd5f532faf0db61b8263d1952";

    private static final Proxy SMARTY_PROXY = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(PROXY_HOST, PROXY_PORT));

    static {
        System.setProperty("java.net.socks.username", PROXY_USER);
        System.setProperty("java.net.socks.password", PROXY_PASS);
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(PROXY_USER, PROXY_PASS.toCharArray());
            }
        });
    }

    public List<SmartyServer> getServers() {
        try {
            // Попытка 1: С вшитым хешем
            List<SmartyServer> servers = tryGetServers(currentHash);

            if (servers == null) {
                // Если вернулся null, значит сервер ответил "UPDATE"
                System.out.println("Хеш устарел! Пробую получить новый 'на лету'...");
                String newHash = downloadAndCalculateHash();
                if (newHash != null) {
                    currentHash = newHash; // Запоминаем новый хеш
                    servers = tryGetServers(newHash); // Пробуем снова
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
                "\"version\": \"3.6.2\"," +
                "\"cheksum\": \"" + hash + "\"," +
                "\"format\": \"jar\"," +
                "\"testModeKey\": \"false\"," +
                "\"debug\": \"false\"" +
                "}";

        String responseJson = sendPostRequest("loader", jsonBody);

        // Чистим от PHP-мусора
        int jsonStart = responseJson.indexOf("{");
        if (jsonStart != -1) {
            responseJson = responseJson.substring(jsonStart);
        }

        Gson gson = new Gson();
        SmartyResponse response = gson.fromJson(responseJson, SmartyResponse.class);

        if ("UPDATE".equals(response.status)) {
            return null; // Сигнал, что нужен новый хеш
        }

        if ("OK".equals(response.status) && response.servers != null) {
            return response.servers;
        }

        return Collections.emptyList();
    }

    // Скачивает их лаунчер во временную папку и считает хеш
    private String downloadAndCalculateHash() {
        try {
            Path tempJar = Files.createTempFile("smarty_temp", ".jar");
            System.out.println("Скачивание обновления для расчета хеша...");

            // Качаем через прокси (или напрямую, если сайт доступен)
            URL url = new URL(OFFICIAL_JAR_URL);
            // Можно использовать обычный openConnection(), если сайт не банит,
            // но лучше через тот же прокси для надежности
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(SMARTY_PROXY);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000); // Файл может быть большим

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tempJar, StandardCopyOption.REPLACE_EXISTING);
            }

            // Считаем MD5
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream fis = Files.newInputStream(tempJar)) {
                byte[] byteArray = new byte[1024];
                int bytesCount;
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
            }

            // Удаляем мусор
            Files.deleteIfExists(tempJar);

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            String newHash = sb.toString();
            System.out.println("Новый хеш получен: " + newHash);
            return newHash;

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
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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