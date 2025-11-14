package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.data.ClientData;
import hivens.core.data.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Реализация сервиса запуска клиента Minecraft.
 * Использует ProcessBuilder для создания процесса на основе данных из API и strace.
 */
public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);

    // Главный класс, определенный в strace (net.minecraft.launchwrapper.Launch)
    private static final String MAIN_CLASS = "net.minecraft.launchwrapper.Launch";
    // TweakClass, определенный в strace
    private static final String TWEAK_CLASS = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    /**
     * {@inheritDoc}
     * <p>
     * Собирает команду, очищая устаревшие флаги JVM.
     */
    @Override
    public Process launchClient(SessionData sessionData, ClientData clientData, Path clientRootPath, Path javaExecutablePath) throws IOException {
        
        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(clientData, "ClientData cannot be null");
        Objects.requireNonNull(clientRootPath, "Client root path cannot be null");
        Objects.requireNonNull(javaExecutablePath, "Java executable path cannot be null");

        List<String> command = new ArrayList<>();

        // 1. Исполняемый файл Java
        command.add(javaExecutablePath.toString());

        // 2. Аргументы JVM
        // Используем аргументы из API, если они есть
        if (clientData.jvmArguments() != null) {
            command.addAll(filterJvmArgs(clientData.jvmArguments()));
        }

        // 3. Classpath (Путь к библиотекам)
        command.add("-cp");
        command.add(buildClasspath(clientRootPath, clientData.filesWithHashes()));

        // 4. Главный класс
        command.add(MAIN_CLASS);

        // 5. Аргументы Minecraft (на основе strace и SessionData)
        command.addAll(buildMinecraftArgs(sessionData, clientRootPath));
        
        // 6. Дополнительные аргументы Minecraft (TweakClass, из API)
        command.add("--tweakClass");
        command.add(TWEAK_CLASS);
        
        if (clientData.mcArguments() != null) {
            command.addAll(clientData.mcArguments());
        }

        log.debug("Assembled launch command: {}", String.join(" ", command));

        // 7. Запуск процесса
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(clientRootPath.toFile()); // Устанавливаем рабочую директорию
        pb.inheritIO(); // Перенаправляем stdout/stderr клиента в консоль лаунчера

        log.info("Launching client process for user {}...", sessionData.playerName());
        return pb.start();
    }

    /**
     * Фильтрует устаревшие или небезопасные аргументы JVM.
     */
    private List<String> filterJvmArgs(List<String> jvmArgs) {
        return jvmArgs.stream()
                // Отбрасываем устаревшие флаги CMS GC (причина сбоя на Linux)
                .filter(arg -> !arg.contains("UseConcMarkSweepGC"))
                .filter(arg -> !arg.contains("CMSIncrementalMode"))
                // Отбрасываем мусорный флаг для Windows
                .filter(arg -> !arg.contains("ThisTricksIntelDriversForPerformance"))
                .collect(Collectors.toList());
    }

    /**
     * Собирает Classpath на основе карты файлов.
     * (Оптимизация: предполагает, что все файлы в /libraries-1.12.2)
     */
    private String buildClasspath(Path clientRootPath, Map<String, String> files) {
        // Определение разделителя Classpath (:) для Linux/Mac, (;) для Windows
        String separator = File.pathSeparator;

        // (Упрощенная реализация: предполагает, что все нужные JAR лежат в /libraries-1.12.2)
        // ВАЖНО: В strace было видно много JAR. В идеале, ClientData должен предоставлять
        // список только тех файлов, которые идут в classpath.
        // На данный момент мы должны воссоздать его на основе filesWithHashes.
        
        // Эта реализация должна быть уточнена, когда мы будем знать,
        // как ClientData.filesWithHashes отличает библиотеки от модов.
        // Пока мы предполагаем, что он содержит все, что нужно.

        Path libPath = clientRootPath.resolve("libraries-1.12.2"); // На основе strace

        return files.keySet().stream()
                .filter(file -> file.endsWith(".jar"))
                .map(file -> libPath.resolve(file).toString()) // Примерный путь
                .collect(Collectors.joining(separator));
        
        // ПРИМЕЧАНИЕ: Если ClientData.filesWithHashes содержит полные пути
        // (например, "libraries-1.12.2/mod.jar"), эта логика должна быть изменена
        // на return files.keySet().stream()...collect(Collectors.joining(separator));
    }

    /**
     * Собирает основные аргументы Minecraft на основе данных сессии.
     * (Все эти флаги были взяты из strace)
     */
    private List<String> buildMinecraftArgs(SessionData sessionData, Path clientRootPath) {
        // Используем List.of() для неизменяемого списка
        return List.of(
                "--username", sessionData.playerName(),
                "--version", "Forge 1.12.2", // (Взято из strace, должно быть динамическим)
                "--gameDir", clientRootPath.toString(),
                "--assetsDir", clientRootPath.resolve("assets-1.12.2").toString(), // (На основе strace)
                "--uuid", sessionData.uuid(),
                "--accessToken", sessionData.accessToken(),
                "--userProperties", "{}",
                "--assetIndex", "1.12.2" // (На основе strace)
        );
    }
}