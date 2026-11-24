package com.vibemusic.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibemusic.app.BuildConfig
import com.vibemusic.app.domain.model.RepeatModeOption
import com.vibemusic.app.domain.model.SortOption
import com.vibemusic.app.domain.model.ThemeModeOption
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.theme.AccentColorOptions

@Composable
fun SettingsScreen(
    viewModel: VibeViewModel,
    onOpenEqualizer: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsState()
    val excludedItems by viewModel.excludedItems.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val context = LocalContext.current

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExcludeFolderDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Backup saved successfully!" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Playlists & favorites restored!" else "Restore failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        // 1. APPEARANCE
        SettingsSectionCard("Appearance") {
            Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModeOption.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Accent Color", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColorOptions.forEach { (name, hex) ->
                    val isSelected = prefs.accentColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setAccentColor(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = name,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SettingsSwitchRow(
                title = "Album Art Rotation Animation",
                subtitle = "Rotate disc artwork smoothly during playback",
                checked = prefs.albumArtAnimation,
                onCheckedChange = { viewModel.setAlbumArtAnimation(it) }
            )
            SettingsSwitchRow(
                title = "Keep Screen On",
                subtitle = "Prevent display sleep on Now Playing screen",
                checked = prefs.keepScreenOn,
                onCheckedChange = { viewModel.setKeepScreenOn(it) }
            )
        }

        // 2. PLAYBACK
        SettingsSectionCard("Playback") {
            SettingsSwitchRow(
                title = "Gapless Playback",
                subtitle = "Seamless transition between consecutive tracks",
                checked = prefs.gaplessPlayback,
                onCheckedChange = { viewModel.setGaplessPlayback(it) }
            )
            SettingsSwitchRow(
                title = "Resume Last Playback",
                subtitle = "Remember last played track and position on launch",
                checked = prefs.resumePlayback,
                onCheckedChange = { viewModel.setResumePlayback(it) }
            )
            SettingsSwitchRow(
                title = "Auto Play on Launch",
                subtitle = "Automatically resume playback when opening VIBE MUSIC",
                checked = prefs.autoPlay,
                onCheckedChange = { viewModel.setAutoPlay(it) }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text("Default Repeat Mode", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatModeOption.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.defaultRepeatMode == mode,
                        onClick = { viewModel.setDefaultRepeatMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        // 3. AUDIO & EQUALIZER
        SettingsSectionCard("Audio") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenEqualizer)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hardware Equalizer & Audio Effects", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "5/10-Band EQ, Presets, Bass Boost & Virtualizer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. LIBRARY & EXCLUSIONS
        SettingsSectionCard("Library") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.scanLibrary()
                        Toast.makeText(context, "Rescanning local media...", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Rescan Local Music", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Supports MP3, FLAC, WAV, M4A, AAC, OGG, OPUS, WMA",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Default Sort Order", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SortOption.entries.take(3).forEach { opt ->
                    FilterChip(
                        selected = prefs.sortOption == opt,
                        onClick = { viewModel.setSortOption(opt) },
                        label = { Text(opt.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExcludeFolderDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Excluded Folders & Songs (${excludedItems.size})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hide specific directories or tracks from your library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            excludedItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeExclusion(item.pathOrId) }) {
                        Icon(Icons.Default.Close, contentDescription = "Restore")
                    }
                }
            }
        }

        // 5. BACKUP & RESTORE
        SettingsSectionCard("Backup & Restore") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportBackupLauncher.launch("vibemusic_backup.json") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Export Playlists & Favorites Backup", style = MaterialTheme.typography.titleMedium)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { importBackupLauncher.launch(arrayOf("application/json")) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Restore Playlists & Favorites Backup", style = MaterialTheme.typography.titleMedium)
            }
        }

        // 6. OTHER
        SettingsSectionCard("Other") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPrivacyDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PrivacyTip, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Privacy Policy", style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("About VIBE MUSIC", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (Offline Hi-Fi Audio)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(140.dp))
    }

    if (showExcludeFolderDialog) {
        AlertDialog(
            onDismissRequest = { showExcludeFolderDialog = false },
            title = { Text("Exclude a Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (folders.isEmpty()) {
                        Text("No folders available to exclude.")
                    } else {
                        folders.forEach { folder ->
                            TextButton(
                                onClick = {
                                    viewModel.excludeFolder(folder.path, folder.name)
                                    showExcludeFolderDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${folder.name} (${folder.songCount} songs)")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExcludeFolderDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Text(
                    "VIBE MUSIC operates 100% offline on your Android device. " +
                        "We never collect personal data, never require an account, never upload audio files, " +
                        "and only scan media folders authorized by your Android system permissions."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("OK") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("VIBE MUSIC") },
            text = {
                Text(
                    "Version ${BuildConfig.VERSION_NAME}
" +
                        "Built with Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, and Room.
" +
                        "Good Music, Good Mood."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
