package hivens.core.api;

import hivens.core.data.ClientData;
import hivens.core.data.SessionData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Контракт для сервиса запуска клиента Minecraft.
 * Отвечает за сборку аргументов JVM и запуск процесса.
 */
public interface ILauncherService {

    /**
     * Собирает и выполняет команду запуска клиента Minecraft.
     *
     * @param sessionData Данные сессии (accessToken, uuid, playerName).
     * @param clientData Данные клиента (jvmArguments, mcArguments).
     * @param clientRootPath Абсолютный путь к корню клиента (например, /home/user/.smarty).
     * @param javaExecutablePath Абсолютный путь к исполняемому файлу java (java.exe/java).
     * @return Запущенный процесс (Process) для мониторинга (например, в UI).
     * @throws IOException в случае ошибки I/O при запуске ProcessBuilder.
     */
    Process launchClient(SessionData sessionData, ClientData clientData, Path clientRootPath, Path javaExecutablePath) throws IOException;
}