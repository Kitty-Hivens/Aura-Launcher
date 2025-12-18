package hivens.launcher

import hivens.core.api.ILauncherService
import hivens.core.api.IManifestProcessorService
import hivens.core.api.model.ServerProfile
import hivens.core.data.FileManifest
import hivens.core.data.InstanceProfile
import hivens.core.data.OptionalMod
import hivens.core.data.SessionData
import hivens.core.util.ZipUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.stream.Collectors

class LauncherService(
    private val manifestProcessor: IManifestProcessorService,
    private val profileManager: ProfileManager,
    private val javaManager: JavaManagerService
) : ILauncherService {

    private val log = LoggerFactory.getLogger(LauncherService::class.java)
    private val launchConfigs: Map<String, LaunchConfig> = buildLaunchConfigMap()

    private data class LaunchConfig(
        val mainClass: String,
        val tweakClass: String?,
        val assetIndex: String,
        val jvmArgs: List<String>,
        val nativesDir: String
    )

    private enum class OS { WINDOWS, LINUX, MACOS, UNKNOWN }

    @Throws(IOException::class)
    override fun launchClient(
        sessionData: SessionData,
        serverProfile: ServerProfile,
        clientRootPath: Path,
        javaExecutablePath: Path,
        allocatedMemoryMB: Int
    ): Process {
        val profile: InstanceProfile = profileManager.getProfile(serverProfile.assetDir)

        // Память
        var memory = if (profile.memoryMb != null && profile.memoryMb!! > 0) profile.memoryMb!! else allocatedMemoryMB
        if (memory < 768) memory = 1024

        // Java
        val javaExec: String = when {
            !profile.javaPath.isNullOrEmpty() -> profile.javaPath!!
            Files.exists(javaExecutablePath) -> javaExecutablePath.toString()
            else -> javaManager.getJavaPath(serverProfile.version).toString()
        }

        log.info("Starting {} with Java: {}, RAM: {}", serverProfile.name, javaExec, memory)

        val version = serverProfile.version
        val config = launchConfigs[version] ?: throw IOException("Unsupported version: $version")

        // === МОДЫ: Логика и Диагностика ===
        val allMods = manifestProcessor.getOptionalModsForClient(serverProfile)
        val manifest = sessionData.fileManifest ?: FileManifest()

        // Передаем в syncMods для отладки
        syncMods(clientRootPath, profile, allMods, manifest, version)

        prepareNatives(clientRootPath, config.nativesDir, version)
        prepareAssets(clientRootPath, "assets-$version.zip")

        val jvmArgs = ArrayList<String>()
        jvmArgs.add(javaExec)

        // [CRITICAL] FIX FOR JAVA 8u300+ AND OLD FORGE/MIXIN
        jvmArgs.add("-noverify")
        // Можно попробовать добавить еще это, если -noverify мало:
        // jvmArgs.add("-Djava.compiler=NONE")

        if (getPlatform() == OS.MACOS) {
            jvmArgs.add("-XstartOnFirstThread")
            jvmArgs.add("-Djava.awt.headless=false")
        }

        jvmArgs.add("-Dminecraft.api.auth.host=http://www.smartycraft.ru/launcher/")
        jvmArgs.add("-Dminecraft.api.account.host=http://www.smartycraft.ru/launcher/")
        jvmArgs.add("-Dminecraft.api.session.host=http://www.smartycraft.ru/launcher/")
        jvmArgs.add("-Dminecraft.launcher.brand=smartycraft")
        jvmArgs.add("-Dlauncher.version=3.0.0")

        jvmArgs.addAll(config.jvmArgs)
        if (!profile.jvmArgs.isNullOrEmpty()) {
            jvmArgs.addAll(profile.jvmArgs!!.split(" "))
        }

        jvmArgs.add("-Xms512M")
        jvmArgs.add("-Xmx${memory}M")

        val nativesPath = clientRootPath.resolve(config.nativesDir)
        jvmArgs.add("-Djava.library.path=" + nativesPath.toAbsolutePath())
        jvmArgs.add("-cp")
        jvmArgs.add(buildClasspath(clientRootPath, manifest))
        jvmArgs.add(config.mainClass)

        jvmArgs.addAll(buildMinecraftArgs(sessionData, serverProfile, clientRootPath, config.assetIndex))

        if (config.tweakClass != null) {
            jvmArgs.add("--tweakClass")
            jvmArgs.add(config.tweakClass)
        }

        val pb = ProcessBuilder(jvmArgs)
        pb.directory(clientRootPath.toFile())
        pb.inheritIO()

        log.info("--------------------------------------------------")
        log.info("LAUNCH COMMAND: {}", java.lang.String.join(" ", jvmArgs))
        log.info("--------------------------------------------------")

        return pb.start()
    }

    private fun buildMinecraftArgs(
        sessionData: SessionData,
        serverProfile: ServerProfile,
        clientRootPath: Path,
        assetIndex: String
    ): List<String> {
        val args = ArrayList<String>()
        args.add("--username")
        args.add(sessionData.playerName)
        args.add("--version")
        args.add("Forge " + serverProfile.version)
        args.add("--gameDir")
        args.add(clientRootPath.toAbsolutePath().toString())
        args.add("--assetsDir")
        args.add(clientRootPath.resolve("assets").toAbsolutePath().toString())
        args.add("--assetIndex")
        args.add(assetIndex)
        args.add("--uuid")
        args.add(sessionData.uuid)
        args.add("--accessToken")
        args.add(sessionData.accessToken)
        args.add("--userProperties")
        args.add("{}")
        args.add("--userType")
        args.add("mojang")
        return args
    }

    private fun prepareNatives(clientRoot: Path, nativesDirName: String, version: String) {
        val binDir = clientRoot.resolve("bin")
        val nativesDir = clientRoot.resolve(nativesDirName)

        if (!Files.exists(nativesDir) || nativesDir.toFile().list()?.isEmpty() == true) {
            val targetZipName = "natives-$version.zip"
            val nativesZip = binDir.resolve(targetZipName)
            if (Files.exists(nativesZip)) {
                log.info("Extracting server natives...")
                try {
                    ZipUtils.unzip(nativesZip.toFile(), nativesDir.toFile())
                } catch (e: IOException) {
                    log.error("Failed to unzip server natives", e)
                }
            }
        }

        val currentOS = getPlatform()
        if (!checkNativesIntegrity(nativesDir, currentOS)) {
            log.warn("⚠️ Natives for {} are missing or incomplete! Downloading fallback...", currentOS)
            downloadFallbackNatives(nativesDir, version, currentOS)
        }
    }

    private fun checkNativesIntegrity(nativesDir: Path, os: OS): Boolean {
        try {
            Files.list(nativesDir).use { stream ->
                val files = stream.map { it.fileName.toString() }.collect(Collectors.toList())
                if (files.isEmpty()) return false
                return when (os) {
                    OS.WINDOWS -> files.any { it.endsWith(".dll") }
                    OS.LINUX -> files.any { it.endsWith(".so") }
                    OS.MACOS -> files.any { it.endsWith(".dylib") || it.endsWith(".jnilib") }
                    else -> true
                }
            }
        } catch (e: IOException) {
            return false
        }
    }

    private fun downloadFallbackNatives(targetDir: Path, version: String, os: OS) {
        try {
            val baseUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform"
            val artifactVersion: String = if ("1.7.10" == version) "2.9.1" else "2.9.4-nightly-20150209"
            val osSuffix = when (os) {
                OS.LINUX -> "natives-linux"
                OS.MACOS -> "natives-osx"
                OS.WINDOWS -> "natives-windows"
                else -> throw IllegalStateException("Unsupported OS: $os")
            }
            val fileName = "lwjgl-platform-$artifactVersion-$osSuffix.jar"
            val url = "$baseUrl/$artifactVersion/$fileName"
            log.info("Downloading natives from: {}", url)

            val tempJar = Files.createTempFile("natives_$osSuffix", ".jar")
            val downloadUrl = URL(url)
            downloadUrl.openStream().use { input ->
                Files.copy(input, tempJar, StandardCopyOption.REPLACE_EXISTING)
            }
            ZipUtils.unzip(tempJar.toFile(), targetDir.toFile())
            Files.deleteIfExists(tempJar)
            log.info("✅ Natives for {} installed successfully!", os)
        } catch (e: Exception) {
            log.error("Failed to download fallback natives", e)
        }
    }

    private fun prepareAssets(clientRoot: Path, assetsZipName: String) {
        val assetsDir = clientRoot.resolve("assets")
        val assetsZip = clientRoot.resolve(assetsZipName)
        if (Files.exists(assetsZip)) {
            val indexesDir = assetsDir.resolve("indexes").toFile()
            if (!indexesDir.exists() || !indexesDir.isDirectory) {
                log.info("Extracting assets archive...")
                try {
                    ZipUtils.unzip(assetsZip.toFile(), assetsDir.toFile())
                } catch (e: IOException) {
                    log.error("Failed to unzip assets", e)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun syncMods(
        clientRoot: Path,
        profile: InstanceProfile,
        allMods: List<OptionalMod>,
        manifest: FileManifest,
        clientVersion: String
    ) {
        val modsDir = clientRoot.resolve("mods")
        if (!Files.exists(modsDir)) Files.createDirectories(modsDir)

        val allowedFiles = HashSet<String>()

        // Собираем имена файлов всех опциональных модов, чтобы исключить их из "обязательных"
        val optionalJarNames = HashSet<String>()
        for (mod in allMods) {
            optionalJarNames.addAll(mod.jars)
        }

        // 1. Обязательные моды из манифеста (ИСКЛЮЧАЯ ОПЦИОНАЛЬНЫЕ)
        val flatFiles = manifestProcessor.flattenManifest(manifest)
        for (path in flatFiles.keys) {
            if (path.contains("/mods/") || path.startsWith("mods/")) {
                val fileName = File(path).name

                // Если этот файл есть в списке опциональных модов, пропускаем его здесь.
                // Его судьбу решит блок ниже.
                if (!optionalJarNames.contains(fileName)) {
                    allowedFiles.add(fileName)
                }
            }
        }

        log.info("DEBUG: Mandatory mods count: ${allowedFiles.size}")

        // 2. Опциональные моды (Включение по выбору игрока)
        val state = profile.optionalModsState

        for (mod in allMods) {
            // Если в профиле нет записи, берем isDefault
            val isEnabled = state.getOrDefault(mod.id, mod.isDefault)

            if (isEnabled) {
                allowedFiles.addAll(mod.jars)
                log.info("DEBUG: Mod ENABLED: ${mod.name}")
            } else {
                log.info("DEBUG: Mod DISABLED: ${mod.name}")
            }
        }

        // 3. Очистка
        cleanDirectory(modsDir, allowedFiles)

        if (clientVersion.isNotEmpty()) {
            val versionModsDir = modsDir.resolve(clientVersion)
            if (Files.exists(versionModsDir)) {
                cleanDirectory(versionModsDir, allowedFiles)
            }
        }
    }

    @Throws(IOException::class)
    private fun cleanDirectory(dir: Path, allowedFiles: Set<String>) {
        log.info("---------------- CLEANUP ----------------")
        log.info("Allowed files count: ${allowedFiles.size}")

        Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { path ->
                    val name = path.fileName.toString()
                    name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".litemod")
                }
                .forEach { path ->
                    val fileName = path.fileName.toString()

                    if (allowedFiles.contains(fileName)) {
                        log.info("KEEP: $fileName")
                    } else {
                        log.warn("DELETE: $fileName (Not in allowed list)")
                        try {
                            Files.delete(path)
                        } catch (e: IOException) {
                            log.error("Failed to delete $fileName", e)
                        }
                    }
                }
        }
        log.info("-----------------------------------------")
    }

    private fun buildClasspath(clientRootPath: Path, manifest: FileManifest): String {
        return manifestProcessor.flattenManifest(manifest).keys.stream()
            .filter { f -> f.endsWith(".jar") }
            .filter { f -> !f.contains("/mods/") }
            .sorted { p1, p2 ->
                if (p1.contains("launchwrapper")) return@sorted -1
                if (p2.contains("launchwrapper")) return@sorted 1
                p1.compareTo(p2)
            }
            .map { clientRootPath.resolve(it) }
            .map { it.toString() }
            .collect(Collectors.joining(File.pathSeparator))
    }

    private fun buildLaunchConfigMap(): Map<String, LaunchConfig> {
        return mapOf(
            "1.7.10" to LaunchConfig(
                "net.minecraft.launchwrapper.Launch",
                "cpw.mods.fml.common.launcher.FMLTweaker",
                "1.7.10",
                listOf("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"),
                "bin/natives-1.7.10"
            ),
            "1.12.2" to LaunchConfig(
                "net.minecraft.launchwrapper.Launch",
                "net.minecraftforge.fml.common.launcher.FMLTweaker",
                "1.12.2",
                listOf(
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
        )
    }

    private fun getPlatform(): OS {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        if (osName.contains("win")) return OS.WINDOWS
        if (osName.contains("mac")) return OS.MACOS
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return OS.LINUX
        return OS.UNKNOWN
    }
}
