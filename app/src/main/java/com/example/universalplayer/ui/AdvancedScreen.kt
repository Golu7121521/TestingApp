package com.example.universalplayer.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.universalplayer.data.CookieVault
import com.example.universalplayer.data.DRM_SCHEMES
import com.example.universalplayer.data.LOGIN_SITES
import com.example.universalplayer.data.LoginSite
import com.example.universalplayer.data.PlayOptions
import com.example.universalplayer.data.USER_AGENTS
import com.example.universalplayer.normalizeUrl
import com.example.universalplayer.player.LinkTester
import com.example.universalplayer.player.PlayerSettings

/** Form state lives in the activity so it survives tab switches. */
class AdvancedForm {
    var link by mutableStateOf("")
    var cookie by mutableStateOf("")
    var referer by mutableStateOf("")
    var origin by mutableStateOf("")
    var drm by mutableStateOf("")
    var drmHeaders by mutableStateOf("")
    var uaIndex by mutableIntStateOf(0)
    var drmIndex by mutableIntStateOf(0)

    fun options() = PlayOptions(
        cookie.trim(), referer.trim(), origin.trim(), drm.trim(),
        USER_AGENTS[uaIndex].second, DRM_SCHEMES[drmIndex], drmHeaders.trim()
    )

    fun clear() {
        link = ""; cookie = ""; referer = ""; origin = ""; drm = ""; drmHeaders = ""; uaIndex = 0; drmIndex = 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(form: AdvancedForm, onLogin: (LoginSite) -> Unit, onPlay: (String, PlayOptions) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var confirmSite by remember { mutableStateOf<LoginSite?>(null) }
    var confirmOut by remember { mutableStateOf<LoginSite?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var bgAudio by remember { mutableStateOf(PlayerSettings.backgroundAudio) }
    var reconnect by remember { mutableStateOf(PlayerSettings.autoReconnect) }
    var bufLevel by remember { mutableIntStateOf(PlayerSettings.bufferLevel) }

    Scaffold(
        topBar = {
            TopAppBar(
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
            SectionTitle("Request headers")
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
            SectionTitle("DRM")
            OutlinedTextField(
                form.drm, { form.drm = it }, Modifier.fillMaxWidth(),
                label = { Text("DRM license URI") }, singleLine = true,
                supportingText = { Text("ClearKey: kid:key (hex), {\"keys\":[...]} JSON, or license URL") }
            )
            Dropdown("DRM scheme", DRM_SCHEMES, form.drmIndex) { form.drmIndex = it }
            OutlinedTextField(
                form.drmHeaders, { form.drmHeaders = it }, Modifier.fillMaxWidth(),
                label = { Text("License headers (Widevine etc.)") }, minLines = 2, maxLines = 4,
                supportingText = { Text("One per line: Name: value") }
            )
            OutlinedButton(
                enabled = form.link.isNotBlank() && !testing, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val u = normalizeUrl(form.link)
                    if (u == null) { error = "Enter a valid video link"; return@OutlinedButton }
                    testing = true
                    scope.launch {
                        testResult = LinkTester.test(u, form.options())
                        testing = false
                    }
                }
            ) { Text(if (testing) "Testing..." else "Test link (check status, DRM, key)") }
            GradientButton(
                "Play", Icons.Rounded.PlayArrow, form.link.isNotBlank(),
                {
                    val u = normalizeUrl(form.link)
                    if (u == null) error = "Enter a valid video link" else onPlay(u, form.options())
                },
                Modifier.fillMaxWidth()
            )

            testResult?.let { r ->
                CompactDialog(
                    onDismissRequest = { testResult = null },
                    title = { Text("Link test") },
                    text = {
                        Text(
                            r, fontSize = 12.sp,
                            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                        )
                    },
                    confirmButton = { TextButton(onClick = { testResult = null }) { Text("Close") } }
                )
            }
            confirmSite?.let { cs ->
                CompactDialog(
                    onDismissRequest = { confirmSite = null },
                    title = { Text("Sign in securely?") },
                    text = {
                        Text(
                            "You sign in on ${cs.name}'s own page. Your password is never saved or seen by this app. " +
                                    "Only a login session stays privately on this phone, used just to play your videos. " +
                                    "You can sign out anytime."
                        )
                    },
                    confirmButton = { TextButton(onClick = { confirmSite = null; onLogin(cs) }) { Text("Continue") } },
                    dismissButton = { TextButton(onClick = { confirmSite = null }) { Text("Cancel") } }
                )
            }
            confirmOut?.let { co ->
                CompactDialog(
                    onDismissRequest = { confirmOut = null },
                    title = { Text("Sign out of ${co.name}?") },
                    text = { Text("Your saved login for ${co.name} will be removed from this app.") },
                    confirmButton = { TextButton(onClick = { CookieVault.remove(co.domain); confirmOut = null }) { Text("Sign out") } },
                    dismissButton = { TextButton(onClick = { confirmOut = null }) { Text("Cancel") } }
                )
            }
            SectionTitle("Site login (age-restricted / private videos)")
            val signed by CookieVault.domains.collectAsState()
            LOGIN_SITES.forEach { site ->
                val isIn = site.domain in signed
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(site.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (isIn) "Signed in" else "Not signed in",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isIn) TextButton(onClick = { confirmOut = site }) { Text("Sign out") }
                    OutlinedButton(onClick = { confirmSite = site }) { Text(if (isIn) "Re-login" else "Sign in") }
                }
            }

            SectionTitle("Playback")
            SettingSwitch("Play audio in background", "Keeps sound going when the screen is off, with notification controls", bgAudio) {
                bgAudio = it; PlayerSettings.backgroundAudio = it
            }
            SettingSwitch("Auto reconnect", "Retry automatically when the network drops", reconnect) {
                reconnect = it; PlayerSettings.autoReconnect = it
            }
            Dropdown("Live buffer (applies after restart)", listOf("Fast start", "Balanced", "Stable (big buffer)"), bufLevel) {
                bufLevel = it; PlayerSettings.bufferLevel = it
            }

            SectionTitle("Video engine")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "If a site stops working, update the engine that reads video links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = !updating, onClick = {
                    updating = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                com.example.universalplayer.AppInit.ready.await()
                                com.example.universalplayer.AppInit.update(ctx.applicationContext as Application, true)
                            }.getOrDefault(false)
                        }
                        updating = false
                        Toast.makeText(ctx, if (ok) "Video engine updated" else "Could not update. Check internet and try again", Toast.LENGTH_LONG).show()
                    }
                }) { Text(if (updating) "Updating..." else "Update") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Dropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = options[selected], onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.width(170.dp).heightIn(max = 260.dp)
        ) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(
                    text = { Text(o, fontSize = 14.sp, maxLines = 1) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = { onSelect(i); expanded = false }
                )
            }
        }
    }
}
