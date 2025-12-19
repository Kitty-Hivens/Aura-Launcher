package hivens.launcher.component

import hivens.core.util.ZipUtils
import hivens.launcher.util.ClientFileHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class EnvironmentPreparer {
    private val log = LoggerFactory.getLogger(EnvironmentPreparer::class.java)

    /**
     * Распаковывает нативы для конкретной версии.
     */
    fun prepareNatives(clientRoot: Path, nativesDirName: String, version: String) {
        val binDir = clientRoot.resolve("bin")
        val nativesDir = clientRoot.resolve(nativesDirName)

        // Используем общий хелпер, чтобы не дублировать логику проверки существования
        // Но здесь логика чуть сложнее: если папка пуста или не существует — распаковываем
        val needUnzip = !Files.exists(nativesDir) || (nativesDir.toFile().list()?.isEmpty() == true)

        if (needUnzip) {
            log.info("Unpacking natives for version {}...", version)
            val targetZipName = "natives-$version.zip"
            val nativesZip = binDir.resolve(targetZipName)
            
            if (Files.exists(nativesZip)) {
                try {
                    ClientFileHelper.ensureDirectoryExists(nativesDir)
                    ZipUtils.unzip(nativesZip.toFile(), nativesDir.toFile())
                } catch (e: IOException) {
                    log.error("Failed to unzip server natives: $targetZipName", e)
                }
            } else {
                log.warn("Natives zip not found: {}", nativesZip)
            }
        }
    }

    /**
     * Распаковывает assets.zip (индексы и объекты).
     */
    fun prepareAssets(clientRoot: Path, assetsZipName: String) {
        val assetsDir = clientRoot.resolve("assets")
        val assetsZip = clientRoot.resolve(assetsZipName)

        if (Files.exists(assetsZip)) {
            val indexesDir = assetsDir.resolve("indexes").toFile()
            // Распаковываем, если нет папки индексов (простая эвристика)
            if (!indexesDir.exists() || !indexesDir.isDirectory) {
                log.info("Unpacking assets: {}", assetsZipName)
                try {
                    ClientFileHelper.ensureDirectoryExists(assetsDir)
                    ZipUtils.unzip(assetsZip.toFile(), assetsDir.toFile())
                } catch (e: IOException) {
                    log.error("Failed to unzip assets", e)
                }
            }
        }
    }
}