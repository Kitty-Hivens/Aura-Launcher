package hivens.launcher

import hivens.core.api.interfaces.ILauncherService
import hivens.core.api.interfaces.IManifestProcessorService
import hivens.core.api.model.ServerProfile
import hivens.core.data.FileManifest
import hivens.core.data.InstanceProfile
import hivens.core.data.SessionData
import hivens.launcher.component.EnvironmentPreparer
import hivens.launcher.component.ModManager
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Locale
import java.util.stream.Collectors
import kotlin.concurrent.thread

enum class LauncherLogType { INFO, WARN, ERROR }

class LauncherService(
    private val manifestProcessor: IManifestProcessorService,
    private val profileManager: ProfileManager,
    private val javaManager: JavaManagerService
) : ILauncherService {

    private val log = LoggerFactory.getLogger(LauncherService::class.java)
    
    // Внедряем новые компоненты (можно через DI, здесь создаем вручную для простоты миграции)
    private val modManager = ModManager(manifestProcessor)
    private val envPreparer = EnvironmentPreparer()
    
    // Конфигурации запуска вынесены в карту
    private val launchConfigs: Map<String, LaunchConfig> = buildLaunchConfigMap()

    private data class LaunchConfig(
        val mainClass: String,
        val tweakClass: String?,
        val assetIndex: String,
        val jvmArgs: List<String>,
        val nativesDir: String
    )

    private enum class OS { WINDOWS, LINUX, MACOS, UNKNOWN }

    /**
     * Основной метод запуска с логгированием.
     */
    @Throws(IOException::class)
    fun launchClientWithLogs(
        sessionData: SessionData,
        serverProfile: ServerProfile,
        clientRootPath: Path,
        javaExecutablePath: Path,
        allocatedMemoryMB: Int,
        onLog: (String, LauncherLogType) -> Unit
    ): Process {
        val profile: InstanceProfile = profileManager.getProfile(serverProfile.assetDir)
        val version = serverProfile.version
        val config = launchConfigs[version] ?: throw IOException("Unsupported version: $version")

        // 1. Определение параметров памяти и Java
        var memory = if (profile.memoryMb > 0) profile.memoryMb else allocatedMemoryMB
        if (memory < 768) memory = 1024

        val javaExec: String = resolveJavaPath(profile, javaExecutablePath, version)

        onLog("Starting ${serverProfile.name} with Java: $javaExec, RAM: $memory", LauncherLogType.INFO)
        log.info("Starting {} with Java: {}, RAM: {}", serverProfile.name, javaExec, memory)

        // 2. Делегирование: Подготовка модов
        val allMods = manifestProcessor.getOptionalModsForClient(serverProfile)
        val manifest = sessionData.fileManifest ?: FileManifest()
        modManager.syncMods(clientRootPath, profile, allMods, manifest, version)

        // 3. Делегирование: Подготовка нативов и ассетов
        envPreparer.prepareNatives(clientRootPath, config.nativesDir, version)
        envPreparer.prepareAssets(clientRootPath, "assets-$version.zip")

        // 4. Сборка команды запуска
        val command = buildProcessCommand(
            javaExec, memory, clientRootPath, config, 
            manifest, sessionData, serverProfile, profile
        )

        // 5. Запуск процесса
        val pb = ProcessBuilder(command)
        pb.directory(clientRootPath.toFile())
        pb.redirectErrorStream(false)

        onLog("LAUNCH COMMAND: ${java.lang.String.join(" ", command)}", LauncherLogType.INFO)

        val process = pb.start()

        // 6. Подключение слушателей логов
        pipeOutput(process.inputStream, LauncherLogType.INFO, onLog)
        pipeOutput(process.errorStream, LauncherLogType.ERROR, onLog)

        return process
    }

    override fun launchClient(
        sessionData: SessionData,
        serverProfile: ServerProfile,
        clientRootPath: Path,
        javaExecutablePath: Path,
        allocatedMemoryMB: Int
    ): Process {
        return launchClientWithLogs(sessionData, serverProfile, clientRootPath, javaExecutablePath, allocatedMemoryMB) { _, _ -> }
    }

    // --- Вспомогательные приватные методы ---

    private fun resolveJavaPath(profile: InstanceProfile, defaultPath: Path, version: String): String {
        return when {
            !profile.javaPath.isNullOrEmpty() -> profile.javaPath!!
            Files.exists(defaultPath) -> defaultPath.toString()
            else -> javaManager.getJavaPath(version).toString()
        }
    }

    private fun buildProcessCommand(
        javaExec: String,
        memoryMB: Int,
        clientRoot: Path,
        config: LaunchConfig,
        manifest: FileManifest,
        session: SessionData,
        serverProfile: ServerProfile,
        userProfile: InstanceProfile
    ): List<String> {
        val args = ArrayList<String>()
        args.add(javaExec)
        args.add("-noverify")

        // Платформо-зависимые аргументы
        if (getPlatform() == OS.MACOS) {
            args.add("-XstartOnFirstThread")
            args.add("-Djava.awt.headless=false")
        }

        // Брендинг и API
        args.add("-Dminecraft.api.auth.host=http://www.smartycraft.ru/launcher/")
        args.add("-Dminecraft.api.account.host=http://www.smartycraft.ru/launcher/")
        args.add("-Dminecraft.api.session.host=http://www.smartycraft.ru/launcher/")
        args.add("-Dminecraft.launcher.brand=smartycraft")
        args.add("-Dlauncher.version=3.0.0")

        // Аргументы профиля (глобальные + пользовательские)
        args.addAll(config.jvmArgs)
        if (!userProfile.jvmArgs.isNullOrEmpty()) {
            args.addAll(userProfile.jvmArgs!!.split(" "))
        }

        args.add("-Xms512M")
        args.add("-Xmx${memoryMB}M")

        val nativesPath = clientRoot.resolve(config.nativesDir)
        args.add("-Djava.library.path=" + nativesPath.toAbsolutePath())
        
        // Classpath
        args.add("-cp")
        args.add(buildClasspath(clientRoot, manifest))
        
        // Main Class
        args.add(config.mainClass)

        // Minecraft Arguments
        args.addAll(buildMinecraftArgs(session, serverProfile, clientRoot, config.assetIndex))

        if (config.tweakClass != null) {
            args.add("--tweakClass")
            args.add(config.tweakClass)
        }

        return args
    }

    private fun buildMinecraftArgs(
        session: SessionData,
        profile: ServerProfile,
        root: Path,
        assetIndex: String
    ): List<String> {
        return listOf(
            "--username", session.playerName,
            "--version", "Forge ${profile.version}",
            "--gameDir", root.toAbsolutePath().toString(),
            "--assetsDir", root.resolve("assets").toAbsolutePath().toString(),
            "--assetIndex", assetIndex,
            "--uuid", session.uuid,
            "--accessToken", session.accessToken,
            "--userProperties", "{}",
            "--userType", "mojang"
        )
    }

    private fun buildClasspath(clientRoot: Path, manifest: FileManifest): String {
        return manifestProcessor.flattenManifest(manifest).keys.stream()
            .filter { f -> f.endsWith(".jar") }
            .filter { f -> !f.contains("/mods/") }
            .sorted { p1, p2 ->
                // launchwrapper должен быть в начале, иначе Forge не стартанет
                if (p1.contains("launchwrapper")) return@sorted -1
                if (p2.contains("launchwrapper")) return@sorted 1
                p1.compareTo(p2)
            }
            .map { clientRoot.resolve(it) }
            .map { it.toString() }
            .collect(Collectors.joining(File.pathSeparator))
    }

    private fun pipeOutput(stream: InputStream, type: LauncherLogType, onLog: (String, LauncherLogType) -> Unit) {
        val reader = BufferedReader(InputStreamReader(stream))
        thread(isDaemon = true) {
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val text = line ?: continue
                    // Простая эвристика для подсветки ошибок в консоли
                    val finalType = when {
                        type == LauncherLogType.ERROR -> LauncherLogType.ERROR
                        text.contains("WARN", ignoreCase = true) -> LauncherLogType.WARN
                        text.contains("ERROR", ignoreCase = true) || text.contains("Exception", ignoreCase = true) -> LauncherLogType.ERROR
                        else -> LauncherLogType.INFO
                    }
                    onLog(text, finalType)
                    if (finalType == LauncherLogType.ERROR) System.err.println(text) else println(text)
                }
            } catch (e: Exception) { /* Stream closed */ }
        }
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
        return when {
            osName.contains("win") -> OS.WINDOWS
            osName.contains("mac") -> OS.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> OS.LINUX
            else -> OS.UNKNOWN
        }
    }
}