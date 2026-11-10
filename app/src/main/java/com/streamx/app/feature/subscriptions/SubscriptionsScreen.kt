package com.streamx.app.feature.subscriptions

import androidx.compose.runtime.Composable
import com.streamx.app.feature.components.EmptyState

/** Phase 5 replaces this with the local subscriptions list. */
@Composable
fun SubscriptionsScreen() {
    EmptyState(
        title = "No subscriptions yet",
        message = "Channels you subscribe to in StreamX will appear here.",
    )
}
