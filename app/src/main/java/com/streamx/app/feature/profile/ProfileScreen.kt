package com.streamx.app.feature.profile

import androidx.compose.runtime.Composable
import com.streamx.app.feature.components.EmptyState

/** Settings entry point. Phase 6 adds the Settings screen (theme, playback, storage, privacy). */
@Composable
fun ProfileScreen() {
    EmptyState(
        title = "Profile",
        message = "Settings and preferences will be available here in a later build.",
    )
}
