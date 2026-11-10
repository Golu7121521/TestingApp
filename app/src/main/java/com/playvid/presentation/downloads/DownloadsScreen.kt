package com.playvid.presentation.downloads

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playvid.download.CompletedItem
import com.playvid.download.DownloadJob
import com.playvid.download.DownloadKind
import com.playvid.download.DownloadStatus
import com.playvid.domain.model.PlaybackRequest
import com.playvid.presentation.AppViewModelProvider
import com.playvid.presentation.components.EmptyState
import com.playvid.presentation.components.LoadingIndicator
import com.playvid.presentation.components.VideoRow

/** Download tab: active jobs (progress, pause/resume, cancel) and previously finished files. */
@Composable
fun DownloadsScreen(
    onPlay: (PlaybackRequest) -> Unit,
    vm: DownloadsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    var cancelTarget by remember { mutableStateOf<DownloadJob?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        item {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
        }

        if (jobs.isNotEmpty()) {
            item {
                Text(
                    "In progress",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(jobs, key = { "job_${it.id}" }) { job ->
                ActiveDownloadRow(
                    job = job,
                    onPause = { vm.pause(job.id) },
                    onResume = { vm.resume(job.id) },
                    onCancel = { cancelTarget = job }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        item {
            Text(
                "Downloaded",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            completed == null -> item { LoadingIndicator() }
            completed!!.isEmpty() && jobs.isEmpty() -> item {
                EmptyState("No downloads yet", "Downloaded videos and audio will show up here.")
            }
            completed!!.isEmpty() -> item { Spacer(Modifier.height(8.dp)) }
            else -> items(completed!!, key = { it.uri }) { c ->
                CompletedRow(item = c, onClick = { onPlay(PlaybackRequest(source = c.uri, title = c.name, isLocal = true)) })
            }
        }
    }

    cancelTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel download?") },
            text = { Text("\u201c${job.title}\u201d will be canceled.") },
            confirmButton = {
                TextButton(onClick = { vm.cancel(job.id); cancelTarget = null }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) { Text("No") }
            }
        )
    }
}

@Composable
private fun ActiveDownloadRow(job: DownloadJob, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLabel(job), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (job.status == DownloadStatus.DOWNLOADING || job.status == DownloadStatus.PAUSED) {
                IconButton(onClick = { if (job.status == DownloadStatus.PAUSED) onResume() else onPause() }) {
                    Icon(
                        if (job.status == DownloadStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (job.status == DownloadStatus.PAUSED) "Resume" else "Pause"
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
        }
        if (job.status != DownloadStatus.FAILED) {
            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

private fun statusLabel(job: DownloadJob): String = when (job.status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "${if (job.kind == DownloadKind.AUDIO) "Audio" else "Video"} \u2022 ${job.progress}%"
    DownloadStatus.MERGING -> "Merging \u2022 ${job.progress}%"
    DownloadStatus.PAUSED -> "Paused \u2022 ${job.progress}%"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.CANCELED -> "Canceled"
    DownloadStatus.FAILED -> "Failed \u2014 ${job.error ?: "Unknown error"}"
}

@Composable
private fun CompletedRow(item: CompletedItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val details = Formatter.formatShortFileSize(context, item.sizeBytes) +
        " \u2022 " + (if (item.kind == DownloadKind.AUDIO) "Audio" else "Video")
    VideoRow(title = item.name, details = details, progress = 0f, onClick = onClick)
}
