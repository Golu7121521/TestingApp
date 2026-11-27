package com.mytune.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Section title with an optional subtitle and a "play all" action. */
@Composable
fun FeedHeader(title: String, subtitle: String = "", onPlayAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onPlayAll != null) {
            FilledTonalIconButton(onClick = onPlayAll, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.PlayArrow, "Play all") }
        }
    }
}

/** Big "Just For You" card: artwork with a gradient and the title on top. */
@Composable
fun HeroSongCard(title: String, artist: String, artworkUrl: String, onClick: () -> Unit) {
    Box(Modifier.width(250.dp).height(250.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Artwork(artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(24.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6000000))))
        Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(artist, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** Trending card with a big rank number. */
@Composable
fun RankedSongCard(rank: Int, title: String, artist: String, artworkUrl: String, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Box {
            Artwork(artworkUrl, Modifier.size(150.dp), RoundedCornerShape(16.dp))
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 9.dp, vertical = 3.dp),
            ) { Text("#$rank", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Playlist / chart tile: artwork with the name over a gradient. */
@Composable
fun PlaylistTile(title: String, subtitle: String, artworkUrl: String, onClick: () -> Unit) {
    Box(Modifier.size(160.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Artwork(artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(18.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xEE000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
        }
    }
}

/** Round artist with an accent ring. */
@Composable
fun ArtistBubble(name: String, imageUrl: String, size: Int = 96, onClick: () -> Unit) {
    Column(Modifier.width((size + 8).dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp).border(BorderStroke(2.5.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))), CircleShape).padding(4.dp),
        ) { Artwork(imageUrl, Modifier.fillMaxSize(), CircleShape) }
        Spacer(Modifier.height(6.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Compact card for "Last Played". */
@Composable
fun CompactSongCard(title: String, artist: String, artworkUrl: String, onClick: () -> Unit) {
    Row(
        Modifier.width(230.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artworkUrl, Modifier.size(56.dp), RoundedCornerShape(14.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
