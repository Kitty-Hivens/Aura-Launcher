package hivens.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import hivens.core.data.SessionData
import hivens.ui.di
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: (SessionData) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savePassword by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    // Загрузка сохраненного логина (как в initialize() старого контроллера)
    LaunchedEffect(Unit) {
        val saved = di.credentialsManager.load()
        if (saved != null) {
            username = saved.username
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.width(300.dp)
        ) {
            Text("Aura Launcher", style = MaterialTheme.typography.h4)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
                // VisualTransformation для звездочек можно добавить позже
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = savePassword,
                    onCheckedChange = { savePassword = it },
                    enabled = !isLoading
                )
                Text("Запомнить пароль")
            }

            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colors.error)
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            // Логика из LoginController.onLogin
                            val serverId = di.profileManager.lastServerId ?: "Industrial" // Заглушка или выбор
                            val session = di.authService.login(username, password, serverId)

                            if (savePassword) {
                                di.credentialsManager.save(username, password)
                            } else {
                                di.credentialsManager.clear()
                            }

                            // Сохраняем имя для UI настроек
                            val settings = di.settingsService.getSettings()
                            settings.savedUsername = session.playerName
                            di.settingsService.saveSettings(settings)

                            onLoginSuccess(session)
                        } catch (e: Exception) {
                            errorMessage = "Ошибка: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Войти")
            }
        }
    }
}
