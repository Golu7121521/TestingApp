package com.example.universalplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.DRM_SCHEMES
import com.example.universalplayer.data.PlayOptions
import com.example.universalplayer.data.USER_AGENTS
import com.example.universalplayer.normalizeUrl

/** Form state lives in the activity so it survives tab switches. */
class AdvancedForm {
    var link by mutableStateOf("")
    var cookie by mutableStateOf("")
    var referer by mutableStateOf("")
    var origin by mutableStateOf("")
    var drm by mutableStateOf("")
    var uaIndex by mutableIntStateOf(0)
    var drmIndex by mutableIntStateOf(0)

    fun options() = PlayOptions(
        cookie.trim(), referer.trim(), origin.trim(), drm.trim(),
        USER_AGENTS[uaIndex].second, DRM_SCHEMES[drmIndex]
    )

    fun clear() {
        link = ""; cookie = ""; referer = ""; origin = ""; drm = ""; uaIndex = 0; drmIndex = 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(form: AdvancedForm, onPlay: (String, PlayOptions) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Advanced") },
                actions = { TextButton(onClick = { form.clear(); error = null }) { Text("Reset") } }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                form.link, { form.link = it; error = null }, Modifier.fillMaxWidth(),
                label = { Text("Video link") }, singleLine = true,
                isError = error != null, supportingText = { error?.let { Text(it) } }
            )
            OutlinedTextField(
                form.cookie, { form.cookie = it }, Modifier.fillMaxWidth(),
                label = { Text("Cookie value") }, singleLine = true
            )
            OutlinedTextField(
                form.referer, { form.referer = it }, Modifier.fillMaxWidth(),
                label = { Text("Referer value") }, singleLine = true
            )
            OutlinedTextField(
                form.origin, { form.origin = it }, Modifier.fillMaxWidth(),
                label = { Text("Origin value") }, singleLine = true
            )
            Dropdown("User agent", USER_AGENTS.map { it.first }, form.uaIndex) { form.uaIndex = it }
            OutlinedTextField(
                form.drm, { form.drm = it }, Modifier.fillMaxWidth(),
                label = { Text("DRM license URI") }, singleLine = true,
                supportingText = { Text("ClearKey also accepts kid:key (hex), comma separated") }
            )
            Dropdown("DRM scheme", DRM_SCHEMES, form.drmIndex) { form.drmIndex = it }
            Button(
                onClick = {
                    val u = normalizeUrl(form.link)
                    if (u == null) error = "Enter a valid video link" else onPlay(u, form.options())
                },
                enabled = form.link.isNotBlank(),
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
private fun Dropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options[selected], onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}
