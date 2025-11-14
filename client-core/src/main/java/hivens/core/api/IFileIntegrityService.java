package hivens.core.api;

import hivens.core.data.FileStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Контракт для сервиса проверки целостности файлов.
 * Отвечает за вычисление хэшей и сравнение их с эталонными.
 */
public interface IFileIntegrityService {

    /**
     * Вычисляет хэш-сумму (MD5 или SHA) для указанного файла.
     * Алгоритм (MD5/SHA1/SHA256) определяется реализацией.
     *
     * @param filePath Путь к файлу.
     * @return Строковое представление хэш-суммы (hex).
     * @throws IOException в случае ошибок I/O при чтении файла.
     */
    String calculateFileHash(Path filePath) throws IOException;

    /**
     * Проверяет статус одного файла относительно ожидаемого хэша.
     *
     * @param filePath Путь к файлу.
     * @param expectedHash Ожидаемый хэш (из ClientData).
     * @return FileStatus (MISSING, MISMATCH, VALID).
     * @throws IOException в случае ошибок I/O.
     */
    FileStatus checkFile(Path filePath, String expectedHash) throws IOException;

    /**
     * Проверяет карту файлов (полученную из ClientData) и возвращает
     * список файлов, требующих обновления (статус MISSING или MISMATCH).
     *
     * @param basePath Корневая директория клиента (например, /home/user/.smarty).
     * @param filesToVerify Карта (относительный путь -> хэш) из ClientData.
     * @return Карта файлов, требующих загрузки (относительный путь -> хэш).
     */
    Map<String, String> verifyIntegrity(Path basePath, Map<String, String> filesToVerify);
}