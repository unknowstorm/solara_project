package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Возвращает true, только если [value] остаётся true дольше [delayMs].
 * При смене [resetKey] (например, id трека) отсчёт начинается заново.
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

/** Через сколько миллисекунд буферизации показывать спиннер. Меняйте здесь. */
const val SPINNER_DELAY_MS = 3000L
