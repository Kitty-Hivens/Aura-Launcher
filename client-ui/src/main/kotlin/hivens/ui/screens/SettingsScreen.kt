package hivens.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hivens.ui.components.CaelestiaButton
import hivens.ui.components.GlassCard
import hivens.ui.di
import hivens.ui.theme.CaelestiaTheme
import java.awt.FileDialog
import java.io.File
import javax.swing.JFrame
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val currentSettings = di.settingsService.getSettings()

    var memory by remember { mutableStateOf(currentSettings.memoryMB.toFloat()) }
    val autoClose by remember { mutableStateOf(currentSettings.closeAfterStart) }
    val javaPath by remember { mutableStateOf(currentSettings.javaPath ?: "") }

    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Text("ГЛОБАЛЬНЫЕ НАСТРОЙКИ", style = MaterialTheme.typography.h4, color = CaelestiaTheme.colors.textPrimary)
        Spacer(Modifier.height(24.dp))

        GlassCard(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxSize().padding(32.dp).verticalScroll(scrollState)
            ) {

                // --- Интерфейс ---
                Text("ИНТЕРФЕЙС", style = MaterialTheme.typography.subtitle2, color = CaelestiaTheme.colors.primary)
                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Темная тема", color = CaelestiaTheme.colors.textPrimary)
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CaelestiaTheme.colors.primary)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Divider(color = CaelestiaTheme.colors.border)
                Spacer(Modifier.height(24.dp))

                // ... (Остальной код настроек памяти и Java такой же, как в прошлом примере, только стили обновлены) ...
                // --- Блок Памяти ---
                Text("ВЫДЕЛЕНИЕ ПАМЯТИ (RAM)", style = MaterialTheme.typography.subtitle2, color = CaelestiaTheme.colors.primary)
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${memory.roundToInt()} MB", style = MaterialTheme.typography.subtitle1, modifier = Modifier.width(80.dp), color = CaelestiaTheme.colors.textPrimary)
                    Slider(
                        value = memory,
                        onValueChange = { memory = it },
                        valueRange = 1024f..16384f,
                        steps = 14,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = CaelestiaTheme.colors.primary, activeTrackColor = CaelestiaTheme.colors.primary)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Divider(color = CaelestiaTheme.colors.border)
                Spacer(Modifier.height(24.dp))

                // --- Кнопка Сохранить ---
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CaelestiaButton(
                        text = "СОХРАНИТЬ",
                        onClick = {
                            val newSettings = di.settingsService.getSettings()
                            newSettings.memoryMB = memory.roundToInt()
                            newSettings.closeAfterStart = autoClose
                            newSettings.javaPath = javaPath.ifBlank { null }

                            di.settingsService.saveSettings(newSettings)
                        },
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }
    }
}

private fun pickJavaFile(): File? {
    val dialog = FileDialog(null as JFrame?, "Выберите Java", FileDialog.LOAD)
    dialog.isVisible = true
    return if (dialog.directory != null && dialog.file != null) File(dialog.directory, dialog.file) else null
}