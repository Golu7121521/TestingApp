package com.playvid.presentation.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playvid.domain.model.ThemeMode
import com.playvid.presentation.AppViewModelProvider
import com.playvid.presentation.components.LocalNotifier

/** 0 = Auto (highest available). */
private val QUALITIES = listOf(0, 144, 240, 360, 480, 720, 1080, 1440)
private fun qualityLabel(q: Int) = if (q == 0) "Auto" else "${q}p"
private val HOLD_SPEEDS = listOf(2, 3, 4, 5)

/**
 * Settings: simple list. Sections: Playback, Appearance, History, About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var holdSpeedMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Playback")
        Box {
            SettingRow(
                "Starting Quality",
                value = if (s.dataSaver) "Lowest (Data Saver)" else qualityLabel(s.preferredQuality),
                onClick = if (s.dataSaver) null else { { qualityMenu = true } }
            )
            DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                QUALITIES.forEach { q ->
                    DropdownMenuItem(
                        text = { Text(qualityLabel(q)) },
                        onClick = {
                            vm.update { it.copy(preferredQuality = q) }
                            qualityMenu = false
                        }
                    )
                }
            }
        }
        SwitchRow("Data Saver (always start at lowest quality)", s.dataSaver) { v -> vm.update { it.copy(dataSaver = v) } }
        SwitchRow("Resume Playback", s.resumePlayback) { v -> vm.update { it.copy(resumePlayback = v) } }
        SwitchRow("Brightness Gesture", s.brightnessGesture) { v -> vm.update { it.copy(brightnessGesture = v) } }
        SwitchRow("Volume Gesture", s.volumeGesture) { v -> vm.update { it.copy(volumeGesture = v) } }
        SwitchRow("Hold to Fast Forward", s.holdSpeedGesture) { v -> vm.update { it.copy(holdSpeedGesture = v) } }
        Box {
            SettingRow(
                "Hold Speed",
                value = "${s.holdSpeedMultiplier}x",
                onClick = if (s.holdSpeedGesture) { { holdSpeedMenu = true } } else null
            )
            DropdownMenu(expanded = holdSpeedMenu, onDismissRequest = { holdSpeedMenu = false }) {
                HOLD_SPEEDS.forEach { x ->
                    DropdownMenuItem(
                        text = { Text("${x}x") },
                        onClick = {
                            vm.update { it.copy(holdSpeedMultiplier = x) }
                            holdSpeedMenu = false
                        }
                    )
                }
            }
        }
        SwitchRow("Picture-in-Picture (auto on leaving app)", s.pipEnabled) { v -> vm.update { it.copy(pipEnabled = v) } }
        SwitchRow("Keep Playing in Background (after back)", s.backgroundPlayback) { v -> vm.update { it.copy(backgroundPlayback = v) } }

        Section("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val modes = ThemeMode.entries
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = s.theme == mode,
                    onClick = { vm.update { it.copy(theme = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = { Text(mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                )
            }
        }

        Section("History")
        SettingRow("Clear History", onClick = { confirmClear = true })

        Section("About")
        SettingRow("App Version", value = appVersion(context), onClick = null)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirmClear = false
                    notifier.show("History cleared")
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(title: String, value: String? = null, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun appVersion(context: android.content.Context): String = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName ?: "-"
} catch (_: Exception) {
    "-"
}
