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
 */
public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);
    private final IManifestProcessorService manifestProcessor;

    // ... (record LaunchConfig) ...
    private record LaunchConfig(
            String mainClass,
            String tweakClass, // (Может быть null)
            String assetIndex,
            List<String> jvmArgs, // (Аргументы БЕЗ учета памяти)
            String nativesDir // (Относительный путь к natives)
    ) {}

    private final Map<String, LaunchConfig> launchConfigs;

    public LauncherService(IManifestProcessorService manifestProcessor) {
        this.manifestProcessor = Objects.requireNonNull(manifestProcessor, "ManifestProcessorService cannot be null");
        this.launchConfigs = buildLaunchConfigMap();
    }

    @Override
    public Process launchClient(
            SessionData sessionData,
            ServerData serverData,
            Path clientRootPath,
            Path javaExecutablePath,
            int allocatedMemoryMB
    ) throws IOException {

        // ... (проверки) ...
        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(serverData, "ServerData cannot be null");

        LaunchConfig config = launchConfigs.get(serverData.version());
        if (config == null) {
            log.error("Missing hardcoded LaunchConfig for version: {}. Aborting.", serverData.version());
            throw new IOException("No launch configuration found for version: " + serverData.version());
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutablePath.toString());

        // 2. Аргументы JVM
        command.addAll(config.jvmArgs());
        command.add("-Xms512M");
        command.add("-Xmx" + allocatedMemoryMB + "M");
        command.add(buildNativesPath(clientRootPath, config.nativesDir()));

        // 3. Classpath
        command.add("-cp");
        command.add(buildClasspath(clientRootPath, sessionData.fileManifest()));

        // 4. Главный класс
        command.add(config.mainClass());

        // 5. Аргументы Minecraft
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

    // ... (buildLaunchConfigMap) ...
    private Map<String, LaunchConfig> buildLaunchConfigMap() {
        // ... (Тот же код, что и в оригинале) ...
        // (Оставил твой Map.of(...) как есть)
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

    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        String separator = File.pathSeparator;
        Map<String, FileData> flatMap = manifestProcessor.flattenManifest(manifest);

        return flatMap.keySet().stream()
                .filter(file -> file.endsWith(".jar"))
                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(separator));
    }

    private String buildNativesPath(Path clientRootPath, String nativesDir) {
        String nativePath = clientRootPath.resolve(nativesDir).toString().replace("\\", "/");
        return "-Djava.library.path=" + nativePath;
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