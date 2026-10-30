package com.example.youtubeclone.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.youtubeclone.data.NewPipeRepository
import com.example.youtubeclone.data.RealVideoDetails
import com.example.youtubeclone.data.RealVideoItem
import com.example.youtubeclone.ui.components.CategoryFilterBar
import com.example.youtubeclone.ui.components.RealVideoFeedCard
import com.example.youtubeclone.ui.theme.YouTubeRed
import kotlinx.coroutines.launch

val defaultCategories = listOf(
    "Trending", "Android Development", "Gaming", "Music", "Kotlin", "Bollywood", "Coding", "Live", "Comedy"
)

@Composable
fun RealHomeScreen(
    searchQuery: String,
    onSearchCategoryChange: (String) -> Unit,
    onVideoSelect: (RealVideoItem) -> Unit
) {
    var videos by remember { mutableStateOf<List<RealVideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadFeed(query: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            val result = NewPipeRepository.fetchTrendingOrQuery(query)
            result.onSuccess {
                videos = it
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Failed to load YouTube stream data"
                isLoading = false
            }
        }
    }

    LaunchedEffect(searchQuery) {
        loadFeed(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryFilterBar(
            categories = defaultCategories,
            selectedCategory = searchQuery,
            onCategorySelected = { onSearchCategoryChange(it) }
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YouTubeRed)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fetching real YouTube streams via NewPipe...", fontSize = 13.sp)
                    }
                }
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMessage!!, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { loadFeed(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(videos) { video ->
                        RealVideoFeedCard(
                            video = video,
                            onClick = { onVideoSelect(video) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RealShortsScreen(onVideoSelect: (RealVideoItem) -> Unit) {
    RealHomeScreen(
        searchQuery = "#shorts",
        onSearchCategoryChange = {},
        onVideoSelect = onVideoSelect
    )
}

@Composable
fun RealVideoPlayerScreen(
    videoItem: RealVideoItem,
    onClose: () -> Unit,
    onSelectVideo: (RealVideoItem) -> Unit
) {
    var details by remember(videoItem.url) { mutableStateOf<RealVideoDetails?>(null) }
    var isLoading by remember(videoItem.url) { mutableStateOf(true) }
    var errorText by remember(videoItem.url) { mutableStateOf<String?>(null) }

    LaunchedEffect(videoItem.url) {
        isLoading = true
        errorText = null
        val res = NewPipeRepository.fetchStreamDetails(videoItem.url)
        res.onSuccess {
            details = it
            isLoading = false
        }.onFailure {
            errorText = it.localizedMessage ?: "Unable to extract video stream"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top 16:9 Real ExoPlayer Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(color = YouTubeRed)
                }
                errorText != null -> {
                    Text(
                        text = errorText!!,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                details?.streamUrl != null -> {
                    ExoVideoPlayerView(streamUrl = details!!.streamUrl!!)
                }
                else -> {
                    Text("No playable stream found", color = Color.White)
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
            }
        }

        // Metadata and Related Videos
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = details?.title ?: videoItem.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${details?.viewCountText ?: videoItem.viewsText} • ${details?.uploadDate ?: videoItem.uploadedText}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(YouTubeRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (details?.uploaderName ?: videoItem.uploaderName).take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = details?.uploaderName ?: videoItem.uploaderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = details?.subscriberText ?: "YouTube Channel",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(details?.likeCountText ?: "Like", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            }

            if (details != null && details!!.relatedStreams.isNotEmpty()) {
                item {
                    Text(
                        text = "Up Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                items(details!!.relatedStreams) { rel ->
                    RealVideoFeedCard(
                        video = rel,
                        onClick = { onSelectVideo(rel) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExoVideoPlayerView(streamUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(streamUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        update = { view ->
            view.player = exoPlayer
        },
        modifier = Modifier.fillMaxSize()
    )
}
