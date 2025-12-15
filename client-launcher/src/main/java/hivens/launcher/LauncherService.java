package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileManifest;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);

    private static final String FORCED_JAVA_8_PATH = "/usr/lib/jvm/liberica-jdk-8-full/bin/java";

    private final IManifestProcessorService manifestProcessor;
    private final Map<String, LaunchConfig> launchConfigs;

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
            ServerProfile serverProfile,
            Path clientRootPath,
            Path javaExecutablePath,
            int allocatedMemoryMB
    ) throws IOException {

        Objects.requireNonNull(sessionData, "SessionData cannot be null");
        Objects.requireNonNull(serverProfile, "ServerProfile cannot be null");

        String version = serverProfile.getVersion();
        LaunchConfig config = launchConfigs.get(version);

        if (config == null) {
            log.error("Missing hardcoded LaunchConfig for version: {}. Aborting.", version);
            throw new IOException("No launch configuration found for version: " + version);
        }

        //deleteBannedMods(clientRootPath);

        prepareNatives(clientRootPath, config.nativesDir(), version);
        prepareAssets(clientRootPath, "assets-" + version + ".zip");
        String actualJavaPath = javaExecutablePath.toString();
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add(actualJavaPath);

        // --- ЭТАП 3: Аргументы JVM ---
        if ("1.12.2".equals(version)) {
            jvmArgs.add("-XX:+UseG1GC");
            jvmArgs.add("-XX:+UnlockExperimentalVMOptions");
            jvmArgs.add("-XX:G1NewSizePercent=20");
            jvmArgs.add("-XX:G1ReservePercent=20");
            jvmArgs.add("-XX:MaxGCPauseMillis=50");
            jvmArgs.add("-XX:G1HeapRegionSize=32M");
            jvmArgs.add("-Djava.net.preferIPv4Stack=true");
            jvmArgs.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            jvmArgs.add("-Dfml.ignorePatchDiscrepancies=true");
            jvmArgs.add("-Dminecraft.api.auth.host=http://www.smartycraft.ru/launcher/");
            jvmArgs.add("-Dminecraft.api.account.host=http://www.smartycraft.ru/launcher/");
            jvmArgs.add("-Dminecraft.api.session.host=http://www.smartycraft.ru/launcher/");
        } else if("1.7.10".equals(version)) {
            jvmArgs.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            jvmArgs.add("-Dfml.ignorePatchDiscrepancies=true");
            jvmArgs.add("-XX:+UseG1GC");
        }

        jvmArgs.addAll(config.jvmArgs());
        jvmArgs.add("-Xms512M");
        jvmArgs.add("-Xmx" + allocatedMemoryMB + "M");
        Path nativesPath = clientRootPath.resolve(config.nativesDir());
        jvmArgs.add("-Djava.library.path=" + nativesPath.toAbsolutePath());
        jvmArgs.add("-cp");
        jvmArgs.add(buildClasspath(clientRootPath, sessionData.fileManifest()));
        jvmArgs.add(config.mainClass());
        jvmArgs.addAll(buildMinecraftArgs(sessionData, serverProfile, clientRootPath, config.assetIndex()));

        if (config.tweakClass() != null) {
            jvmArgs.add("--tweakClass");
            jvmArgs.add(config.tweakClass());
        }

        log.debug("Assembled launch command: {}", String.join(" ", jvmArgs));

        ProcessBuilder pb = new ProcessBuilder(jvmArgs);
        pb.directory(clientRootPath.toFile());
        pb.redirectErrorStream(true);

        log.info("Launching client process for user {} (Version: {})...", sessionData.playerName(), version);

        Process process = pb.start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[GAME] " + line);
                }
            } catch (IOException ignored) {}
        }, "GameOutputReader").start();

        return process;
    }

    private List<String> buildMinecraftArgs(SessionData sessionData, ServerProfile serverProfile, Path clientRootPath, String assetIndex) {

        List<String> args = new ArrayList<>();
        args.add("--username");
        args.add(sessionData.playerName());
        args.add("--version");
        args.add("Forge " + serverProfile.getVersion());
        args.add("--gameDir");
        args.add(clientRootPath.toString());
        args.add("--assetsDir");
        args.add(clientRootPath.resolve("assets").toString());
        args.add("--assetIndex");
        args.add(assetIndex);
        args.add("--uuid");
        args.add(sessionData.uuid());
        args.add("--accessToken");
        args.add(sessionData.accessToken());
        args.add("--userProperties");
        args.add("{}");

        args.add("--userType");
        args.add("mojang");
        args.add("--versionType");
        args.add("Forge");

        return args;
    }

    private void prepareNatives(Path clientRoot, String nativesDirName, String version) {
        Path binDir = clientRoot.resolve("bin");
        Path nativesDir = clientRoot.resolve(nativesDirName);
        OS currentOS = getPlatform();

        String targetZipName = "natives-" + version + ".zip";
        Path nativesZip = binDir.resolve(targetZipName);

        if (Files.exists(nativesZip)) {
            File dir = nativesDir.toFile();
            if (!dir.exists() || (dir.listFiles() != null && Objects.requireNonNull(dir.listFiles()).length == 0)) {
                log.info("Extracting natives from {}...", nativesZip);
                try {
                    unzip(nativesZip.toFile(), nativesDir.toFile());

                    if (currentOS == OS.LINUX && !hasLinuxNatives(nativesDir)) {
                        log.warn("⚠️ Нативы распакованы, но файлов .so не найдено! Качаем аварийный комплект Linux...");
                        downloadFallbackNatives(nativesDir, version, OS.LINUX);
                    } else if (currentOS == OS.MACOS && !hasMacNatives(nativesDir)) {
                        log.warn("⚠️ Нативы распакованы, но файлов .dylib не найдено! Качаем аварийный комплект Mac...");
                        downloadFallbackNatives(nativesDir, version, OS.MACOS);
                    }

                } catch (IOException e) {
                    log.error("Failed to unzip natives!", e);
                }
            }
        } else {
            log.warn("Natives archive not found: {}", nativesZip);
            if (currentOS != OS.WINDOWS) {
                downloadFallbackNatives(nativesDir, version, currentOS);
            }
        }
    }

    // --- НОВЫЙ МЕТОД: РАСПАКОВКА АССЕТОВ ---
    private void prepareAssets(Path clientRoot, String assetsZipName) {
        Path assetsDir = clientRoot.resolve("assets");
        Path assetsZip = clientRoot.resolve(assetsZipName);

        // Если архив скачан, но папка indexes пуста или отсутствует - распаковываем
        if (Files.exists(assetsZip)) {
            if (!Files.exists(assetsDir.resolve("indexes"))) {
                log.info("Extracting assets from {}...", assetsZip);
                try {
                    unzip(assetsZip.toFile(), assetsDir.toFile());
                } catch (IOException e) {
                    log.error("Failed to unzip assets!", e);
                }
            }
        }
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());

                // Защита Zip Slip
                if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) throw new IOException("Failed to create dir " + newFile);
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Failed to create dir " + parent);
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    @Deprecated
    private void deleteBannedMods(Path root) {
        log.info("Scanning for banned mods...", root);
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().contains("ReplayMod")) // или другие моды
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (file.delete()) {
                            log.warn("🔥🔥🔥 DELETED BANNED MOD: {} 🔥🔥🔥", file.getAbsolutePath());
                        }
                    });
        } catch (IOException e) {
            log.error("Error cleaning mods", e);
        }
    }

    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        return manifestProcessor.flattenManifest(manifest).keySet().stream()
                .filter(f -> f.endsWith(".jar"))
                .filter(f -> !f.contains("/mods/"))
                .sorted((path1, path2) -> {
                    if (path1.contains("vecmath")) return -1;
                    if (path2.contains("vecmath")) return 1;
                    return path1.compareTo(path2);
                })
                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private Map<String, LaunchConfig> buildLaunchConfigMap() {
        return Map.of(
                "1.7.10", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "cpw.mods.fml.common.launcher.FMLTweaker",
                        "1.7.10",
                        List.of(
                                "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true",
                                "-Dminecraft.launcher.brand=smartycraft",
                                "-Dlauncher.version=3.0.0"
                        ),
                        "bin/natives-1.7.10"
                ),
                "1.12.2", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "net.minecraftforge.fml.common.launcher.FMLTweaker",
                        "1.12.2",
                        List.of(
                                "-XX:+UseG1GC",
                                "-XX:+UnlockExperimentalVMOptions",
                                "-XX:G1NewSizePercent=20",
                                // ... (остальные XX флаги)...
                                "-Dfml.ignoreInvalidMinecraftCertificates=true",
                                "-Dfml.ignorePatchDiscrepancies=true",
                                // ВОТ ЗДЕСЬ УБИРАЕМ ВСЕ ЛИШНЕЕ
                                "-Dminecraft.launcher.brand=SmartyCraft",
                                "-Dlauncher.version=3.0.0"
                        ),
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

    private enum OS { WINDOWS, LINUX, MACOS, UNKNOWN }

    private OS getPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return OS.WINDOWS;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return OS.LINUX;
        if (osName.contains("mac")) return OS.MACOS;
        return OS.UNKNOWN;
    }

    private boolean hasLinuxNatives(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.toString().endsWith(".so"));
        } catch (IOException e) { return false; }
    }

    private boolean hasMacNatives(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.toString().endsWith(".dylib") || p.toString().endsWith(".jnilib"));
        } catch (IOException e) { return false; }
    }

    private void downloadFallbackNatives(Path targetDir, String version, OS os) {
        log.warn("Need fallback natives for {} on {}", version, os);
        // Заглушка, если понадобится
    }
}