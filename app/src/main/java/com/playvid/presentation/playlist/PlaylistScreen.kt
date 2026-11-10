package com.playvid.presentation.playlist

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playvid.domain.model.Playlist
import com.playvid.presentation.AppViewModelProvider
import com.playvid.presentation.components.EmptyState
import com.playvid.presentation.components.LocalNotifier

/** What the details dialog is currently for. */
private sealed interface PlaylistDialog {
    object AddUrl : PlaylistDialog
    data class AddFile(val uri: Uri, val suggestedName: String) : PlaylistDialog
    data class Edit(val playlist: Playlist) : PlaylistDialog
}

/**
 * Playlist tab: saved playlists as outlined cards (name, link, edit pencil). The top-right menu adds
 * one from a URL or a picked .m3u file; tapping a card opens its channels.
 */
@Composable
fun PlaylistScreen(
    onOpenPlaylist: (Long) -> Unit,
    vm: PlaylistsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }

    LaunchedEffect(message) {
        message?.let { notifier.show(it); vm.dismissMessage() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "Playlist"
            dialog = PlaylistDialog.AddFile(uri, fileName.substringBeforeLast('.'))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlist", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Add playlist")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Add URL") }, onClick = {
                            menuOpen = false
                            dialog = PlaylistDialog.AddUrl
                        })
                        DropdownMenuItem(text = { Text("Select File") }, onClick = {
                            menuOpen = false
                            picker.launch(arrayOf("*/*"))
                        })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (playlists.isEmpty()) {
            item { EmptyState("No playlists yet", "Use the menu at the top right to add a URL or select a file.") }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onEdit = { dialog = PlaylistDialog.Edit(playlist) }
                )
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        PlaylistDialog.AddUrl -> PlaylistDetailsDialog(
            title = "Enter Playlist details",
            initialName = "",
            initialUrl = "",
            urlEditable = true,
            confirmLabel = "Create",
            onDismiss = { dialog = null },
            onConfirm = { name, url -> vm.addUrl(name, url); dialog = null }
        )
        is PlaylistDialog.AddFile -> PlaylistDetailsDialog(
            title = "Enter Playlist details",
            initialName = d.suggestedName,
            initialUrl = "Local file",
            urlEditable = false,
            confirmLabel = "Create",
            onDismiss = { dialog = null },
            onConfirm = { name, _ -> vm.addFile(name, d.uri); dialog = null }
        )
        is PlaylistDialog.Edit -> PlaylistDetailsDialog(
            title = "Edit Playlist",
            initialName = d.playlist.name,
            initialUrl = if (d.playlist.isFile) "Local file" else d.playlist.source,
            urlEditable = !d.playlist.isFile,
            confirmLabel = "Save",
            onDismiss = { dialog = null },
            onConfirm = { name, url -> vm.update(d.playlist, name, url); dialog = null },
            onDelete = { vm.delete(d.playlist); dialog = null }
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onEdit: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (playlist.isFile) "Local file" else playlist.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit playlist")
            }
        }
    }
}

/** Name + URL form: used to create (URL or file) and to edit a playlist. */
@Composable
private fun PlaylistDetailsDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    urlEditable: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    val fieldShape = RoundedCornerShape(14.dp)

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter playlist name") },
                    singleLine = true,
                    shape = fieldShape
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter playlist url") },
                    singleLine = true,
                    enabled = urlEditable,
                    shape = fieldShape
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { onConfirm(name, url) },
                        enabled = if (urlEditable) url.isNotBlank() else true,
                        modifier = Modifier.weight(1f)
                    ) { Text(confirmLabel) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete playlist", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
