package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.data.FileManifest;
import hivens.core.api.model.ServerProfile;
import hivens.core.data.InstanceProfile;
import hivens.core.data.OptionalMod;
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
    private final ProfileManager profileManager;
    private final JavaManagerService javaManager;

    private record LaunchConfig(
            String mainClass,
            String tweakClass,
            String assetIndex,
            List<String> jvmArgs,
            String nativesDir
    ) {}

    public LauncherService(IManifestProcessorService manifestProcessor, ProfileManager profileManager, JavaManagerService javaManager) {
        this.manifestProcessor = manifestProcessor;
        this.profileManager = profileManager;
        this.javaManager = javaManager;
        this.launchConfigs = buildLaunchConfigMap();
    }

    @Override
    public Process launchClient(
            SessionData sessionData,
            ServerProfile serverProfile,
            Path clientRootPath,
            Path defaultJavaPath,   // (Может быть null, если в глобалках не задано)
            int defaultMemoryMB
    ) throws IOException {

        // 1. ЗАГРУЖАЕМ ПРОФИЛЬ (assetDir = "Industrial")
        InstanceProfile profile = profileManager.getProfile(serverProfile.getAssetDir());

        // 2. ОПРЕДЕЛЯЕМ ПАМЯТЬ
        int memory = (profile.getMemoryMb() != null && profile.getMemoryMb() > 0) ? profile.getMemoryMb() : defaultMemoryMB;
        if (memory < 512) memory = 4096; // Защита от дурака

        // 3. ОПРЕДЕЛЯЕМ JAVA (Приоритет: Профиль -> Глобалки -> Авто-скачивание)
        String javaExec;
        if (profile.getJavaPath() != null && !profile.getJavaPath().isEmpty()) {
            javaExec = profile.getJavaPath();
        } else if (defaultJavaPath != null && Files.exists(defaultJavaPath)) {
            javaExec = defaultJavaPath.toString();
        } else {
            // Если ничего не задано, просим JavaManager найти или скачать
            javaExec = javaManager.getJavaPath(serverProfile.getVersion()).toString();
        }

        log.info("Launch Config: Java={}, RAM={}MB, Server={}", javaExec, memory, serverProfile.getName());

        String version = serverProfile.getVersion();
        LaunchConfig config = launchConfigs.get(version);
        if (config == null) throw new IOException("Config not found for version: " + version);

        // 4. СИНХРОНИЗАЦИЯ МОДОВ (ХИРУРГ)
        List<OptionalMod> allMods = ((ManifestProcessorService) manifestProcessor).getOptionalModsForClient(version);
        syncMods(clientRootPath, profile, allMods);

        // 5. ПОДГОТОВКА ФАЙЛОВ
        prepareNatives(clientRootPath, config.nativesDir(), version);
        prepareAssets(clientRootPath, "assets-" + version + ".zip");

        // 6. СБОРКА КОМАНДЫ
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add(javaExec);

        // Флаги версии
        if ("1.12.2".equals(version)) {
            jvmArgs.add("-XX:+UseG1GC");
            jvmArgs.add("-XX:+UnlockExperimentalVMOptions");
            jvmArgs.add("-XX:G1NewSizePercent=20");
            jvmArgs.add("-XX:G1ReservePercent=20");
            jvmArgs.add("-XX:MaxGCPauseMillis=50");
            jvmArgs.add("-XX:G1HeapRegionSize=32M");
            // Smarty Auth Fixes
            jvmArgs.add("-Dminecraft.api.auth.host=http://www.smartycraft.ru/launcher/");
            jvmArgs.add("-Dminecraft.api.account.host=http://www.smartycraft.ru/launcher/");
            jvmArgs.add("-Dminecraft.api.session.host=http://www.smartycraft.ru/launcher/");
        } else if ("1.7.10".equals(version)) {
            jvmArgs.add("-XX:+UseG1GC");
        }

        jvmArgs.addAll(config.jvmArgs());
        if (profile.getJvmArgs() != null && !profile.getJvmArgs().isEmpty()) {
            jvmArgs.addAll(List.of(profile.getJvmArgs().split(" ")));
        }

        jvmArgs.add("-Xms512M");
        jvmArgs.add("-Xmx" + memory + "M");

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

        ProcessBuilder pb = new ProcessBuilder(jvmArgs);
        pb.directory(clientRootPath.toFile());
        pb.redirectErrorStream(true);

        return pb.start();
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

    /**
     * Синхронизирует папку mods с выбором игрока.
     * Удаляет выключенные моды, проверяет наличие включенных.
     */
    private void syncMods(Path clientRoot, InstanceProfile profile, List<OptionalMod> allMods) throws IOException {
        log.info("Synchronizing optional mods...");
        Path modsDir = clientRoot.resolve("mods");
        if (!Files.exists(modsDir)) Files.createDirectories(modsDir);

        Map<String, Boolean> state = profile.getOptionalModsState();

        for (OptionalMod mod : allMods) {
            boolean isEnabled = state.getOrDefault(mod.getId(), mod.isDefault());

            if (isEnabled) {
                // Если включен -> удаляем конфликты
                if (mod.getExcludings() != null) {
                    for (String exclude : mod.getExcludings()) {
                        Files.deleteIfExists(modsDir.resolve(exclude));
                    }
                }
                // TODO: Здесь же можно проверять наличие jars и докачивать их
            } else {
                // Если выключен -> удаляем файлы самого мода
                for (String jar : mod.getJars()) {
                    Files.deleteIfExists(modsDir.resolve(jar));
                    log.debug("Removed disabled mod: {}", jar);
                }
            }
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
