package com.example.universalplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.AdvancedSettings
import com.example.universalplayer.data.AdvancedStore
import com.example.universalplayer.data.DrmScheme
import com.example.universalplayer.data.UserAgentPreset
import com.example.universalplayer.normalizeUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(onPlay: (String) -> Unit) {
    val s by AdvancedStore.settings.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }

    fun set(new: AdvancedSettings) = AdvancedStore.update(new)

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Advanced") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "These settings are used for every video you play. Leave a field empty to skip it. " +
                        "Set a DRM license URI only for DRM-protected links, because normal links " +
                        "will not play with it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = s.link,
                onValueChange = { set(s.copy(link = it)); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Video link") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = s.cookie,
                onValueChange = { set(s.copy(cookie = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cookie value") },
                minLines = 1,
                maxLines = 3
            )
            OutlinedTextField(
                value = s.referer,
                onValueChange = { set(s.copy(referer = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Referer value") },
                singleLine = true
            )
            OutlinedTextField(
                value = s.origin,
                onValueChange = { set(s.copy(origin = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Origin value") },
                singleLine = true
            )
            SettingDropdown(
                label = "User agent",
                options = UserAgentPreset.entries,
                selected = s.userAgent,
                text = { it.label },
                onSelect = { set(s.copy(userAgent = it)) }
            )
            OutlinedTextField(
                value = s.drmLicenseUri,
                onValueChange = { set(s.copy(drmLicenseUri = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DRM license URI") },
                singleLine = true
            )
            SettingDropdown(
                label = "DRM scheme",
                options = DrmScheme.entries,
                selected = s.drmScheme,
                text = { it.label },
                onSelect = { set(s.copy(drmScheme = it)) }
            )

            Button(
                onClick = {
                    val u = normalizeUrl(s.link)
                    if (u == null) error = "Enter a valid video link" else onPlay(u)
                },
                enabled = s.link.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingDropdown(
    label: String,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
