package hivens.core.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import hivens.config.ServiceEndpoints
import hivens.core.api.dto.SmartyResponse
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SmartyNetworkService(baseClient: OkHttpClient, private val gson: Gson) {

    private val logger = LoggerFactory.getLogger(SmartyNetworkService::class.java)

    // URL официального лаунчера для обхода защиты
    private val officialJarUrl = "https://www.smartycraft.ru/downloads/smartycraft.jar"
    private val cachedHashFile = File("smarty_hash.cache")

    // Дефолтный хеш (обновится сам, если устарел)
    private var currentHash = "5515a4bdd5f532faf0db61b8263d1952"

    private val client: OkHttpClient = baseClient.newBuilder()
        .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("proxy.smartycraft.ru", 1080)))
        .proxyAuthenticator { _, response ->
            val credential = Credentials.basic("proxyuser", "proxyuserproxyuser")
            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        // Восстанавливаем хеш из кэша при запуске
        if (cachedHashFile.exists()) {
            try {
                currentHash = Files.readString(cachedHashFile.toPath()).trim()
                logger.info("Loaded cached hash: $currentHash")
            } catch (e: IOException) {
                logger.warn("Failed to read hash cache", e)
            }
        }
    }

    /**
     * Получает данные для Дашборда (Серверы + Новости).
     * Если хеш устарел (статус UPDATE), автоматически обновляет его.
     */
    fun getDashboardResponse(): SmartyResponse {
        var response = tryGetDashboard(currentHash)

        if (response.status == "UPDATE") {
            logger.info("Protection triggered (Status: UPDATE). Initiating bypass...")

            val newHash = downloadAndCalculateHash()

            if (newHash != null) {
                logger.info("Bypass successful. New hash: $newHash")
                currentHash = newHash
                saveHashToCache(newHash)

                // Повторяем запрос с новым хешем
                response = tryGetDashboard(newHash)
            } else {
                logger.error("Bypass failed. Could not calculate new hash.")
            }
        }

        return response
    }

    /**
     * Загружает скин или плащ на сервер.
     */
    fun uploadAsset(file: File, type: String, session: String): String {
        // Используем современные расширения OkHttp (избавляемся от Deprecated)
        val mediaType = "image/png".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "upload")
            .addFormDataPart("type", type)
            .addFormDataPart("session", session)
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(ServiceEndpoints.AUTH_LOGIN)
            .post(requestBody)
            .header("User-Agent", "SMARTYlauncher/3.6.2")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Ошибка сети: ${response.code}"
                response.body?.string()?.trim() ?: "Ошибка: Пустой ответ"
            }
        } catch (e: Exception) {
            logger.error("Upload asset failed", e)
            "Ошибка: ${e.message}"
        }
    }

    // --- Приватные методы ---

    private fun tryGetDashboard(hash: String): SmartyResponse {
        val jsonBody = """
            {
                "version": "3.6.2",
                "cheksum": "$hash",
                "format": "jar",
                "testModeKey": "false",
                "debug": "false"
            }
        """.trimIndent()

        val formBody = FormBody.Builder()
            .add("action", "loader")
            .add("json", jsonBody)
            .build()

        val request = Request.Builder()
            .url(ServiceEndpoints.AUTH_LOGIN)
            .post(formBody)
            .header("User-Agent", "SMARTYlauncher/3.6.2")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return SmartyResponse()

                var rawJson = response.body?.string() ?: return SmartyResponse()

                // Очистка от HTML-мусора
                val jsonStart = rawJson.indexOf("{")
                if (jsonStart != -1) {
                    rawJson = rawJson.substring(jsonStart)
                }

                return try {
                    gson.fromJson(rawJson, SmartyResponse::class.java)
                } catch (e: JsonSyntaxException) {
                    logger.error("Parsing error: $rawJson", e)
                    SmartyResponse()
                }
            }
        } catch (e: Exception) {
            logger.error("Network error in tryGetDashboard", e)
            return SmartyResponse()
        }
    }

    private fun downloadAndCalculateHash(): String? {
        try {
            logger.info("Downloading official launcher form $officialJarUrl...")

            val tempJar = Files.createTempFile("smarty_temp", ".jar")

            val request = Request.Builder()
                .url(officialJarUrl)
                .header("User-Agent", "SMARTYlauncher/3.6.2")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error("Download failed: HTTP ${response.code}")
                    return null
                }
                response.body?.byteStream()?.use { input ->
                    Files.copy(input, tempJar, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            logger.info("Calculating MD5 hash...")
            val digest = MessageDigest.getInstance("MD5")
            Files.newInputStream(tempJar).use { fis ->
                val buffer = ByteArray(1024)
                var bytesCount: Int
                while (fis.read(buffer).also { bytesCount = it } != -1) {
                    digest.update(buffer, 0, bytesCount)
                }
            }

            Files.deleteIfExists(tempJar)

            val bytes = digest.digest()
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
            }
            return sb.toString()

        } catch (e: Exception) {
            logger.error("Failed to calculate new hash", e)
            return null
        }
    }

    private fun saveHashToCache(hash: String) {
        try {
            Files.writeString(cachedHashFile.toPath(), hash)
            logger.info("New hash saved to cache.")
        } catch (e: IOException) {
            logger.error("Failed to save hash cache", e)
        }
    }
}
