package hivens.launcher.component

import hivens.core.util.ZipUtils
import hivens.launcher.util.ClientFileHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.stream.Collectors

class EnvironmentPreparer {
    private val log = LoggerFactory.getLogger(EnvironmentPreparer::class.java)

    private val lwjglModules = listOf(
        "lwjgl", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl",
        "lwjgl-glfw", "lwjgl-stb", "lwjgl-tinyfd"
    )
    private val lwjglVersion = "3.3.3"

    fun prepareNatives(clientRoot: Path, nativesDirName: String, version: String) {
        val binDir = clientRoot.resolve("bin")
        val nativesDir = clientRoot.resolve(nativesDirName)
        val osSuffix = getOsSuffix()

        // 1. Проверка
        if (isFolderValidForOs(nativesDir, osSuffix)) {
            log.info("Natives valid for $osSuffix.")
            return
        }

        if (Files.exists(nativesDir)) {
            ClientFileHelper.cleanDirectory(nativesDir, emptySet(), log)
        }
        ClientFileHelper.ensureDirectoryExists(nativesDir)

        log.info("Preparing natives for $version ($osSuffix)...")

        // 2. Локальный Zip
        val targetZipName = "natives-$version-$osSuffix.zip"
        val genericZipName = "natives-$version.zip"

        var nativesZip = binDir.resolve(targetZipName)
        if (!Files.exists(nativesZip)) {
            val generic = binDir.resolve(genericZipName)
            if (Files.exists(generic)) nativesZip = generic
        }

        var unpackedSuccessfully = false
        if (Files.exists(nativesZip)) {
            log.info("Found local zip. Unpacking...")
            try {
                ZipUtils.unzip(nativesZip.toFile(), nativesDir.toFile())
                flattenNatives(nativesDir) // <--- НОВОЕ: Вытаскиваем файлы из подпапок

                if (isFolderValidForOs(nativesDir, osSuffix)) {
                    unpackedSuccessfully = true
                } else {
                    log.warn("Local zip content was invalid for $osSuffix. Cleaning...")
                    ClientFileHelper.cleanDirectory(nativesDir, emptySet(), log)
                }
            } catch (e: Exception) {
                log.error("Failed to unzip local natives", e)
            }
        }

        // 3. Maven
        if (!unpackedSuccessfully) {
            log.warn("Downloading natives from Maven Central...")
            downloadFromMaven(nativesDir, osSuffix)
            flattenNatives(nativesDir) // <--- НОВОЕ: Вытаскиваем файлы

            if (!isFolderValidForOs(nativesDir, osSuffix)) {
                log.error("CRITICAL: Failed to provide natives via Maven!")
            }
        }
    }

    /**
     * Перемещает все .so/.dll/.dylib файлы из подпапок в корень nativesDir.
     * Maven архивы LWJGL содержат файлы в linux/x64/org/..., а Java ищет их в корне.
     */
    private fun flattenNatives(dir: Path) {
        try {
            val libraries = Files.walk(dir)
                .filter { Files.isRegularFile(it) }
                .filter {
                    val name = it.fileName.toString()
                    name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")
                }
                .collect(Collectors.toList())

            for (lib in libraries) {
                val target = dir.resolve(lib.fileName)
                // Если файл не в корне, переносим его
                if (lib.parent != dir) {
                    Files.move(lib, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to flatten natives directory", e)
        }
    }

    private fun isFolderValidForOs(dir: Path, os: String): Boolean {
        if (!Files.exists(dir)) return false
        val expectedExtension = when (os) {
            "linux" -> ".so"
            "windows" -> ".dll"
            "macos" -> ".dylib"
            else -> return false
        }
        // Ищем файл именно в КОРНЕ папки (depth 1)
        return try {
            Files.list(dir).use { stream ->
                stream.anyMatch { it.toString().lowercase().endsWith(expectedExtension) }
            }
        } catch (e: Exception) { false }
    }

    private fun downloadFromMaven(destDir: Path, os: String) {
        val mavenOsClassifier = "natives-$os"
        val baseUrl = "https://repo1.maven.org/maven2/org/lwjgl"

        for (module in lwjglModules) {
            try {
                val fileName = "$module-$lwjglVersion-$mavenOsClassifier.jar"
                val url = "$baseUrl/$module/$lwjglVersion/$fileName"
                val tempJar = Files.createTempFile("aura_native_", ".jar")

                log.info("Downloading: $module")
                URL(url).openStream().use { input ->
                    Files.copy(input, tempJar, StandardCopyOption.REPLACE_EXISTING)
                }
                ZipUtils.unzip(tempJar.toFile(), destDir.toFile())
                Files.delete(tempJar)
            } catch (e: Exception) {
                log.error("Failed to fetch $module", e)
            }
        }
    }

    // Метод prepareAssets оставляем без изменений
    fun prepareAssets(clientRoot: Path, assetsZipName: String) {
        val assetsDir = clientRoot.resolve("assets")
        val objectsDir = assetsDir.resolve("objects")
        val assetsZip = clientRoot.resolve(assetsZipName)

        var needUnzip = false
        if (Files.exists(assetsZip)) {
            if (!Files.exists(objectsDir)) {
                needUnzip = true
            } else {
                try {
                    if (Files.list(objectsDir).count() < 10) needUnzip = true
                } catch (e: Exception) { needUnzip = true }
            }
        }

        if (needUnzip) {
            log.info("Unpacking assets archive...")
            try {
                ClientFileHelper.ensureDirectoryExists(assetsDir)
                ZipUtils.unzip(assetsZip.toFile(), assetsDir.toFile())
            } catch (e: IOException) {
                log.error("Failed to unzip assets", e)
            }
        }
    }

    private fun getOsSuffix(): String {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        return when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> "linux"
            else -> "unknown"
        }
    }
}
