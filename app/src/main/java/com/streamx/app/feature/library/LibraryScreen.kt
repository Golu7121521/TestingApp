package com.streamx.app.feature.library

import androidx.compose.runtime.Composable
import com.streamx.app.feature.components.EmptyState

/** Phase 5 replaces this with history, watch later and playlists backed by Room. */
@Composable
fun LibraryScreen() {
    EmptyState(
        title = "Your library",
        message = "History, Watch Later and playlists will show up here.",
    )
}
