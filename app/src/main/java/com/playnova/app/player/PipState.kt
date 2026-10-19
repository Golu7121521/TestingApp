package com.playnova.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared flags between MainActivity (lifecycle) and the player screen. */
object PipState {
    var inPip by mutableStateOf(false)
    var playerVisible = false
}
