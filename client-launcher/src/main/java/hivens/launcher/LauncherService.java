package hivens.launcher;

import hivens.core.api.ILauncherService;
import hivens.core.api.IManifestProcessorService;
import hivens.core.data.*;
import hivens.core.api.model.ServerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hivens.core.util.ZipUtils.unzip;

public class LauncherService implements ILauncherService {

    private static final Logger log = LoggerFactory.getLogger(LauncherService.class);

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
            Path defaultJavaPath,
            int defaultMemoryMB
    ) throws IOException {

        InstanceProfile profile = profileManager.getProfile(serverProfile.getAssetDir());

        // Память
        int memory = (profile.getMemoryMb() != null && profile.getMemoryMb() > 0) ? profile.getMemoryMb() : defaultMemoryMB;
        if (memory < 768) memory = 1024;

        // Java
        String javaExec;
        if (profile.getJavaPath() != null && !profile.getJavaPath().isEmpty()) {
            javaExec = profile.getJavaPath();
        } else if (defaultJavaPath != null && Files.exists(defaultJavaPath)) {
            javaExec = defaultJavaPath.toString();
        } else {
            javaExec = javaManager.getJavaPath(serverProfile.getVersion()).toString();
        }

        log.info("Starting {} with Java: {}, RAM: {}", serverProfile.getName(), javaExec, memory);

        String version = serverProfile.getVersion();
        LaunchConfig config = launchConfigs.get(version);
        if (config == null) throw new IOException("Unsupported version: " + version);

        // Синхронизация модов
        List<OptionalMod> allMods = manifestProcessor.getOptionalModsForClient(serverProfile);
        log.info("Syncing {} mods for profile {}", allMods.size(), serverProfile.getName());
        syncMods(clientRootPath, profile, allMods, sessionData.fileManifest(), version);

        // Подготовка файлов (нативы)
        prepareNatives(clientRootPath, config.nativesDir(), version);
        prepareAssets(clientRootPath, "assets-" + version + ".zip");

        // Сборка аргументов JVM
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add(javaExec);

        // FIX для macOS
        if (getPlatform() == OS.MACOS) {
            jvmArgs.add("-XstartOnFirstThread");
            jvmArgs.add("-Djava.awt.headless=false");
        }

        // Аргументы SmartyCraft
        jvmArgs.add("-Dminecraft.api.auth.host=http://www.smartycraft.ru/launcher/");
        jvmArgs.add("-Dminecraft.api.account.host=http://www.smartycraft.ru/launcher/");
        jvmArgs.add("-Dminecraft.api.session.host=http://www.smartycraft.ru/launcher/");
        jvmArgs.add("-Dminecraft.launcher.brand=smartycraft");
        jvmArgs.add("-Dlauncher.version=3.0.0");

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

        // Если не сделать inheritIO(), буфер вывода заполнится логами игры,
        // и процесс игры "зависнет" (перестанет отвечать) во время загрузки.
        ProcessBuilder pb = new ProcessBuilder(jvmArgs);
        pb.directory(clientRootPath.toFile());

        // ВАЖНО: inheritIO перенаправляет логи игры в консоль IDEA/Терминала.
        // Это предотвращает зависание.
        pb.inheritIO();

        log.debug("Launch cmd: {}", String.join(" ", jvmArgs));

        return pb.start();
    }

    private List<String> buildMinecraftArgs(SessionData sessionData, ServerProfile serverProfile, Path clientRootPath, String assetIndex) {
        List<String> args = new ArrayList<>();
        args.add("--username");
        args.add(sessionData.playerName());
        args.add("--version");
        args.add("Forge " + serverProfile.getVersion());
        args.add("--gameDir");
        args.add(clientRootPath.toAbsolutePath().toString());
        args.add("--assetsDir");
        args.add(clientRootPath.resolve("assets").toAbsolutePath().toString());
        args.add("--assetIndex");
        args.add(assetIndex);
        args.add("--uuid");
        args.add(sessionData.uuid());
        args.add("--accessToken");
        args.add(sessionData.accessToken());

        args.add("--userProperties");
        args.add("{}"); // Важно для совместимости

        args.add("--userType");
        args.add("mojang"); // Smarty эмулирует Mojang

        return args;
    }

    private void prepareNatives(Path clientRoot, String nativesDirName, String version) {
        Path binDir = clientRoot.resolve("bin");
        Path nativesDir = clientRoot.resolve(nativesDirName);

        // Очистка при смене версии/системы (опционально, но полезно)
        // if (Files.exists(nativesDir)) deleteDirectory(nativesDir);

        // 1. Сначала пробуем распаковать то, что дал сервер (обычно это Windows нативы)
        if (!Files.exists(nativesDir) || Objects.requireNonNull(nativesDir.toFile().list()).length == 0) {
            String targetZipName = "natives-" + version + ".zip";
            Path nativesZip = binDir.resolve(targetZipName);
            if (Files.exists(nativesZip)) {
                log.info("Extracting server natives...");
                try {
                    unzip(nativesZip.toFile(), nativesDir.toFile());
                } catch (IOException e) {
                    log.error("Failed to unzip server natives", e);
                }
            }
        }

        // 2. ПРОВЕРКА ЦЕЛОСТНОСТИ ПОД ТЕКУЩУЮ ОС
        OS currentOS = getPlatform();
        if (!checkNativesIntegrity(nativesDir, currentOS)) {
            log.warn("⚠️ Natives for {} are missing or incomplete! Downloading fallback...", currentOS);
            downloadFallbackNatives(nativesDir, version, currentOS);
        }
    }

    private boolean checkNativesIntegrity(Path nativesDir, OS os) {
        try (Stream<Path> stream = Files.list(nativesDir)) {
            List<String> files = stream.map(p -> p.getFileName().toString()).toList();
            if (files.isEmpty()) return false;

            return switch (os) {
                case WINDOWS -> files.stream().anyMatch(f -> f.endsWith(".dll"));
                case LINUX -> files.stream().anyMatch(f -> f.endsWith(".so"));
                case MACOS -> files.stream().anyMatch(f -> f.endsWith(".dylib") || f.endsWith(".jnilib"));
                default -> true;
            };
        } catch (IOException e) {
            return false;
        }
    }

    private void downloadFallbackNatives(Path targetDir, String version, OS os) {
        try {
            String baseUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform";
            String artifactVersion;
            String osSuffix;

            // Определяем версию LWJGL
            if ("1.7.10".equals(version)) {
                artifactVersion = "2.9.1";
            } else {
                // Для 1.12.2 и выше (до 1.13)
                artifactVersion = "2.9.4-nightly-20150209";
            }

            // Определяем суффикс файла
            switch (os) {
                case LINUX -> osSuffix = "natives-linux";
                case MACOS -> osSuffix = "natives-osx";
                case WINDOWS -> osSuffix = "natives-windows";
                default -> throw new IllegalStateException("Unsupported OS: " + os);
            }

            String fileName = "lwjgl-platform-" + artifactVersion + "-" + osSuffix + ".jar";
            String url = baseUrl + "/" + artifactVersion + "/" + fileName;

            log.info("Downloading natives from: {}", url);

            Path tempJar = Files.createTempFile("natives_" + osSuffix, ".jar");
            java.net.URL downloadUrl = new java.net.URL(url);

            // Качаем
            try (InputStream in = downloadUrl.openStream()) {
                Files.copy(in, tempJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Распаковываем
            unzip(tempJar.toFile(), targetDir.toFile());

            // Удаляем временный файл
            Files.deleteIfExists(tempJar);

            log.info("✅ Natives for {} installed successfully!", os);

        } catch (Exception e) {
            log.error("Failed to download fallback natives", e);
        }
    }

    private void prepareAssets(Path clientRoot, String assetsZipName) {
        Path assetsDir = clientRoot.resolve("assets");
        Path assetsZip = clientRoot.resolve(assetsZipName);

        // Распаковываем, только если папка assets/indexes отсутствует,
        // но сам zip архив есть.
        if (Files.exists(assetsZip)) {
            File indexesDir = assetsDir.resolve("indexes").toFile();
            if (!indexesDir.exists() || !indexesDir.isDirectory()) {
                log.info("Extracting assets archive...");
                try {
                    unzip(assetsZip.toFile(), assetsDir.toFile());
                } catch (IOException e) {
                    log.error("Failed to unzip assets", e);
                }
            }
        }
    }

    /**
     * "Хирург": Удаляет ВСЁ лишнее из папки mods, обеспечивая чистоту сборки.
     */
    private void syncMods(Path clientRoot, InstanceProfile profile, List<OptionalMod> allMods, FileManifest manifest, String clientVersion) throws IOException {
        Path modsDir = clientRoot.resolve("mods");
        if (!Files.exists(modsDir)) Files.createDirectories(modsDir);

        // 1. Составляем "Белый список" (Whitelist)
        Set<String> allowedFiles = new HashSet<>();

        // А) Обязательные моды из манифеста
        // [FIX] Используем flattenManifest, чтобы достать файлы из глубины папок!
        Map<String, FileData> flatFiles = manifestProcessor.flattenManifest(manifest);

        for (String path : flatFiles.keySet()) {
            // path выглядит как "Industrial/mods/1.12.2/jei.jar" или "mods/jei.jar"
            // Нам нужно понять, является ли файл модом.
            if (path.contains("/mods/") || path.startsWith("mods/")) {
                String fileName = new File(path).getName();
                allowedFiles.add(fileName);
            }
        }

        // Б) Опциональные моды
        Map<String, Boolean> state = profile.getOptionalModsState();
        for (OptionalMod mod : allMods) {
            boolean isEnabled = state.getOrDefault(mod.getId(), mod.isDefault());

            if (isEnabled) {
                if (mod.getJars() != null) allowedFiles.addAll(mod.getJars());
            }
            // Выключенные моды не добавляем -> они будут удалены как "чужие"
        }

        // 2. Сканируем и чистим
        // Чистим корень mods/
        cleanDirectory(modsDir, allowedFiles);

        // Чистим папку версии (например mods/1.12.2/)
        if (clientVersion != null && !clientVersion.isEmpty()) {
            Path versionModsDir = modsDir.resolve(clientVersion);
            if (Files.exists(versionModsDir)) {
                cleanDirectory(versionModsDir, allowedFiles);
            }
        }

        log.info("Mods folder synced for version {}. Allowed files: {}", clientVersion, allowedFiles.size());
    }

    private void cleanDirectory(Path dir, Set<String> allowedFiles) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar") || path.toString().endsWith(".zip")) // Только моды
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        // Если файла нет в белом списке -> удаляем
                        if (!allowedFiles.contains(fileName)) {
                            try {
                                log.info("Deleting extraneous file: {}", fileName);
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Failed to delete {}", fileName, e);
                            }
                        }
                    });
        }
    }

    private String buildClasspath(Path clientRootPath, FileManifest manifest) {
        // Улучшенная сортировка для classpath
        return manifestProcessor.flattenManifest(manifest).keySet().stream()
                .filter(f -> f.endsWith(".jar"))
                .filter(f -> !f.contains("/mods/")) // Исключаем моды из CP
                .sorted((p1, p2) -> {
                    // Важные библиотеки должны быть выше
                    if (p1.contains("launchwrapper")) return -1;
                    if (p2.contains("launchwrapper")) return 1;
                    return p1.compareTo(p2);
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
                        List.of("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"),
                        "bin/natives-1.7.10"
                ),
                "1.12.2", new LaunchConfig(
                        "net.minecraft.launchwrapper.Launch",
                        "net.minecraftforge.fml.common.launcher.FMLTweaker",
                        "1.12.2",
                        List.of(
                                // ВОССТАНОВЛЕННЫЕ АРГУМЕНТЫ
                                "-XX:+UseG1GC",
                                "-XX:+UnlockExperimentalVMOptions",
                                "-XX:G1NewSizePercent=20",
                                "-XX:G1ReservePercent=20",
                                "-XX:MaxGCPauseMillis=50",
                                "-XX:G1HeapRegionSize=32M",
                                "-Dfml.ignoreInvalidMinecraftCertificates=true",
                                "-Dfml.ignorePatchDiscrepancies=true"
                        ),
                        "bin/natives-1.12.2"
                )
        );
    }

    private enum OS { WINDOWS, LINUX, MACOS, UNKNOWN }

    private OS getPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return OS.WINDOWS;
        if (osName.contains("mac")) return OS.MACOS;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return OS.LINUX;
        return OS.UNKNOWN;
    }
}