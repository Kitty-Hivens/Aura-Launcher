package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileData;
import hivens.core.data.FileManifest;
import hivens.core.data.ServerData;
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
 * Реализация сервиса запуска клиента (на основе ai.class).
 * Использует hardcoded "Launch Factory" на основе версии клиента.
 */
public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);
    private final IManifestProcessorService manifestProcessor;

    /**
     * Внутренний record для хранения hardcoded конфигураций запуска.
     */
    private record LaunchConfig(
            String mainClass,
            String tweakClass, // (Может быть null)
            String assetIndex,
            List<String> jvmArgs, // (Аргументы БЕЗ учета памяти)
            String nativesDir // (Относительный путь к natives)
    ) {}

    private final Map<String, LaunchConfig> launchConfigs;

    /**
     * DI-Конструктор.
     * @param manifestProcessor Сервис для "выпрямления" FileManifest
     */
    public LauncherService(IManifestProcessorService manifestProcessor) {
        this.manifestProcessor = Objects.requireNonNull(manifestProcessor, "ManifestProcessorService cannot be null");
        this.launchConfigs = buildLaunchConfigMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process launchClient(
            SessionData sessionData,
            ServerData serverData,
            Path clientRootPath,
            Path javaExecutablePath,
            int allocatedMemoryMB
    ) throws IOException {

        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(serverData, "ServerData cannot be null");

        // 1. Получаем конфигурацию запуска
        LaunchConfig config = launchConfigs.get(serverData.version());
        if (config == null) {
            log.error("Missing hardcoded LaunchConfig for version: {}. Aborting.", serverData.version());
            throw new IOException("No launch configuration found for version: " + serverData.version());
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutablePath.toString());

        // 2. Аргументы JVM
        command.addAll(config.jvmArgs());
        // Добавляем динамическую память (из ai.class)
        command.add("-Xms512M"); // (Hardcoded в ai.class)
        command.add("-Xmx" + allocatedMemoryMB + "M");
        command.add(buildNativesPath(clientRootPath, config.nativesDir()));

        // 3. Classpath
        command.add("-cp");
        command.add(buildClasspath(clientRootPath, sessionData.fileManifest()));

        // 4. Главный класс
        command.add(config.mainClass());

        // 5. Аргументы Minecraft (Динамические)
        command.addAll(buildMinecraftArgs(sessionData, serverData, clientRootPath, config.assetIndex()));

        // 6. TweakClass
        if (config.tweakClass() != null) {
            command.add("--tweakClass");
            command.add(config.tweakClass());
        }

        log.debug("Assembled launch command: {}", String.join(" ", command));

        // 7. Запуск
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(clientRootPath.toFile());
        pb.inheritIO();

        log.info("Launching client process for user {} (Version: {})...",
                sessionData.playerName(), serverData.version());
        return pb.start();
    }

    /**
     * Имитирует switch-блок из ai.class, храня hardcoded данные.
     * (Оптимизировано: Устаревшие CMS GC заменены на G1GC)
     */
    private Map<String, LaunchConfig> buildLaunchConfigMap() {

        // --- 1.7.10 (Case 4) ---

        // TODO: Добавить 1.16.5 (Case 1) и 1.18.2 (Case 2)

        return Map.of("1.7.10", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch", // mainClass
                        "cpw.mods.fml.common.launcher.FMLTweaker", // tweakClass
                        "1.7.10", // assetIndex
                        List.of( // jvmArgs (Оптимизировано, убран CMS)
                                "-XX:+UseG1GC",
                                "-XX:-DisableAttachMechanism",
                                "-XX:-UseFastAccessorMethods",
                                "-XX:-UseAdaptiveSizePolicy",
                                "-Xmn128M",
                                "-Dfml.ignoreInvalidMinecraftCertificates=true",
                                "-Dfml.ignorePatchDiscrepancies=true",
                                "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"
                        ),
                        "bin/natives-1.7.10" // (Предположение)
                ),

                // --- 1.12.2 (Case 0) ---
                "1.12.2", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch", // mainClass
                        "net.minecraftforge.fml.common.launcher.FMLTweaker", // tweakClass
                        "1.12.2", // assetIndex
                        List.of( // jvmArgs (Оптимизировано, убран CMS, взят 'else' блок из ai.class)
                                "-XX:+UseG1GC",
                                "-XX:-DisableAttachMechanism",
                                "-XX:-UseFastAccessorMethods",
                                "-XX:-UseAdaptiveSizePolicy",
                                "-Xmn128M",
                                "-Dfml.ignoreInvalidMinecraftCertificates=true",
                                "-Dfml.ignorePatchDiscrepancies=true",
                                "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"
                        ),
                        "bin/natives-1.12.2" // (На основе strace)
                ),

                // --- 1.21.1 (Case 3) ---
                "1.21.1", new LaunchConfig(
                        "cpw.mods.bootstraplauncher.BootstrapLauncher", // mainClass
                        null, // tweakClass
                        "1.21.1", // assetIndex
                        List.of( // jvmArgs (Взято из ai.class)
                                "-Dos.name=Windows 10",
                                "-Dos.version=10.0",
                                "-Xss1M",
                                "-Dminecraft.launcher.brand=smartycraft",
                                "-Dminecraft.launcher.version=3.6.2",
                                "-Djava.net.preferIPv6Addresses=system",
                                // (Пропускаем dignoreList/dmergeModules/p - это для BootstrapLauncher 1.18)
                                "--add-modules", "ALL-MODULE-PATH",
                                "--add-opens", "java.base/java.util.jar=cpw.mods.securejarhandler",
                                "--add-opens", "java.base/java.lang.invoke=cpw.mods.securejarhandler",
                                "--add-exports", "java.base/sun.security.util=cpw.mods.securejarhandler",
                                "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming",
                                "-XX:+UnlockExperimentalVMOptions",
                                "-XX:+UseG1GC",
                                "-XX:G1NewSizePercent=20",
                                "-XX:G1ReservePercent=20",
                                "-XX:MaxGCPauseMillis=50",
                                "-XX:G1HeapRegionSize=32M",
                                "-Dfml.ignoreInvalidMinecraftCertificates=true",
                                "-Dfml.ignorePatchDiscrepancies=true",
                                "-Djava.net.preferIPv4Stack=true",
                                "-Dminecraft.api.env=smartycraft"
                        ),
                        "bin/natives-1.21.1" // (Предположение на основе ai.class)
                ));
    }

    /**
     * Собирает Classpath, используя ManifestProcessorService.
     */
    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        String separator = File.pathSeparator;

        // Используем сервис Issue #15 для получения плоской карты
        Map<String, FileData> flatMap = manifestProcessor.flattenManifest(manifest);

        return flatMap.keySet().stream()
                .filter(file -> file.endsWith(".jar"))
                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(separator));
    }

    private String buildNativesPath(Path clientRootPath, String nativesDir) {
        return "-Djava.library.path=" + clientRootPath.resolve(nativesDir).toString();
    }

    private List<String> buildMinecraftArgs(SessionData sessionData, ServerData serverData, Path clientRootPath, String assetIndex) {
        // (На основе ai.class)
        return List.of(
                "--username", sessionData.playerName(),
                "--version", "Forge " + serverData.version(),
                "--gameDir", clientRootPath.toString(),
                "--assetsDir", clientRootPath.resolve("assets").toString(),
                "--uuid", sessionData.uuid(),
                "--accessToken", sessionData.accessToken(),
                "--userProperties", "{}",
                "--assetIndex", assetIndex
        );
    }
}