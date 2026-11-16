package com.universal.videoplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universal.videoplayer.data.Keys
import com.universal.videoplayer.data.Scaling
import com.universal.videoplayer.data.ThemeMode

private val speedChoices = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
fun speedLabel(v: Float) = "%.2f".format(v).trimEnd('0').let { if (it.endsWith(".")) it + "0" else it } + "x"

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> PickDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(o); onDismiss() }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(o == selected, { onPick(o); onDismiss() })
                        Text(label(o))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycleCompat()
    var dialog by remember { mutableStateOf<String?>(null) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Section("Appearance")
            ThemeMode.values().forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.set(Keys.THEME, m.name) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(s.theme == m, { vm.set(Keys.THEME, m.name) })
                    Text(when (m) { ThemeMode.SYSTEM -> "System Default"; ThemeMode.LIGHT -> "Light Theme"; ThemeMode.DARK -> "Dark Theme" })
                }
            }

            Section("Playback")
            ValueRow("Default Playback Speed", speedLabel(s.speed)) { dialog = "speed" }
            ValueRow("Default Scaling Mode", s.scaling.label) { dialog = "scaling" }
            SwitchRow("Auto Resume", "Continue from the last watched position", s.autoResume) { vm.set(Keys.AUTO_RESUME, it) }
            SwitchRow("Auto Hide Controls", "Hide player controls after a few seconds", s.autoHide) { vm.set(Keys.AUTO_HIDE, it) }

            Section("Clipboard")
            SwitchRow("Detect Copied Links", "Checked only when the app opens or resumes", s.clipDetect) { vm.set(Keys.CLIP_DETECT, it) }
            SwitchRow("Ask Before Playing", "Show a prompt instead of just filling the link box", s.clipAsk) { vm.set(Keys.CLIP_ASK, it) }

            Section("History")
            SwitchRow("Enable History", null, s.historyEnabled) { vm.set(Keys.HISTORY, it) }
            SwitchRow("Automatically Save Progress", null, s.saveProgress) { vm.set(Keys.SAVE_PROGRESS, it) }
            ValueRow("Clear History", "") { dialog = "clear" }

            Section("Network")
            SwitchRow("Retry on Network Error", "Retry failed segment loads more times", s.retryOnError) { vm.set(Keys.RETRY, it) }
            SwitchRow("Playback Error Details", "Show technical details on error screens", s.errorDetails) { vm.set(Keys.ERROR_DETAILS, it) }

            Section("About")
            ValueRow("App Version", version) {}
            ValueRow("Open Source Licenses", "") { dialog = "licenses" }
            ValueRow("Privacy Policy", "") { dialog = "privacy" }
            ValueRow("Developer Information", "") { dialog = "dev" }
        }
    }

    when (dialog) {
        "speed" -> PickDialog("Default playback speed", speedChoices, s.speed, ::speedLabel, { vm.set(Keys.SPEED, it) }) { dialog = null }
        "scaling" -> PickDialog("Default scaling mode", Scaling.values().toList(), s.scaling, { it.label }, { vm.set(Keys.SCALING, it.name) }) { dialog = null }
        "clear" -> ConfirmDialog("Clear history?", "This removes all history entries and saved positions.", "Clear", { vm.clearHistory() }) { dialog = null }
        "licenses" -> InfoDialog("Open source licenses",
            "Licensed under Apache License 2.0: AndroidX (Compose, Navigation, Room, DataStore, Lifecycle, Activity, Core), " +
                "AndroidX Media3 (ExoPlayer), Coil, Kotlin and kotlinx.coroutines.\n\nThis app is released under the MIT License.") { dialog = null }
        "privacy" -> InfoDialog("Privacy policy",
            "Universal Video Player has no accounts, analytics or ads. History, playlists and presets are stored only on this device. " +
                "Cookies and headers you enter are used for the current request only and are never saved. " +
                "The clipboard is read only when the app opens or resumes, and only if you leave that setting on. " +
                "Video links you play are requested directly from their servers.") { dialog = null }
        "dev" -> InfoDialog("Developer information",
            "Universal Video Player $version\nOpen source (MIT). Built with Kotlin, Jetpack Compose, Material 3 and Media3, " +
                "and packaged by GitHub Actions.") { dialog = null }
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onDismiss) { Text("OK") } })
}
