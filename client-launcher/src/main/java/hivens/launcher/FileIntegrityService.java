package hivens.launcher;

import hivens.core.api.IFileIntegrityService;
import hivens.core.data.FileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Реализация сервиса проверки целостности файлов.
 * Использует MD5 для вычисления хэшей, как наиболее вероятный алгоритм
 * в оригинальном лаунчере.
 */
public class FileIntegrityService implements IFileIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(FileIntegrityService.class);
    
    // Алгоритм хэширования (MD5 используется для обратной совместимости)
    private static final String HASH_ALGORITHM = "MD5";
    
    // Размер буфера для чтения файлов (оптимизация)
    private static final int BUFFER_SIZE = 8192;

    // Пул MessageDigest для потокобезопасности, если сервис будет синглтоном
    // и использоваться в нескольких потоках (для параллельной проверки).
    private final ThreadLocal<MessageDigest> digestProvider =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance(HASH_ALGORITHM);
                } catch (NoSuchAlgorithmException e) {
                    log.error("Fatal: {} algorithm not supported.", HASH_ALGORITHM, e);
                    throw new RuntimeException(e);
                }
            });

    /**
     * {@inheritDoc}
     * <p>
     * Реализация использует MD5.
     */
    @Override
    public String calculateFileHash(Path filePath) throws IOException {
        MessageDigest digest = digestProvider.get();
        digest.reset();

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return bytesToHex(digest.digest());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileStatus checkFile(Path filePath, String expectedHash) throws IOException {
        Objects.requireNonNull(expectedHash, "Expected hash cannot be null");

        if (!Files.exists(filePath)) {
            log.trace("File MISSING: {}", filePath);
            return FileStatus.MISSING;
        }

        String actualHash = calculateFileHash(filePath);

        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            log.warn("File MISMATCH: {} (Expected: {}, Actual: {})", filePath, expectedHash, actualHash);
            return FileStatus.MISMATCH;
        }

        log.trace("File VALID: {}", filePath);
        return FileStatus.VALID;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Реализация не использует параллелизм для простоты,
     * но может быть легко оптимизирована с использованием ForkJoinPool.
     */
    @Override
    public Map<String, String> verifyIntegrity(Path basePath, Map<String, String> filesToVerify) {
        
        Map<String, String> mismatchedFiles = new HashMap<>();

        for (Map.Entry<String, String> entry : filesToVerify.entrySet()) {
            String relativePath = entry.getKey();
            String expectedHash = entry.getValue();
            Path absolutePath = basePath.resolve(relativePath);

            try {
                FileStatus status = checkFile(absolutePath, expectedHash);
                
                if (status != FileStatus.VALID) {
                    // Файл отсутствует или поврежден, его нужно загрузить
                    mismatchedFiles.put(relativePath, expectedHash);
                }
            } catch (IOException e) {
                log.error("Failed to check integrity for {}", absolutePath, e);
                // Принудительно добавляем файл в список на загрузку, если произошла ошибка I/O
                mismatchedFiles.put(relativePath, expectedHash);
            }
        }
        
        log.info("Integrity check complete. Mismatched/Missing files: {}", mismatchedFiles.size());
        return mismatchedFiles;
    }

    /**
     * Вспомогательный метод для преобразования байтового массива хэша в HEX-строку.
     * Оптимизирован для производительности.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}