@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.playnova.app.AppViewModel
import com.playnova.app.data.Video
import com.playnova.app.data.fmtSize
import com.playnova.app.data.fmtTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * Long-press menu for one video or a multi-selection, with every follow-up dialog.
 * "Remove from library" only hides the video inside PlayNova; "Delete file" really deletes it.
 */
@Composable
fun VideoActions(vm: AppViewModel, target: List<Video>?, onDismiss: () -> Unit, onPlay: ((Video) -> Unit)?) {
    if (target.isNullOrEmpty()) return
    val ctx = LocalContext.current
    val single = target.size == 1
    val v = target.first()
    var mode by remember(target) { mutableStateOf("menu") }
    val favs by vm.favoriteIds.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val isFav = single && v.id in favs

    val pending = remember { mutableStateOf<List<Video>>(emptyList()) }
    val runner = remember { arrayOfNulls<(List<Video>) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            if (pending.value.isNotEmpty()) runner[0]?.invoke(pending.value) else vm.refresh()
        }
        onDismiss()
    }
    runner[0] = { list ->
        // Android 10 and below: delete one by one; system may ask permission per file.
        val rest = list.toMutableList()
        var asked = false
        while (rest.isNotEmpty() && !asked) {
            val x = rest.first()
            try {
                ctx.contentResolver.delete(x.uri, null, null); rest.removeAt(0)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    pending.value = rest.toList(); asked = true
                    launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                } else { rest.removeAt(0) }
            } catch (e: Exception) { rest.removeAt(0) }
        }
        if (!asked) { pending.value = emptyList(); vm.refresh(); onDismiss() }
    }

    fun deleteNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val s = vm.deleteRequest(target)
            if (s != null) launcher.launch(IntentSenderRequest.Builder(s).build()) else onDismiss()
        } else runner[0]?.invoke(target.filter { !it.external })
    }

    when (mode) {
        "menu" -> ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(if (single) v.title else "${target.size} videos selected", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (single && onPlay != null) MenuRow(Icons.Outlined.PlayArrow, "Play") { onPlay(v); onDismiss() }
                if (single) MenuRow(if (isFav) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (isFav) "Favorites se hatao" else "Favorites mein jodo") { vm.toggleFavorite(v); onDismiss() }
                else MenuRow(Icons.Outlined.FavoriteBorder, "Sabko Favorites mein jodo") { vm.favoriteAll(target); onDismiss() }
                MenuRow(Icons.Outlined.PlaylistAdd, "Playlist mein jodo") { mode = "playlist" }
                if (single) MenuRow(Icons.Outlined.Share, "Share") {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "video/*"; putExtra(Intent.EXTRA_STREAM, v.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(i, "Share video")); onDismiss()
                }
                if (single) MenuRow(Icons.Outlined.Info, "Details") { mode = "details" }
                MenuRow(Icons.Outlined.VisibilityOff, "Library se hatao (file safe rehti hai)") { mode = "remove" }
                if (target.any { !it.external }) MenuRow(Icons.Outlined.Delete, "File delete karo", danger = true) { mode = "delete" }
            }
        }
        "playlist" -> PlaylistChooser(vm, playlists.map { it.id to it.name }, target, onDismiss)
        "details" -> AlertDialog(
            onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Naam" to v.title, "Folder" to v.folder, "Size" to fmtSize(v.size),
                        "Duration" to fmtTime(v.durationMs), "Resolution" to v.resolution, "Format" to v.mime,
                        "Add hui" to SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(v.dateAdded * 1000))
                    ).forEach { (k, x) ->
                        Column { Text(k, color = Muted, style = MaterialTheme.typography.labelSmall); Text(x) }
                    }
                }
            }
        )
        "remove" -> ConfirmDialog(
            "Library se hatao?", "Video sirf PlayNova ki list se chhupega. Phone se file delete NAHI hogi. Settings > Privacy mein wapas la sakte ho.",
            "Hatao", onConfirm = { vm.removeFromLibrary(target) }, onDismiss = onDismiss
        )
        "delete" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("File permanently delete karein?") },
            text = { Text(if (single) "\"${v.title}\" phone se hamesha ke liye delete ho jayegi. Ye wapas nahi aayegi." else "${target.size} files phone se hamesha ke liye delete ho jayengi.") },
            confirmButton = {
                TextButton(onClick = { mode = "deleting"; deleteNow() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        else -> {} // "deleting": keep this composable alive until the system dialog returns
    }
}

@Composable
private fun PlaylistChooser(vm: AppViewModel, lists: List<Pair<Long, String>>, target: List<Video>, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    if (creating) {
        NameDialog("Nayi playlist", "", "Banao", onOk = { vm.createPlaylist(it, target); Toast.makeText(ctx, "Playlist ban gayi", Toast.LENGTH_SHORT).show() }, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Playlist chuno") },
        text = {
            Column {
                ListItem(headlineContent = { Text("Nayi playlist banao") }, leadingContent = { Icon(Icons.Outlined.Add, null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { creating = true })
                lists.forEach { (id, name) ->
                    ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(Icons.Outlined.PlaylistPlay, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable {
                            vm.addToPlaylist(id, target)
                            Toast.makeText(ctx, "Playlist mein add ho gaya", Toast.LENGTH_SHORT).show(); onDismiss()
                        })
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
