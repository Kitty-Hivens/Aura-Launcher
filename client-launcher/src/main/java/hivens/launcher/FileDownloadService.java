package hivens.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hivens.core.api.IFileDownloadService;
import hivens.core.data.SessionData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FileDownloadService implements IFileDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadService.class);
    private final OkHttpClient client;
    private final Gson gson;

    // Базовый URL
    private static final String DOWNLOAD_BASE_URL = "https://www.smartycraft.ru/launcher/clients/";

    // Глобальная папка обновлений
    private final Path globalUpdatesDir;

    // --- ЧЕРНЫЙ СПИСОК (Опциональные/Конфликтные моды) ---
    // Лаунчер будет ИГНОРИРОВАТЬ эти файлы при скачивании.
    private static final List<String> OPTIONAL_MODS_BLACKLIST = List.of(
            "ReplayMod",
            "OptiFine",
            "FoamFix",
            "BetterFps",
            "TexFix",
            "DiscordRP",
            "ConnectedTexturesMod",
            "Chisel"
    );

    public FileDownloadService(OkHttpClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
        this.globalUpdatesDir = Paths.get(System.getProperty("user.home"), ".SCOL", "updates");
    }

    /**
     * Точка входа для контроллера.
     */
    public void processSession(SessionData session, String serverId, Consumer<String> progressUI) throws IOException {
        logger.info("Processing session for server: " + serverId);

        JsonElement clientJson = gson.toJsonTree(session.fileManifest());
        if (!clientJson.isJsonObject()) {
            throw new IOException("Client data in session is not a JSON object");
        }

        // Папка конкретного сервера
        Path serverBaseDir = globalUpdatesDir.resolve(serverId);
        Files.createDirectories(serverBaseDir);

        // 1. Превращаем дерево JSON в плоскую карту
        Map<String, String> filesToDownload = new HashMap<>();
        flattenJsonTree(clientJson.getAsJsonObject(), "", filesToDownload);

        logger.info("Total files found in manifest: " + filesToDownload.size());
        if (progressUI != null) progressUI.accept("Проверка целостности...");

        // 2. Вызываем метод загрузки
        int downloaded = downloadMissingFiles(serverBaseDir, filesToDownload, progressUI);

        logger.info("Download complete. Files downloaded: " + downloaded);
        if (progressUI != null) progressUI.accept("Готово! Загружено файлов: " + downloaded);
    }

    @Override
    public void downloadFile(String relativePath, Path destinationPath) throws IOException {
        String fileUrl = getFileUrl(relativePath);

        if (destinationPath.getParent() != null) {
            Files.createDirectories(destinationPath.getParent());
        }

        logger.debug("Downloading: {} -> {}", fileUrl, destinationPath);

        Request request = new Request.Builder().url(fileUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + fileUrl);
            }

            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty body for " + fileUrl);

            try (InputStream is = body.byteStream();
                 OutputStream os = new FileOutputStream(destinationPath.toFile())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }
    }

    @NotNull
    private static String getFileUrl(String relativePath) {
        return DOWNLOAD_BASE_URL + relativePath.replace(" ", "%20");
    }

    @Override
    public int downloadMissingFiles(Path basePath, Map<String, String> filesToDownload, Consumer<String> progressConsumer) throws IOException {
        AtomicInteger downloadedCount = new AtomicInteger(0);
        int total = filesToDownload.size();
        AtomicInteger current = new AtomicInteger(0);

        for (Map.Entry<String, String> entry : filesToDownload.entrySet()) {
            String relPath = entry.getKey();
            String expectedMd5 = entry.getValue();
            Path targetFile = basePath.resolve(relPath);

            // --- ФИЛЬТР: Пропускаем опасные моды ---
            boolean isBanned = OPTIONAL_MODS_BLACKLIST.stream()
                    .anyMatch(banned -> relPath.contains(banned));

            if (isBanned) {
                logger.info("🚫 SKIPPING unstable/optional mod: {}", relPath);
                // Увеличиваем счетчик прогресса, чтобы UI не завис
                if (progressConsumer != null) {
                    current.incrementAndGet();
                }
                continue; // Не качаем!
            }
            // ---------------------------------------

            // Обновляем UI
            if (progressConsumer != null) {
                int c = current.incrementAndGet();
                if (c % 5 == 0 || c == total) {
                    progressConsumer.accept(String.format("Загрузка: %d/%d (%s)", c, total, relPath));
                }
            }

            // Проверка и загрузка
            if (needDownload(targetFile, expectedMd5)) {
                try {
                    downloadFile(relPath, targetFile);
                    downloadedCount.incrementAndGet();
                } catch (IOException e) {
                    logger.error("Failed to download: {}", relPath, e);
                    throw e;
                }
            }
        }
        return downloadedCount.get();
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    private void flattenJsonTree(JsonObject dirObject, String currentPath, Map<String, String> filesMap) {
        if (dirObject.has("files")) {
            JsonObject files = dirObject.getAsJsonObject("files");
            for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
                String fileName = entry.getKey();
                JsonObject fileInfo = entry.getValue().getAsJsonObject();
                String md5 = fileInfo.has("md5") ? fileInfo.get("md5").getAsString() : "any";

                String relPath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;
                filesMap.put(relPath, md5);
            }
        }

        if (dirObject.has("directories")) {
            JsonObject directories = dirObject.getAsJsonObject("directories");
            for (Map.Entry<String, JsonElement> entry : directories.entrySet()) {
                String dirName = entry.getKey();
                JsonObject subDirObj = entry.getValue().getAsJsonObject();

                String newPath = currentPath.isEmpty() ? dirName : currentPath + "/" + dirName;
                flattenJsonTree(subDirObj, newPath, filesMap);
            }
        }
    }

    private boolean needDownload(Path file, String expectedMd5) {
        if (!Files.exists(file)) return true;
        if (Files.isDirectory(file)) return true;
        if ("any".equalsIgnoreCase(expectedMd5) || expectedMd5 == null) return false;

        try {
            String localMd5 = getFileChecksum(file);
            return !localMd5.equalsIgnoreCase(expectedMd5);
        } catch (Exception e) {
            return true;
        }
    }

    private String getFileChecksum(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}