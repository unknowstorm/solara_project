package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Returns true only if [value] stays true longer than [delayMs].
 * When [resetKey] changes (e.g. the track id) the countdown starts over.
 */
@Composable
fun rememberDelayedFlag(value: Boolean, delayMs: Long = 900, resetKey: Any? = null): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(value, resetKey) {
        shown = false
        if (value) {
            delay(delayMs)
            shown = true
        }
    }
    return shown
}

/** After how many milliseconds of buffering to show the spinner. Change it here. */
const val SPINNER_DELAY_MS = 3000L
