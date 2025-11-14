package hivens.launcher;

import hivens.config.ServiceEndpoints;
import hivens.core.api.IFileDownloadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Реализация сервиса загрузки файлов клиента.
 * Использует OkHttp для загрузки с CDN (определенного в ServiceEndpoints).
 */
public class FileDownloadService implements IFileDownloadService {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);
    private static final int BUFFER_SIZE = 8192; // 8KB буфер

    private final OkHttpClient client;

    /**
     * Инициализирует сервис с OkHttp клиентом (внедрение зависимости).
     * @param client Потокобезопасный OkHttp клиент (предпочтительно синглтон).
     */
    public FileDownloadService(OkHttpClient client) {
        this.client = Objects.requireNonNull(client, "OkHttpClient cannot be null");
    }

    /**
     * {@inheritDoc}
     * <p>
     * URL-кодирует относительный путь для безопасности.
     */
    @Override
    public void downloadFile(String relativePath, Path destinationPath) throws IOException {
        
        // 1. Формируем URL
        String encodedPath = encodeUrlPath(relativePath);
        String url = ServiceEndpoints.CLIENT_DOWNLOAD_BASE + encodedPath;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        log.debug("Downloading: {}", url);

        try (Response response = client.newCall(request).execute()) {
            
            ResponseBody body = response.body();

            if (!response.isSuccessful() || body == null) {
                log.error("Failed to download {}. Code: {}", url, response.code());
                throw new IOException("Failed to download file " + relativePath + ". Code: " + response.code());
            }

            // 2. Создаем родительские директории
            Path parentDir = destinationPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 3. Запись файла (потоковая)
            try (InputStream is = body.byteStream();
                 OutputStream os = Files.newOutputStream(destinationPath)) {
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Эта реализация будет продолжать загрузку, даже если некоторые файлы не удалось скачать,
     * логируя ошибки. Выбрасывает IOException в конце, если была хотя бы одна ошибка.
     */
    @Override
    public int downloadMissingFiles(Path basePath, Map<String, String> filesToDownload, Consumer<String> progressConsumer) throws IOException {
        int successfulDownloads = 0;
        int failedDownloads = 0;
        
        // Потребитель прогресса по умолчанию (no-op), чтобы избежать NullPointerException
        Consumer<String> consumer = (progressConsumer != null) ? progressConsumer : (s) -> {};

        for (Map.Entry<String, String> entry : filesToDownload.entrySet()) {
            String relativePath = entry.getKey();
            Path destinationPath = basePath.resolve(relativePath);

            try {
                consumer.accept(relativePath); // Сообщаем UI о начале загрузки
                downloadFile(relativePath, destinationPath);
                successfulDownloads++;
            } catch (IOException e) {
                log.error("Failed to download file: {}", relativePath, e);
                failedDownloads++;
            }
        }

        log.info("Download task complete. Success: {}, Failed: {}", successfulDownloads, failedDownloads);

        // Если были ошибки, выбрасываем исключение
        if (failedDownloads > 0) {
            throw new IOException("Failed to download " + failedDownloads + " files.");
        }

        return successfulDownloads;
    }

    /**
     * Кодирует сегменты пути URL, сохраняя '/'.
     * Заменяет Windows-разделители и пробелы.
     */
    private String encodeUrlPath(String path) {
        String posixPath = path.replace("\\", "/");
        // Заменяем пробелы на %20 - самый частый случай в старых лаунчерах
        // ПРИМЕЧАНИЕ: Полная реализация URL-кодирования здесь избыточна,
        // т.к. OkHttp не кодирует '+' в '%20' в builder.url().
        return posixPath.replace(" ", "%20");
    }
}