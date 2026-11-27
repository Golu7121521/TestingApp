package com.mytune.app.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.mytune.app.R
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.*
import com.mytune.app.ui.UiState
import com.mytune.app.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch



import com.mytune.app.player.PlayerState
import android.content.Intent
import com.mytune.app.data.db.PlaylistInfo

fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "x",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.4f), base, base.copy(alpha = 0.4f)),
        start = Offset(x - 300f, 0f),
        end = Offset(x, 0f),
    )
}

/** Placeholder row shown while a home section loads. */
@Composable
fun ShimmerRow() {
    val brush = shimmerBrush()
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Column(Modifier.width(140.dp)) {
                Box(Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)).background(brush))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }
        }
    }
}

@Composable
fun Artwork(url: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    AsyncImage(
        model = url.ifBlank { null },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun ErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.error_network), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun EmptyText(res: Int) {
    Text(
        stringResource(res),
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
fun MediaCard(title: String, subtitle: String, artworkUrl: String, circle: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        Artwork(artworkUrl, Modifier.size(140.dp), if (circle) CircleShape else RoundedCornerShape(12.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** List row for albums / playlists / artists. */
@Composable
fun CollectionRow(item: MediaCollection, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.artworkUrl, Modifier.size(56.dp), if (item.type == CollectionType.ARTIST) CircleShape else RoundedCornerShape(8.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

class SongActions(
    val downloaded: Set<String>,
    val liked: Set<String>,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val toggleLike: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val equalizer: () -> Unit,
    val download: (Song) -> Unit,
    val goToArtist: (Song) -> Unit,
    val share: (Song) -> Unit,
    val deleteDownload: (Song) -> Unit,
)

/** Called to open an album / playlist / artist page; AppRoot also collapses the full player. */
val LocalOpenCollection = staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** Opens an artist page: (artistId, artistName). The name is used as a fallback to find songs. */
val LocalOpenArtist = staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** The shared three-dots menu: Play next, Add to queue, Liked Songs, Add to playlist, Equalizer, Download, Go to artist. */
@Composable
fun ColumnScope.SongMenuItems(song: Song, actions: SongActions, onDismiss: () -> Unit, onRemove: (() -> Unit)? = null) {
    val liked = song.id in actions.liked
    @Composable
    fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.Unspecified, onClick: () -> Unit) {
        DropdownMenuItem(
            text = { Text(label) },
            // Never pass an Unspecified tint: Icon would then draw the raw black vector (invisible in dark mode).
            leadingIcon = { if (tint == Color.Unspecified) Icon(icon, null) else Icon(icon, null, tint = tint) },
            onClick = { onDismiss(); onClick() },
        )
    }
    item("Play next", Icons.Default.QueuePlayNext) { actions.playNext(song) }
    item("Add to queue", Icons.Default.AddToQueue) { actions.addToQueue(song) }
    item(
        if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        if (liked) MaterialTheme.colorScheme.primary else Color.Unspecified,
    ) { actions.toggleLike(song) }
    item("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { actions.addToPlaylist(song) }
    item("Equalizer", Icons.Default.Equalizer) { actions.equalizer() }
    if (song.id in actions.downloaded) item("Delete download", Icons.Default.Delete) { actions.deleteDownload(song) }
    else item("Download", Icons.Default.Download) { actions.download(song) }
    item("Go to artist", Icons.Default.Person) { actions.goToArtist(song) }
    if (onRemove != null) item(stringResource(R.string.menu_remove), Icons.Default.Close) { onRemove() }
}

@Composable
fun SongRow(song: Song, actions: SongActions, onClick: () -> Unit, onRemove: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkUrl, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.id in actions.downloaded) {
                    Icon(Icons.Default.DownloadDone, stringResource(R.string.offline_available), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (song.durationSec > 0) {
            Text(formatTime(song.durationSec * 1000), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SongMenuItems(song, actions, onDismiss = { menu = false }, onRemove = onRemove)
            }
        }
    }
}

/** Adds song rows for a list; tapping a row plays the whole list from that index. */
fun LazyListScope.songItems(
    songs: List<Song>,
    actions: SongActions,
    c: AppContainer,
    onRemove: ((Song) -> Unit)? = null,
    showDelete: Boolean = false,
) {
    itemsIndexed(songs) { index, song ->
        SongRow(
            song = song,
            actions = actions,
            onClick = { c.player.play(songs, index) },
            onRemove = onRemove?.let { r -> { r(song) } },
            onDelete = if (showDelete) ({ actions.deleteDownload(song) }) else null,
        )
    }
}

/** Provides the shared three-dot actions plus the "add to playlist" dialog to its content. */
@Composable
fun SongActionsHost(c: AppContainer, content: @Composable (SongActions) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by c.downloads.items.collectAsState(initial = emptyList())
    val done = remember(items) { items.filter { it.download.status == "DONE" }.map { it.download.songId }.toSet() }
    var picker by remember { mutableStateOf<Song?>(null) }
    var showEq by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Song?>(null) }

    val favs by c.library.favorites.collectAsState(initial = emptyList())
    val liked = remember(favs) { favs.map { it.id }.toSet() }
    val openArtist = LocalOpenArtist.current

    val actions = SongActions(
        downloaded = done,
        liked = liked,
        playNext = { c.player.playNext(it); Toast.makeText(ctx, ctx.getString(R.string.will_play_next), Toast.LENGTH_SHORT).show() },
        addToQueue = { c.player.addToQueue(it); Toast.makeText(ctx, ctx.getString(R.string.added_to_queue), Toast.LENGTH_SHORT).show() },
        toggleLike = { song ->
            scope.launch { c.library.toggleFavorite(song) }
            Toast.makeText(ctx, if (song.id in liked) "Removed from Liked Songs" else "Added to Liked Songs", Toast.LENGTH_SHORT).show()
        },
        addToPlaylist = { picker = it },
        equalizer = { showEq = true },
        download = {
            scope.launch { c.downloads.enqueue(it, c.settings.downloadQualityNow) }
            Toast.makeText(ctx, ctx.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        },
        goToArtist = { song ->
            val name = song.artist.split(",").firstOrNull()?.trim().orEmpty()
            if (song.artistId.isNotBlank()) {
                openArtist(song.artistId, name)
            } else scope.launch {
                val hit = if (name.isBlank()) null else c.repository.searchArtists(name, 1).getOrNull()?.firstOrNull()
                if (hit != null) openArtist(hit.id, hit.title) else Toast.makeText(ctx, "Artist not found", Toast.LENGTH_SHORT).show()
            }
        },
        share = { song ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.share_text_fmt, song.title, song.artist))
            }
            ctx.startActivity(Intent.createChooser(send, null))
        },
        deleteDownload = { deleting = it },
    )
    content(actions)
    picker?.let { song -> PlaylistPickerDialog(c, song) { picker = null } }
    if (showEq) EqualizerDialog(c.equalizer) { showEq = false }
    deleting?.let { song ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete download?") },
            text = { Text("\"${song.title}\" will be removed from this device. You can download it again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.downloads.delete(song.id) }
                    Toast.makeText(ctx, "Download deleted", Toast.LENGTH_SHORT).show()
                    deleting = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun NameDialog(titleRes: Int, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PlaylistPickerDialog(c: AppContainer, song: Song, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by c.library.playlists.collectAsState(initial = emptyList<PlaylistInfo>())
    var creating by remember { mutableStateOf(false) }

    fun add(id: Long) {
        scope.launch { c.library.addToPlaylist(id, song) }
        Toast.makeText(ctx, ctx.getString(R.string.added_to_playlist), Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    if (creating) {
        NameDialog(R.string.new_playlist, "", onConfirm = { name ->
            scope.launch {
                val id = c.library.createPlaylist(name)
                c.library.addToPlaylist(id, song)
            }
            Toast.makeText(ctx, ctx.getString(R.string.added_to_playlist), Toast.LENGTH_SHORT).show()
            onDismiss()
        }, onDismiss = { creating = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            LazyColumn {
                item {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_playlist))
                    }
                }
                items(playlists.size) { i ->
                    val p = playlists[i]
                    Text(
                        p.name,
                        modifier = Modifier.fillMaxWidth().clickable { add(p.id) }.padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Height (bottom navigation + mini player) that scrolling screens should leave free at the end of their lists. */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** Section title taking a plain string (used by the dynamic home / search sections). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** Row + progress line of the mini player, without its own background (so the expanding panel can reuse it). */
@Composable
fun MiniPlayerContent(state: PlayerState, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.artworkUrl, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(state.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, stringResource(R.string.next)) }
        }
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
    }
}

@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clickable(onClick = onOpen),
    ) {
        MiniPlayerContent(state, onToggle, onNext)
    }
}
