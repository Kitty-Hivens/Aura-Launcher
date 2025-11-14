package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.data.FileManifest; // (Импорт из SessionData)
import hivens.core.data.ServerData;
import hivens.core.data.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация сервиса запуска клиента Minecraft.
 * Использует ServerData и hardcoded значения).
 */
public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);

    @Override
    public Process launchClient(SessionData sessionData, ServerData serverData, Path clientRootPath, Path javaExecutablePath) throws IOException {

        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(serverData, "ServerData cannot be null");
        Objects.requireNonNull(clientRootPath, "Client root path cannot be null");
        Objects.requireNonNull(javaExecutablePath, "Java executable path cannot be null");

        // Определяем параметры запуска на основе версии
        LaunchConfig config = getLaunchConfig(serverData.version());

        List<String> command = new ArrayList<>();
        command.add(javaExecutablePath.toString());

        // Аргументы JVM
        command.addAll(config.jvmArguments);
        command.add(getNativesPath(clientRootPath)); // (На основе strace)

        // Classpath
        command.add("-cp");
        command.add(buildClasspath(clientRootPath, sessionData.fileManifest()));

        // Главный класс
        command.add(config.mainClass);

        // Аргументы Minecraft (Динамические)
        command.addAll(buildMinecraftArgs(sessionData, serverData, clientRootPath, config.assetIndex));

        // TweakClass (если нужен)
        if (config.tweakClass != null) {
            command.add("--tweakClass");
            command.add(config.tweakClass);
        }

        log.debug("Assembled launch command: {}", String.join(" ", command));

        // Запуск
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(clientRootPath.toFile());
        pb.inheritIO();

        log.info("Launching client process for user {} (Version: {})...",
                sessionData.playerName(), serverData.version());
        return pb.start();
    }

    /**
     * Вспомогательный внутренний класс (record) для хранения hardcoded
     * параметров запуска для разных версий.
     */
    private record LaunchConfig(
            List<String> jvmArguments,
            String mainClass,
            String tweakClass, // (Может быть null)
            String assetIndex
    ) {}

    /**
     * Возвращает hardcoded конфигурацию запуска на основе строки версии.
     * Мы должны заполнить это на основе strace.
     */
    private LaunchConfig getLaunchConfig(String version) {
        // TODO: Добавить switch/case для 1.7.10 и 1.21.1

        // (Данные из strace для 1.12.2)
        if (version.contains("1.12.2")) {
            return new LaunchConfig(
                    List.of(
                            "-XX:+UseG1GC",
                            "-Xmx4096M",
                            "-Xms512M",
                            "-Xmn128M"
                    ),
                    "net.minecraft.launchwrapper.Launch",
                    "net.minecraftforge.fml.common.launcher.FMLTweaker",
                    "1.12.2"
            );
        }

        log.warn("Unknown client version: {}. Using default 1.12.2 config.", version);
        // Возвращаем 1.12.2 по умолчанию
        return getLaunchConfig("1.12.2");
    }

    /**
     * Рекурсивно "выпрямляет" FileManifest в плоскую Map<String, String>.
     * (Это должно быть в ManifestProcessorService, Issue #13)
     */
    private Map<String, String> flattenManifest(FileManifest manifest) {
        Map<String, String> flatMap = new HashMap<>();
        if (manifest == null) return flatMap;

        // TODO: Реализовать рекурсивный обход 'manifest.directories()'
        // и 'manifest.files()' для сбора всех путей и md5.

        // Эта логика критически важна для Classpath и IntegrityCheck.

        return flatMap;
    }

    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        String separator = File.pathSeparator;
        Map<String, String> files = flattenManifest(manifest);

        return files.keySet().stream()
                .filter(file -> file.endsWith(".jar"))
                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(separator));
    }

    private String getNativesPath(Path clientRootPath) {
        // (На основе strace)
        return "-Djava.library.path=" + clientRootPath.resolve("bin/natives-1.12.2");
    }

    private List<String> buildMinecraftArgs(SessionData sessionData, ServerData serverData, Path clientRootPath, String assetIndex) {
        return List.of(
                "--username", sessionData.playerName(),
                "--version", serverData.version(),
                "--gameDir", clientRootPath.toString(),
                "--assetsDir", clientRootPath.resolve("assets").toString(),
                "--uuid", sessionData.uuid(),
                "--accessToken", sessionData.accessToken(),
                "--userProperties", "{}",
                "--assetIndex", assetIndex
        );
    }
}