package hivens.core.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hivens.config.ServiceEndpoints;
import hivens.core.data.ServerListResponse;
import okhttp3.HttpUrl; // <-- ИМПОРТ ДОБАВЛЕН
import okhttp3.OkHttpClient;
import okhttp3.Request;
// import okhttp3.RequestBody; // <-- УДАЛЕНО
// import okhttp3.FormBody; // <-- УДАЛЕНО
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Реализация сервиса IServerListService.
 * Загружает список серверов (сборок).
 */
public class ServerListService implements IServerListService {

    private static final Logger log = LoggerFactory.getLogger(ServerListService.class);

    private static final String ACTION_NAME = "servers";

    private final OkHttpClient client;
    private final Gson gson;

    public ServerListService(OkHttpClient client, Gson gson) {
        this.client = Objects.requireNonNull(client, "OkHttpClient cannot be null");
        this.gson = Objects.requireNonNull(gson, "Gson cannot be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerListResponse getServerList() throws IOException {

        // 1. ИСПРАВЛЕНИЕ: Собираем URL с GET-параметром ?action=servers
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(ServiceEndpoints.LAUNCHER_API))
                .newBuilder()
                .addQueryParameter("action", ACTION_NAME)
                // .addQueryParameter("json", "{}") // (Пока не добавляем)
                .build();

        // 2. Сборка OkHttp Request
        Request request = new Request.Builder()
                .url(url) // Используем собранный URL
                .get() // <-- ИСПРАВЛЕНИЕ: Меняем POST на GET
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build();

        log.debug("Executing GET to {} (Action: {})", url, ACTION_NAME);

        // 3. Выполнение запроса
        try (Response response = client.newCall(request).execute()) {

            ResponseBody responseBody = response.body();

            if (!response.isSuccessful() || responseBody == null) {
                log.error("HTTP request failed: Code={}, Message={}", response.code(), response.message());
                throw new IOException("Unexpected HTTP response code: " + response.code());
            }

            // 4. Десериализация ответа
            String responseString = responseBody.string();
            log.debug("Received response body: {}", responseString);

            ServerListResponse serverList = gson.fromJson(responseString, ServerListResponse.class);

            if (serverList == null || serverList.servers() == null) {
                throw new IOException("Failed to parse server list (null response)");
            }

            log.info("Server list loaded. Total servers: {}", serverList.servers().size());
            return serverList;

        } catch (JsonSyntaxException e) {
            log.error("Failed to parse server JSON response", e);
            throw new IOException("Invalid server response format.", e);
        }
    }
}