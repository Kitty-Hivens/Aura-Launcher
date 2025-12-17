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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import hivens.core.util.ZipUtils;

public record FileDownloadService(OkHttpClient client, Gson gson) implements IFileDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadService.class);
    private static final String DOWNLOAD_BASE_URL = "https://www.smartycraft.ru/launcher/clients/";

    /**
     * Список стандартных папок Minecraft, которые НЕЛЬЗЯ трогать.
     * Если папка начинается НЕ с этого списка -> значит это название клиента, и его надо отрезать.
     */
    private static final Set<String> STANDARD_DIRS = Set.of(
            "mods", "config", "bin", "assets", "libraries", "libraries-1.12.2", "libraries-1.7.10",
            "scripts", "resources", "saves", "resourcepacks", "shaderpacks", "texturepacks", "coremods", "natives"
    );

    public void processSession(SessionData session, String serverId, Path targetDir, String extraCheckSum, Set<String> ignoredFiles, Consumer<String> messageUI, BiConsumer<Integer, Integer> progressUI) throws IOException {
        logger.info("Начало загрузки сессии для: {} -> {}", serverId, targetDir);

        JsonElement clientJson = gson.toJsonTree(session.fileManifest());
        if (!clientJson.isJsonObject()) throw new IOException("Ошибка манифеста: не JSON объект");

        Files.createDirectories(targetDir);

        Map<String, String> filesToDownload = new HashMap<>();
        flattenJsonTree(clientJson.getAsJsonObject(), "", filesToDownload);

        // [FIX] Фильтрация модов (Модуль 2)
        if (ignoredFiles != null && !ignoredFiles.isEmpty()) {
            int before = filesToDownload.size();
            filesToDownload.entrySet().removeIf(entry -> {
                // Проверяем "очищенное" имя файла
                String cleanPath = sanitizePath(entry.getKey());
                return ignoredFiles.stream().anyMatch(ignoredName -> cleanPath.endsWith("/" + ignoredName) || cleanPath.equals(ignoredName));
            });
            logger.info("Отфильтровано файлов: {}", before - filesToDownload.size());
        }

        logger.info("Файлов к загрузке: {}", filesToDownload.size());
        if (messageUI != null) messageUI.accept("Проверка файлов...");

        // Скачивание
        int downloaded = downloadFiles(targetDir, filesToDownload, messageUI, progressUI);

        // Обработка extra.zip (Конфиги)
        handleExtraZip(targetDir, filesToDownload, extraCheckSum, messageUI);

        logger.info("Загрузка завершена. Скачано: {}", downloaded);
        if (messageUI != null) messageUI.accept("Готово! Обновление завершено.");
        if (progressUI != null) progressUI.accept(filesToDownload.size(), filesToDownload.size());
    }

    /**
     * [FIX] Главная магия: Превращает "Industrial/mods/jei.jar" -> "mods/jei.jar"
     */
    private String sanitizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) return rawPath;

        // Разбиваем путь на части
        String[] parts = rawPath.split("/");
        
        // Если путь состоит из 1 части ("file.txt") - оставляем
        if (parts.length < 2) return rawPath;

        String firstDir = parts[0];

        // Если первая папка — это "bin", "mods" и т.д. — оставляем как есть.
        if (STANDARD_DIRS.contains(firstDir) || firstDir.startsWith("natives") || firstDir.startsWith("libraries")) {
            return rawPath;
        }

        // Иначе считаем, что это "Industrial" или другой мусорный префикс.
        // Отрезаем его: берем подстроку после первого слэша.
        return rawPath.substring(firstDir.length() + 1);
    }

    private void handleExtraZip(Path targetDir, Map<String, String> allFiles, String serverProfileExtraCheckSum, Consumer<String> messageUI) {
        // Ищем extra.zip (теперь проверяем с учетом sanitizePath)
        String extraKey = allFiles.keySet().stream()
                .filter(k -> sanitizePath(k).endsWith("extra.zip"))
                .findFirst()
                .orElse(null);

        if (extraKey == null) return;

        // Важно: скачанный файл лежит по "чистому" пути
        Path localZipPath = targetDir.resolve(sanitizePath(extraKey));

        boolean needUnzip = false;
        if (Files.exists(localZipPath)) {
            if (serverProfileExtraCheckSum != null && !serverProfileExtraCheckSum.isEmpty()) {
                try {
                    String localHash = getFileChecksum(localZipPath);
                    if (!localHash.equalsIgnoreCase(serverProfileExtraCheckSum)) needUnzip = true;
                } catch (Exception e) { needUnzip = true; }
            }
        } else {
            needUnzip = true;
        }

        if (needUnzip) {
             if (messageUI != null) messageUI.accept("Применение настроек...");
             // Ждем завершения скачивания (файл мог еще не докачаться в потоке, но здесь мы после downloadFiles)
             if (Files.exists(localZipPath)) {
                 try {
                     // Распаковываем в корень клиента
                     ZipUtils.unzip(localZipPath.toFile(), targetDir.toFile());
                 } catch (IOException e) {
                     logger.error("Ошибка распаковки extra.zip", e);
                 }
             }
        }
    }

    private int downloadFiles(Path basePath, Map<String, String> filesToDownload, Consumer<String> messageUI, BiConsumer<Integer, Integer> progressUI) throws IOException {
        AtomicInteger downloadedCount = new AtomicInteger(0);
        int total = filesToDownload.size();
        AtomicInteger current = new AtomicInteger(0);

        for (Map.Entry<String, String> entry : filesToDownload.entrySet()) {
            if (progressUI != null) progressUI.accept(current.get(), total);

            String rawPath = entry.getKey();     // "Industrial/mods/mod.jar"
            String expectedMd5 = entry.getValue();

            // [FIX] Определяем правильный локальный путь (без "Industrial")
            String cleanPath = sanitizePath(rawPath);
            Path targetFile = basePath.resolve(cleanPath);

            // Удаленный путь оставляем как есть, чтобы сервер нас понял
            // (хотя скорее всего сервер понимает и так, но для надежности берем rawPath)
            // ИЛИ если мы скачиваем по-абсолютному URL, нам нужен rawPath.
            // SCOLD обычно хранит файлы по путям из манифеста.
            
            // UI
            if (messageUI != null && (current.get() % 10 == 0 || current.get() == total)) {
                messageUI.accept("Загрузка: " + shortPath(cleanPath));
            }

            if (needDownload(targetFile, expectedMd5)) {
                try {
                    downloadFile(rawPath, targetFile); // Качаем raw -> кладем в clean
                    downloadedCount.incrementAndGet();
                } catch (IOException e) {
                    logger.error("Ошибка скачивания: {}", rawPath, e);
                    // throw e; // Можно не ронять весь лаунчер из-за одного файла
                }
            }
            current.incrementAndGet();
        }
        return downloadedCount.get();
    }

    @Override
    public void downloadFile(String relativePath, Path destinationPath) throws IOException {
        String fileUrl = getFileUrl(relativePath);
        if (destinationPath.getParent() != null) Files.createDirectories(destinationPath.getParent());

        Request request = new Request.Builder().url(fileUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty body");

            try (InputStream is = body.byteStream();
                 OutputStream os = new FileOutputStream(destinationPath.toFile())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
            }
        }
    }

    @NotNull
    private static String getFileUrl(String relativePath) {
        // Здесь relativePath - это "Industrial/mods/..." (rawPath)
        return DOWNLOAD_BASE_URL + relativePath.replace(" ", "%20");
    }

    private boolean needDownload(Path file, String expectedMd5) {
        if (!Files.exists(file)) return true;
        if (Files.isDirectory(file)) return false;
        if ("any".equalsIgnoreCase(expectedMd5) || expectedMd5 == null) return false;
        try {
            if (Files.size(file) == 0) return true;
            String localMd5 = getFileChecksum(file);
            return !localMd5.equalsIgnoreCase(expectedMd5);
        } catch (Exception e) {
            return true;
        }
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
                flattenJsonTree(entry.getValue().getAsJsonObject(), 
                    currentPath.isEmpty() ? dirName : currentPath + "/" + dirName, filesMap);
            }
        }
    }

    private String getFileChecksum(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String shortPath(String path) {
        return path.length() > 40 ? "..." + path.substring(path.length() - 40) : path;
    }
}