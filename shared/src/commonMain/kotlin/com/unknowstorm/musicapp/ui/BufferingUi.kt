package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** true only if buffering lasts longer than delayMs. Short loads do not flash the spinner. */
@Composable
fun rememberDelayedBuffering(isBuffering: Boolean, delayMs: Long = 1500): Boolean {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(delayMs)
            show = true
        } else {
            show = false
        }
    }
    return show
}
