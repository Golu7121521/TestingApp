package com.playvid.presentation.server

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playvid.PlayVidApp
import com.playvid.domain.model.PlaybackRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onPlay: (PlaybackRequest) -> Unit,
    viewModel: ServerViewModel = viewModel()
) {
    val links by viewModel.links.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val processingUrl by viewModel.processingUrl.collectAsState()

    val context = LocalContext.current
    val validateUrl = remember { (context.applicationContext as PlayVidApp).container.validateUrl }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Links") },
                actions = {
                    IconButton(onClick = { viewModel.fetchLinks() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Links")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (links.isEmpty()) {
                Text(
                    text = "No link available",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(links) { link ->
                        val isCurrentItemProcessing = processingUrl == link.url.trim()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable(enabled = processingUrl == null) {
                                    viewModel.playLink(
                                        link = link,
                                        validateUrl = validateUrl,
                                        onValid = onPlay,
                                        onError = { errorMsg ->
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = link.title,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (isCurrentItemProcessing && !link.url.contains(".m3u8")) {
                                        Text(
                                            text = "Extracting live stream...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (isCurrentItemProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
