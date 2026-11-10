package com.playvid.presentation.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tiny wrapper so any screen can show a short Snackbar message. */
@Stable
class Notifier(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String) {
        host.currentSnackbarData?.dismiss()
        scope.launch { host.showSnackbar(message, duration = SnackbarDuration.Short) }
    }
}

val LocalNotifier = staticCompositionLocalOf<Notifier> { error("Notifier not provided") }
