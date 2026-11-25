package com.mytune.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

import coil.imageLoader
import com.mytune.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(c: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stream by c.settings.streamQuality.collectAsState(initial = 320)
    val download by c.settings.downloadQuality.collectAsState(initial = 320)
    val theme by c.settings.theme.collectAsState(initial = "dark")
    val qualities = listOf(96, 160, 320)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        SectionTitle(R.string.streaming_quality)
        ChipRow {
            qualities.forEach { q ->
                FilterChip(selected = stream == q, onClick = { scope.launch { c.settings.setStreamQuality(q) } }, label = { Text(stringResource(R.string.kbps_fmt, q)) })
            }
        }

        SectionTitle(R.string.download_quality)
        ChipRow {
            qualities.forEach { q ->
                FilterChip(selected = download == q, onClick = { scope.launch { c.settings.setDownloadQuality(q) } }, label = { Text(stringResource(R.string.kbps_fmt, q)) })
            }
        }

        SectionTitle(R.string.download_location)
        Text(
            stringResource(R.string.download_location_info, c.musicDir().absolutePath),
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(R.string.theme)
        ChipRow {
            listOf("dark" to R.string.theme_dark, "light" to R.string.theme_light, "system" to R.string.theme_system).forEach { (key, label) ->
                FilterChip(selected = theme == key, onClick = { scope.launch { c.settings.setTheme(key) } }, label = { Text(stringResource(label)) })
            }
        }

        Spacer(Modifier.height(16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.clear_cache)) },
            leadingContent = { Icon(Icons.Default.CleaningServices, null) },
            modifier = Modifier.clickable {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ctx.imageLoader.diskCache?.clear()
                        ctx.imageLoader.memoryCache?.clear()
                    }
                    Toast.makeText(ctx, ctx.getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                }
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.clear_history)) },
            leadingContent = { Icon(Icons.Default.History, null) },
            modifier = Modifier.clickable {
                scope.launch {
                    c.library.clearHistory()
                    Toast.makeText(ctx, ctx.getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                }
            },
        )

        SectionTitle(R.string.about)
        Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME), modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            stringResource(R.string.about_text),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
