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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record FileDownloadService(OkHttpClient client, Gson gson) implements IFileDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadService.class);
    // Базовый URL
    private static final String DOWNLOAD_BASE_URL = "https://www.smartycraft.ru/launcher/clients/";

    // --- ЧЕРНЫЙ СПИСОК (Опциональные/Конфликтные моды) ---
    private static final List<String> OPTIONAL_MODS_BLACKLIST = List.of(
            "ReplayMod",
            "FoamFix",
            "BetterFps",
            "TexFix",
            "DiscordRP"
    );

    /**
     * Точка входа для контроллера.
     *
     * @param targetDir  Папка, куда нужно сохранить клиент
     * @param messageUI  Колбэк для текста (что сейчас качаем)
     * @param progressUI Колбэк для прогресса (скачано, всего) [FIX]
     */
    public void processSession(SessionData session, String serverId, Path targetDir, Consumer<String> messageUI, BiConsumer<Integer, Integer> progressUI) throws IOException {
        logger.info("Processing session for server: {} -> {}", serverId, targetDir);

        JsonElement clientJson = gson.toJsonTree(session.fileManifest());
        if (!clientJson.isJsonObject()) {
            throw new IOException("Client data in session is not a JSON object");
        }

        Files.createDirectories(targetDir);

        // 1. Превращаем дерево JSON в плоскую карту
        Map<String, String> filesToDownload = new HashMap<>();
        flattenJsonTree(clientJson.getAsJsonObject(), "", filesToDownload);

        logger.info("Total files found in manifest: {}", filesToDownload.size());
        if (messageUI != null) messageUI.accept("Проверка целостности...");

        // 2. Вызываем метод загрузки
        int downloaded = downloadMissingFiles(targetDir, serverId, filesToDownload, messageUI, progressUI);

        logger.info("Download complete. Files downloaded: {}", downloaded);
        if (messageUI != null) messageUI.accept("Готово! Загружено файлов: " + downloaded);
        // Заполняем прогресс до конца
        if (progressUI != null) progressUI.accept(filesToDownload.size(), filesToDownload.size());
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

    /**
     * Основной метод загрузки с исправлением путей и обновлением прогресса
     */
    public int downloadMissingFiles(Path basePath, String serverId, Map<String, String> filesToDownload, Consumer<String> messageUI, BiConsumer<Integer, Integer> progressUI) throws IOException {
        AtomicInteger downloadedCount = new AtomicInteger(0);
        int total = filesToDownload.size();
        AtomicInteger current = new AtomicInteger(0);

        for (Map.Entry<String, String> entry : filesToDownload.entrySet()) {
            // [FIX] Обновляем прогресс перед обработкой файла
            if (progressUI != null) {
                progressUI.accept(current.get(), total);
            }

            String rawPath = entry.getKey();
            String expectedMd5 = entry.getValue();

            // --- 1. ЛОГИКА ДЛЯ ЛОКАЛЬНОГО ФАЙЛА ---
            String localRelPath = rawPath;
            if (localRelPath.startsWith(serverId + "/")) {
                localRelPath = localRelPath.substring(serverId.length() + 1);
            }
            Path targetFile = basePath.resolve(localRelPath);

            // --- 2. ЛОГИКА ДЛЯ URL ---
            String remoteRelPath = getRelPath(serverId, rawPath);

            // --- ФИЛЬТР ---
            boolean isBanned = OPTIONAL_MODS_BLACKLIST.stream()
                    .anyMatch(localRelPath::contains);

            if (isBanned) {
                logger.info("🚫 SKIPPING unstable/optional mod: {}", relPathForLog(localRelPath));
                current.incrementAndGet();
                continue;
            }

            // Обновляем UI (текст)
            if (messageUI != null) {
                // Обновляем текст реже, чтобы не фризить UI
                if (current.get() % 3 == 0 || current.get() == total) {
                    messageUI.accept(String.format("Загрузка: %d/%d (%s)", current.get() + 1, total, relPathForLog(localRelPath)));
                }
            }

            // Проверка и загрузка
            if (needDownload(targetFile, expectedMd5)) {
                try {
                    downloadFile(remoteRelPath, targetFile);
                    downloadedCount.incrementAndGet();
                } catch (IOException e) {
                    logger.error("Failed to download URL: {}", getFileUrl(remoteRelPath), e);
                    throw e;
                }
            }

            // Увеличиваем счетчик обработанных файлов
            current.incrementAndGet();
        }
        return downloadedCount.get();
    }

    @NotNull
    private static String getRelPath(String serverId, String rawPath) {
        String remoteRelPath = rawPath;
        boolean isSharedFolder = remoteRelPath.startsWith("libraries") ||
                remoteRelPath.startsWith("bin") ||
                remoteRelPath.startsWith("assets");

        if (!isSharedFolder && !remoteRelPath.startsWith(serverId + "/")) {
            remoteRelPath = serverId + "/" + remoteRelPath;
        }
        return remoteRelPath;
    }

    private String relPathForLog(String path) {
        if (path.length() > 30) {
            return "..." + path.substring(path.length() - 30);
        }
        return path;
    }

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
            if (Files.size(file) == 0) return true;
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
