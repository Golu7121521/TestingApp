package com.universal.videoplayer.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.universal.videoplayer.data.PresetEntity
import com.universal.videoplayer.resolver.DrmScheme
import com.universal.videoplayer.resolver.PlaybackRequest
import com.universal.videoplayer.resolver.UaOption
import com.universal.videoplayer.utils.UrlValidator

@Composable
private fun <T> Dropdown(label: String, current: String, options: List<T>, text: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ open = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text(current, Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(text(o)) }, { onPick(o); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceScreen(vm: AppViewModel, actions: Actions) {
    val ctx = LocalContext.current
    val presets by vm.presets.collectAsStateWithLifecycleCompat()
    var url by rememberSaveable { mutableStateOf("") }
    var cookie by rememberSaveable { mutableStateOf("") }
    var referer by rememberSaveable { mutableStateOf("") }
    var origin by rememberSaveable { mutableStateOf("") }
    var ua by rememberSaveable { mutableStateOf(UaOption.ANDROID_CHROME.name) }
    var customUa by rememberSaveable { mutableStateOf("") }
    var drm by rememberSaveable { mutableStateOf(DrmScheme.NONE.name) }
    var license by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val uaOpt = UaOption.valueOf(ua)
    val drmOpt = DrmScheme.valueOf(drm)

    fun reset() {
        url = ""; cookie = ""; referer = ""; origin = ""; ua = UaOption.ANDROID_CHROME.name
        customUa = ""; drm = DrmScheme.NONE.name; license = ""; error = null
    }

    fun play() {
        val u = UrlValidator.normalize(url)
        if (u == null) { error = "Enter a valid http(s) video link."; return }
        if (drmOpt != DrmScheme.NONE && UrlValidator.normalize(license, requireScheme = true) == null) {
            error = "Enter a valid DRM license URL."; return
        }
        error = null
        actions.play(
            PlaybackRequest(
                url = u,
                userAgent = if (uaOpt == UaOption.CUSTOM) customUa.trim().ifEmpty { null } else uaOpt.value,
                cookie = cookie, referer = referer, origin = origin,
                drmScheme = drmOpt, drmLicense = license,
            ),
            true,
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Advanced playback") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Video source", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                url, { url = it; error = null }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                label = { Text("Video URL") }, isError = error != null, supportingText = { error?.let { Text(it) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    val t = readClipboardText(ctx)
                    if (t.isNullOrBlank()) Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show() else url = t.trim()
                }) { Text("Paste") }
                TextButton({ url = "" }, enabled = url.isNotEmpty()) { Text("Clear") }
            }

            Text("HTTP request headers", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use only with sources you are authorized to access. Cookies are never saved to history or presets.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                cookie, { cookie = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                label = { Text("Cookie") }, visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(referer, { referer = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text("Referer") }, singleLine = true)
            OutlinedTextField(origin, { origin = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text("Origin") }, singleLine = true)
            Dropdown("User-Agent", uaOpt.label, UaOption.values().toList(), { it.label }) { ua = it.name }
            if (uaOpt == UaOption.CUSTOM) {
                OutlinedTextField(customUa, { customUa = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text("Custom User-Agent") })
            }

            Text("DRM configuration", style = MaterialTheme.typography.titleMedium)
            Text(
                "For streams you are licensed to play. Support depends on your device: Widevine is common, " +
                    "PlayReady is rarely available on Android, ClearKey is for compatible test streams.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Dropdown("DRM scheme", drmOpt.label, DrmScheme.values().toList(), { it.label }) { drm = it.name }
            if (drmOpt != DrmScheme.NONE) {
                OutlinedTextField(
                    license, { license = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    label = { Text("DRM license URI") }, singleLine = true,
                    isError = license.isNotBlank() && UrlValidator.normalize(license, true) == null,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ play() }, Modifier.weight(1f)) { Text("Play Video") }
                OutlinedButton({ saving = true }, Modifier.weight(1f)) { Text("Save Configuration") }
            }
            TextButton({ reset() }) { Text("Reset Settings") }

            if (presets.isNotEmpty()) {
                Text("Saved presets", style = MaterialTheme.typography.titleMedium)
                presets.forEach { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(p.url.ifBlank { "(no URL)" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            TextButton({
                                url = p.url; ua = p.uaKey; customUa = p.customUa; referer = p.referer
                                origin = p.origin; drm = p.drmScheme; license = p.drmLicense; error = null
                            }) { Text("Load") }
                            TextButton({ vm.deletePreset(p.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (saving) {
        TextInputDialog("Save configuration", "", "Save", { name ->
            vm.savePreset(PresetEntity(name = name, url = url.trim(), uaKey = ua, customUa = customUa, referer = referer, origin = origin, drmScheme = drm, drmLicense = license))
            Toast.makeText(ctx, "Saved (cookie not included)", Toast.LENGTH_SHORT).show()
        }, { saving = false })
    }
}
