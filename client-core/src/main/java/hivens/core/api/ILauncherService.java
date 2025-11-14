package hivens.core.api;

import hivens.core.data.ServerData;
import hivens.core.data.SessionData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Контракт для сервиса запуска клиента Minecraft.
 */
public interface ILauncherService {

    /**
     * Собирает и выполняет команду запуска клиента Minecraft.
     *
     * @param sessionData Данные сессии (accessToken, uuid, playerName).
     * @param serverData Данные о выбранном сервере (версия, имя).
     * @param clientRootPath Абсолютный путь к корню клиента.
     * @param javaExecutablePath Абсолютный путь к исполняемому файлу java.
     * @return Запущенный процесс (Process) для мониторинга.
     * @throws IOException в случае ошибки I/O при запуске.
     */
    Process launchClient(SessionData sessionData, ServerData serverData, Path clientRootPath, Path javaExecutablePath) throws IOException;
}