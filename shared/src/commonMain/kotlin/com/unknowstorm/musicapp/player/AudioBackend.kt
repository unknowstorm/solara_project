package com.unknowstorm.musicapp.player

import androidx.compose.runtime.Composable
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/** Воспроизведение звука. На каждой платформе своя реализация. */
interface AudioBackend {
    val state: StateFlow<PlaybackState>

    /** Ставит список в очередь и начинает с трека под номером startIndex. */
    fun playQueue(tracks: List<Track>, startIndex: Int)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun release()
}

@Composable
expect fun rememberAudioBackend(): AudioBackend
