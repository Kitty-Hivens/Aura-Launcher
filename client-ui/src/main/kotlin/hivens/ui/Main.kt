package hivens.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import hivens.core.api.model.ServerProfile
import hivens.core.data.SessionData
import hivens.launcher.LauncherDI
import hivens.ui.components.GlassCard
import hivens.ui.screens.*
import hivens.ui.theme.CaelestiaTheme
import hivens.ui.utils.SkinManager
import kotlinx.coroutines.launch

val di = LauncherDI()

sealed class AppState {
    object Login : AppState()
    data class Shell(val session: SessionData) : AppState()
}

sealed class ShellScreen {
    object Home : ShellScreen()
    object Profile : ShellScreen()
    object GlobalSettings : ShellScreen()
    data class ServerSettings(val server: ServerProfile) : ShellScreen()
}

fun main() = application {
    val windowState = rememberWindowState(width = 1000.dp, height = 650.dp)

    // Состояние темы (по умолчанию темная)
    var isDarkTheme by remember { mutableStateOf(true) }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Aura Launcher",
        resizable = false
    ) {
        CaelestiaTheme(useDarkTheme = isDarkTheme) {
            var appState by remember { mutableStateOf<AppState>(AppState.Login) }

            LaunchedEffect(Unit) {
                val creds = di.credentialsManager.load()
                if (creds?.decryptedPassword != null) {
                    try {
                        val lastServer = di.profileManager.lastServerId ?: "Industrial"
                        val session = di.authService.login(creds.username, creds.decryptedPassword!!, lastServer)
                        appState = AppState.Shell(session)
                    } catch (e: Exception) {
                        println("Auto-login failed: ${e.message}")
                    }
                }
            }

            when (val state = appState) {
                is AppState.Login -> LoginScreen(
                    onLoginSuccess = { session -> appState = AppState.Shell(session) }
                )
                is AppState.Shell -> ShellUI(
                    initialSession = state.session,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onLogout = {
                        di.credentialsManager.clear()
                        appState = AppState.Login
                    },
                    onCloseApp = ::exitApplication
                )
            }
        }
    }
}

@Composable
fun ShellUI(
    initialSession: SessionData,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onCloseApp: () -> Unit
) {
    var currentSession by remember { mutableStateOf(initialSession) }
    var currentScreen by remember { mutableStateOf<ShellScreen>(ShellScreen.Home) }
    var selectedServer by remember { mutableStateOf<ServerProfile?>(null) }

    // Загрузка лица для аватара
    var faceBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(currentSession.playerName) {
        faceBitmap = SkinManager.getSkinFront(currentSession.playerName)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        val bgColors = if (isDarkTheme)
            listOf(Color(0xFF0F0F0F), Color(0xFF151515))
        else
            listOf(Color(0xFFFFFFFF), Color(0xFFF0F0F0))

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors = bgColors)))

        Row(Modifier.fillMaxSize().padding(24.dp)) {
            // --- DOCK ---
            GlassCard(
                modifier = Modifier.width(80.dp).fillMaxHeight(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    Modifier.fillMaxSize().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Аватарка
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CaelestiaTheme.colors.surface)
                            .border(1.dp, CaelestiaTheme.colors.primary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (faceBitmap != null) {
                            Image(
                                painter = BitmapPainter(faceBitmap!!),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).offset(y = 4.dp), // Смещаем, чтобы лицо было по центру
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter
                            )
                        } else {
                            Text(
                                currentSession.playerName.take(1).uppercase(),
                                color = CaelestiaTheme.colors.primary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    NavButton(Icons.Default.Home, currentScreen is ShellScreen.Home || currentScreen is ShellScreen.ServerSettings) {
                        currentScreen = ShellScreen.Home
                    }
                    NavButton(Icons.Default.Person, currentScreen is ShellScreen.Profile) {
                        currentScreen = ShellScreen.Profile
                    }
                    NavButton(Icons.Default.Settings, currentScreen is ShellScreen.GlobalSettings) {
                        currentScreen = ShellScreen.GlobalSettings
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = CaelestiaTheme.colors.error.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(Modifier.width(24.dp))

            // --- CONTENT ---
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Crossfade(targetState = currentScreen) { screen ->
                    when (screen) {
                        is ShellScreen.Home -> DashboardScreen(
                            session = currentSession,
                            initialSelectedServer = selectedServer,
                            onServerSelected = { server -> selectedServer = server },
                            onSessionUpdated = { newSession -> currentSession = newSession },
                            onCloseApp = onCloseApp,
                            onOpenServerSettings = { server -> currentScreen = ShellScreen.ServerSettings(server) }
                        )
                        is ShellScreen.Profile -> ProfileScreen(currentSession)
                        is ShellScreen.GlobalSettings -> SettingsScreen(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = onToggleTheme
                        )
                        is ShellScreen.ServerSettings -> ServerSettingsScreen(
                            server = screen.server,
                            onBack = { currentScreen = ShellScreen.Home }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavButton(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) CaelestiaTheme.colors.primary else CaelestiaTheme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
    }
}
