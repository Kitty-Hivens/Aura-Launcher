package hivens.ui.logic

import hivens.core.api.model.ServerProfile
import hivens.core.data.SessionData
import hivens.launcher.LauncherDI
import hivens.ui.di
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class LaunchUseCase(private val di: LauncherDI) {

    suspend fun launch(
        currentSession: SessionData,
        server: ServerProfile,
        onProgress: (Float, String) -> Unit,
        onSessionUpdated: (SessionData) -> Unit // Колбэк, если сессия обновилась
    ) = withContext(Dispatchers.IO) {
        
        var activeSession = currentSession
        val targetServerId = server.assetDir
        val clientRoot = di.dataDirectory.resolve("clients").resolve(targetServerId)

        // --- ШАГ 1: Lazy Re-Auth (Как в UpdateAndLaunchTask.java) ---
        if (activeSession.serverId != targetServerId) {
            onProgress(0f, "Смена сервера: $targetServerId...")
            
            if (activeSession.cachedPassword != null) {
                try {
                    // Ре-логин для получения правильного манифеста
                    activeSession = di.authService.login(
                        activeSession.playerName,
                        activeSession.cachedPassword!!,
                        targetServerId
                    )
                    // Сообщаем UI, что сессия обновилась (чтобы сохранить новый токен если надо)
                    onSessionUpdated(activeSession)
                } catch (e: Exception) {
                    throw Exception("Ошибка смены сервера: ${e.message}")
                }
            } else {
                throw Exception("Требуется ручной перезаход в аккаунт (пароль не сохранен).")
            }
        }

        // --- ШАГ 2: Загрузка ---
        onProgress(0f, "Проверка файлов...")
        
        // Тут можно добавить логику ManifestProcessor для игнорирования модов (excludings)
        // Пока базовый сет
        val ignoredFiles = emptySet<String>()

        di.downloadService.processSession(
            activeSession,
            targetServerId,
            clientRoot,
            server.extraCheckSum,
            ignoredFiles,
            messageUI = { msg -> onProgress(0.5f, msg) },
            progressUI = { cur, total -> 
                val p = if (total > 0) cur.toFloat() / total else 0f
                onProgress(p, "Загрузка: $cur / $total")
            }
        )

        // --- ШАГ 3: Запуск ---
        onProgress(1f, "Запуск Java...")
        
        val settings = di.settingsService.getSettings()
        val javaPath = if (!settings.javaPath.isNullOrEmpty()) Path.of(settings.javaPath!!) else null
        val memory = settings.memoryMB

        val process = di.launcherService.launchClient(
            activeSession,
            server,
            clientRoot,
            javaPath ?: di.javaManagerService.getJavaPath(server.version),
            memory
        )

        // Ждем запуска (чтобы UI не разблокировался сразу)
        // Но не ждем закрытия игры, если стоит галочка "Закрыть лаунчер"
        // В JavaFX task ждал waitFor(), тут мы можем вернуть Process
        
        return@withContext process
    }
}