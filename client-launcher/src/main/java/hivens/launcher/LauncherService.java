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
 * Использует ProcessBuilder и ClientData для data-driven запуска.
 */
public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Process launchClient(SessionData sessionData, ClientData clientData, Path clientRootPath, Path javaExecutablePath) throws IOException {

        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(clientData, "ClientData cannot be null");
        Objects.requireNonNull(clientData.mainClass(), "ClientData 'mainClass' cannot be null");
        Objects.requireNonNull(clientRootPath, "Client root path cannot be null");
        Objects.requireNonNull(javaExecutablePath, "Java executable path cannot be null");

        List<String> command = new ArrayList<>();

        // 1. Исполняемый файл Java
        command.add(javaExecutablePath.toString());

        // 2. Аргументы JVM
        if (clientData.jvmArguments() != null) {
            command.addAll(filterJvmArgs(clientData.jvmArguments()));
        }

        // 3. Classpath
        command.add("-cp");
        command.add(buildClasspath(clientRootPath, clientData.filesWithHashes()));

        // 4. Главный класс (из API)
        command.add(clientData.mainClass());

        // 5. Аргументы Minecraft (на основе SessionData)
        command.addAll(buildMinecraftArgs(sessionData, clientData, clientRootPath));

        // 6. Дополнительные аргументы Minecraft (TweakClass, из API)
        if (clientData.tweakClass() != null && !clientData.tweakClass().isEmpty()) {
            command.add("--tweakClass");
            command.add(clientData.tweakClass());
        }

        // 7. Дополнительные аргументы (из API)
        if (clientData.mcArguments() != null) {
            command.addAll(clientData.mcArguments());
        }

        log.debug("Assembled launch command: {}", String.join(" ", command));

        // 8. Запуск процесса
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(clientRootPath.toFile()); // Устанавливаем рабочую директорию
        pb.inheritIO(); // Перенаправляем stdout/stderr клиента в консоль лаунчера

        log.info("Launching client process for user {} (Version: {})...",
                sessionData.playerName(), clientData.versionId());
        return pb.start();
    }

    /**
     * Фильтрует устаревшие или небезопасные аргументы JVM.
     */
    private List<String> filterJvmArgs(List<String> jvmArgs) {
        return jvmArgs.stream()
                // Отбрасываем устаревшие флаги CMS GC
                .filter(arg -> !arg.contains("UseConcMarkSweepGC"))
                .filter(arg -> !arg.contains("CMSIncrementalMode"))
                // Отбрасываем мусорный флаг для Windows
                .filter(arg -> !arg.contains("ThisTricksIntelDriversForPerformance"))
                .collect(Collectors.toList());
    }

    /**
     * Собирает Classpath на основе карты файлов.
     * ПРИМЕЧАНИЕ: Эта реализация все еще хрупкая и является тех. долгом.
     * Она предполагает, что все .jar файлы из filesWithHashes должны быть в classpath.
     */
    private String buildClasspath(Path clientRootPath, Map<String, String> files) {
        String separator = File.pathSeparator;

        if (files == null || files.isEmpty()) {
            log.warn("Classpath is empty! ClientData.filesWithHashes is null or empty.");
            return "";
        }

        // Эта логика предполагает, что 'filesWithHashes' содержит относительные пути
        // (e.g., "libraries/log4j.jar", "mods/mod.jar", "client-1.12.2.jar")
        return files.keySet().stream()
                .filter(file -> file.endsWith(".jar"))
                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(separator));
    }

    /**
     * Собирает основные аргументы Minecraft на основе данных сессии и клиента.
     */
    private List<String> buildMinecraftArgs(SessionData sessionData, ClientData clientData, Path clientRootPath) {
        // Используем List.of() для неизменяемого списка
        return List.of(
                "--username", sessionData.playerName(),
                "--version", clientData.versionId(), // Используем версию из API
                "--gameDir", clientRootPath.toString(),
                "--assetsDir", clientRootPath.resolve("assets").toString(), // (Упрощено, API должно давать 'assetsDir')
                "--uuid", sessionData.uuid(),
                "--accessToken", sessionData.accessToken(),
                "--userProperties", "{}",
                "--assetIndex", clientData.assetIndex() // Используем assetIndex из API
        );
    }
}