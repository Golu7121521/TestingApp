package com.playvid.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playvid.domain.model.PlaybackRequest
import com.playvid.presentation.AppViewModelProvider
import com.playvid.presentation.components.EmptyState
import com.playvid.presentation.components.LoadingIndicator
import com.playvid.presentation.components.RecentRow

/**
 * Home: app name, URL field, Play Video, helper text, then the Recent list.
 * Hierarchy: LazyColumn > [Header(title, field, button, helper), "Recent", rows | empty | loading]
 */
@Composable
fun HomeScreen(
    onPlay: (PlaybackRequest) -> Unit,
    vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val recentState by vm.recent.collectAsStateWithLifecycle()
    val recent = recentState
    val focus: FocusManager = LocalFocusManager.current

    val submit = {
        focus.clearFocus()
        vm.play(onPlay)
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        item {
            Text("PlayVid", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = ui.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste video URL") },
                singleLine = true,
                isError = ui.error != null,
                supportingText = { ui.error?.let { Text(it) } },
                trailingIcon = {
                    if (ui.input.isNotEmpty()) {
                        IconButton(onClick = vm::clearInput) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear URL")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() })
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { submit() },
                enabled = !ui.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (ui.isLoading) "Loading..." else "Play Video")
            }

            Text(
                "Supports M3U8, MP4 and direct video links",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(32.dp))
            Text("Recent", style = MaterialTheme.typography.titleMedium)
        }

        when {
            recent == null -> item { LoadingIndicator() }
            recent.isEmpty() -> item {
                EmptyState("No videos yet", "Paste a video URL to start watching.")
            }
            else -> items(recent, key = { it.id }) { item ->
                RecentRow(item = item, onClick = { onPlay(item.toRequest()) }, onRemove = { vm.removeFromHistory(item.id) })
            }
        }
    }
}
