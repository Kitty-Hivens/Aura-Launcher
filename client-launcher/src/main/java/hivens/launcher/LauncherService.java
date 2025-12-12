package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileManifest;
import hivens.core.api.model.ServerProfile;
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

public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);
    private final IManifestProcessorService manifestProcessor;
    private final Map<String, LaunchConfig> launchConfigs;

    // ... (record LaunchConfig и конструктор оставляем как были) ...
    private record LaunchConfig(
            String mainClass,
            String tweakClass,
            String assetIndex,
            List<String> jvmArgs,
            String nativesDir
    ) {}

    public LauncherService(IManifestProcessorService manifestProcessor) {
        this.manifestProcessor = Objects.requireNonNull(manifestProcessor, "ManifestProcessorService cannot be null");
        this.launchConfigs = buildLaunchConfigMap();
    }

    @Override
    public Process launchClient(
            SessionData sessionData,
            ServerProfile serverProfile, // <-- Изменили тип
            Path clientRootPath,
            Path javaExecutablePath,
            int allocatedMemoryMB
    ) throws IOException {

        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(serverProfile, "ServerProfile cannot be null");

        // ВАЖНО: .version() меняем на .getVersion()
        LaunchConfig config = launchConfigs.get(serverProfile.getVersion());
        if (config == null) {
            log.error("Missing hardcoded LaunchConfig for version: {}. Aborting.", serverProfile.getVersion());
            throw new IOException("No launch configuration found for version: " + serverProfile.getVersion());
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutablePath.toString());

        // JVM аргументы
        command.addAll(config.jvmArgs());
        command.add("-Xms512M");
        command.add("-Xmx" + allocatedMemoryMB + "M");
        command.add(buildNativesPath(clientRootPath, config.nativesDir()));

        // Classpath
        command.add("-cp");
        command.add(buildClasspath(clientRootPath, sessionData.fileManifest()));

        // Main Class
        command.add(config.mainClass());

        // Minecraft аргументы
        command.addAll(buildMinecraftArgs(sessionData, serverProfile, clientRootPath, config.assetIndex()));

        // TweakClass
        if (config.tweakClass() != null) {
            command.add("--tweakClass");
            command.add(config.tweakClass());
        }

        log.debug("Assembled launch command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(clientRootPath.toFile());
        pb.inheritIO();

        log.info("Launching client process for user {} (Version: {})...",
                sessionData.playerName(), serverProfile.getVersion());
        return pb.start();
    }

    // ... (методы buildLaunchConfigMap, buildClasspath, buildNativesPath без изменений) ...

    private Map<String, LaunchConfig> buildLaunchConfigMap() {
        // Код инициализации мапы оставляем тот же (1.7.10, 1.12.2, 1.21.1)
        return Map.of(
                // ... (скопируй старый код мапы сюда) ...
                "1.7.10", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "cpw.mods.fml.common.launcher.FMLTweaker",
                        "1.7.10",
                        List.of("-XX:+UseG1GC", "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"), // Упростил для примера
                        "bin/natives-1.7.10"
                ),
                "1.12.2", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "net.minecraftforge.fml.common.launcher.FMLTweaker",
                        "1.12.2",
                        List.of("-XX:+UseG1GC"),
                        "bin/natives-1.12.2"
                ),
                "1.21.1", new LaunchConfig(
                        "cpw.mods.bootstraplauncher.BootstrapLauncher",
                        null,
                        "1.21.1",
                        List.of("-Dminecraft.launcher.brand=smartycraft"),
                        "bin/natives-1.21.1"
                )
        );
    }

    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        // ... (код без изменений) ...
        return manifestProcessor.flattenManifest(manifest).keySet().stream()
                .filter(f -> f.endsWith(".jar"))
                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private String buildNativesPath(Path clientRootPath, String nativesDir) {
        return "-Djava.library.path=" + clientRootPath.resolve(nativesDir).toString().replace("\\", "/");
    }

    private List<String> buildMinecraftArgs(SessionData sessionData, ServerProfile serverProfile, Path clientRootPath, String assetIndex) {
        return List.of(
                "--username", sessionData.playerName(),
                "--version", "Forge " + serverProfile.getVersion(), // <-- .getVersion()
                "--gameDir", clientRootPath.toString(),
                "--assetsDir", clientRootPath.resolve("assets").toString(),
                "--uuid", sessionData.uuid(),
                "--accessToken", sessionData.accessToken(),
                "--userProperties", "{}",
                "--assetIndex", assetIndex
        );
    }
}