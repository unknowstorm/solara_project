package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberSongRecorder(): SongRecorder = remember {
    object : SongRecorder {
        override suspend fun record(seconds: Int, onStarted: () -> Unit): RecordResult = RecordResult.Unsupported
    }
}
