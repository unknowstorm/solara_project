package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable

/** The result of recording a short clip from the microphone. */
sealed interface RecordResult {
    class Ok(val bytes: ByteArray, val mime: String) : RecordResult
    data object PermissionDenied : RecordResult
    data object Unsupported : RecordResult
    data object Failed : RecordResult
}

interface SongRecorder {
    /** Records [seconds] seconds from the microphone. [onStarted] is called when the recording has really begun. */
    suspend fun record(seconds: Int, onStarted: () -> Unit = {}): RecordResult
}

/** Microphone recorder (Android). On desktop it reports [RecordResult.Unsupported]. */
@Composable
expect fun rememberSongRecorder(): SongRecorder
