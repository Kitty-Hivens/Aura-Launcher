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

    // ПУТЬ К JAVA 8 (Твой рабочий путь из логов)
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

        // --- ЭТАП 0: ЗАЧИСТКА ВРАГОВ (ReplayMod) ---
        // Удаляем проблемные моды физически перед запуском
        deleteBannedMods(clientRootPath);

        // --- ЭТАП 1: Подготовка нативов (Распаковка ZIP) ---
        prepareNatives(clientRootPath, config.nativesDir(), version);

        // --- ЭТАП 2: Выбор Java (Форсируем Java 8 для 1.12.2) ---
        String actualJavaPath;
        if ("1.12.2".equals(version)) {
            log.warn("FORCE OVERRIDE: Using Java 8 for 1.12.2 -> {}", FORCED_JAVA_8_PATH);
            actualJavaPath = FORCED_JAVA_8_PATH;
        } else {
            actualJavaPath = javaExecutablePath.toString();
        }

        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add(actualJavaPath);

        // --- ЭТАП 3: Оптимизированные аргументы JVM для 1.12.2 ---
        if ("1.12.2".equals(version)) {
            // Специальные аргументы G1GC для модпаков
            jvmArgs.add("-XX:+UseG1GC");
            jvmArgs.add("-XX:+UnlockExperimentalVMOptions");
            jvmArgs.add("-XX:G1NewSizePercent=20");
            jvmArgs.add("-XX:G1ReservePercent=20");
            jvmArgs.add("-XX:MaxGCPauseMillis=50");
            jvmArgs.add("-XX:G1HeapRegionSize=32M");

            // Forge-специфичные флаги
            jvmArgs.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            jvmArgs.add("-Dfml.ignorePatchDiscrepancies=true");


        } else if("1.7.10".equals(version)) {
            jvmArgs.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            jvmArgs.add("-Dfml.ignorePatchDiscrepancies=true");
            jvmArgs.add("-XX:+UseG1GC");
            jvmArgs.add("-XX:+UnlockExperimentalVMOptions");
            jvmArgs.add("-XX:G1NewSizePercent=20");
            jvmArgs.add("-XX:G1ReservePercent=20");
            jvmArgs.add("-XX:MaxGCPauseMillis=50");
            jvmArgs.add("-XX:G1HeapRegionSize=32M");
        } else {
            // Для других версий используем базовые аргументы из конфига
            jvmArgs.addAll(config.jvmArgs());
        }

        // Память
        jvmArgs.add("-Xms512M");
        jvmArgs.add("-Xmx" + allocatedMemoryMB + "M");

        // Путь к нативам
        Path nativesPath = clientRootPath.resolve(config.nativesDir());
        jvmArgs.add("-Djava.library.path=" + nativesPath.toAbsolutePath());

        // Classpath (Сборка с учетом фильтрации и сортировки)
        jvmArgs.add("-cp");
        jvmArgs.add(buildClasspath(clientRootPath, sessionData.fileManifest()));

        // Main Class
        jvmArgs.add(config.mainClass());

        // --- ЭТАП 4: Аргументы Minecraft ---
        jvmArgs.addAll(buildMinecraftArgs(sessionData, serverProfile, clientRootPath, config.assetIndex()));

        // TweakClass
        if (config.tweakClass() != null) {
            jvmArgs.add("--tweakClass");
            jvmArgs.add(config.tweakClass());
        }

        log.debug("Assembled launch command: {}", String.join(" ", jvmArgs));

        ProcessBuilder pb = new ProcessBuilder(jvmArgs);
        pb.directory(clientRootPath.toFile());
        pb.redirectErrorStream(true); // Объединяем потоки вывода

        log.info("Launching client process for user {} (Version: {})...", sessionData.playerName(), version);

        Process process = pb.start();

        // Читаем вывод процесса в консоль (для отладки)
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[GAME] " + line);
                }
            } catch (IOException e) {
                // Игнорируем ошибку при закрытии игры
            }
        }, "GameOutputReader").start();

        return process;
    }

    // --- УБИЙЦА МОДОВ ---
    private void deleteBannedMods(Path root) {
        log.info("Scanning for banned mods...", root);
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> {
                        String name = p.toString();
                        // Фильтруем всё, что вызывает краш
                        return name.contains("ReplayMod");
                    })
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

    // --- ЛОГИКА РАСПАКОВКИ НАТИВОВ ---
    private void prepareNatives(Path clientRoot, String nativesDirName, String version) {
        Path binDir = clientRoot.resolve("bin");
        Path nativesDir = clientRoot.resolve(nativesDirName);
        Path nativesZip = binDir.resolve("natives-" + version + ".zip");

        if (Files.exists(nativesZip)) {
            File dir = nativesDir.toFile();
            // Если папки нет или она пустая - распаковываем
            if (!dir.exists() || (dir.listFiles() != null && Objects.requireNonNull(dir.listFiles()).length == 0)) {
                log.info("Extracting natives from {} to {}...", nativesZip, nativesDir);
                try {
                    unzip(nativesZip.toFile(), nativesDir.toFile());
                } catch (IOException e) {
                    log.error("Failed to unzip natives!", e);
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

    private Map<String, LaunchConfig> buildLaunchConfigMap() {
        return Map.of(
                "1.7.10", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "cpw.mods.fml.common.launcher.FMLTweaker",
                        "1.7.10",
                        List.of("-XX:+UseG1GC", "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"),
                        "bin/natives-1.7.10"
                ),
                "1.12.2", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "net.minecraftforge.fml.common.launcher.FMLTweaker",
                        "1.12.2",
                        List.of(
                                "-XX:+UseG1GC",
                                "-Dfml.ignoreInvalidMinecraftCertificates=true",
                                "-Dfml.ignorePatchDiscrepancies=true"
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

    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        return manifestProcessor.flattenManifest(manifest).keySet().stream()
                // 1. Оставляем только JAR-файлы и библиотеки, исключая моды:
                .filter(f -> f.endsWith(".jar"))
                .filter(f -> !f.contains("/mods/")) // Исключаем все, что лежит в папке mods/

                // 2. Сортировка (оставляем только для vecmath и библиотек)
                .sorted((path1, path2) -> {
                    if (path1.contains("vecmath")) return -1;
                    if (path2.contains("vecmath")) return 1;
                    return path1.compareTo(path2);
                })

                .map(clientRootPath::resolve)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private List<String> buildMinecraftArgs(SessionData sessionData, ServerProfile serverProfile, Path clientRootPath, String assetIndex) {
        return List.of(
                "--username", sessionData.playerName(),
                "--version", "Forge " + serverProfile.getVersion(),
                "--gameDir", clientRootPath.toString(),
                "--assetsDir", clientRootPath.resolve("assets").toString(),
                "--uuid", sessionData.uuid(),
                "--accessToken", sessionData.accessToken(),
                "--userProperties", "{}",
                "--assetIndex", assetIndex
        );
    }
}
