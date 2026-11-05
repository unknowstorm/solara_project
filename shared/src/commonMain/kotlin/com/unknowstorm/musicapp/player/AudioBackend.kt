package com.unknowstorm.musicapp.player

import androidx.compose.runtime.Composable
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode { OFF, ONE, ALL }

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
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val provider: ProviderId? = null,
    val currentIndex: Int = -1,
)

/** Строка очереди воспроизведения. */
data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val provider: ProviderId?,
)

private val EmptyQueue: StateFlow<List<QueueItem>> = MutableStateFlow(emptyList<QueueItem>()).asStateFlow()

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
    /** Останавливает воспроизведение и очищает очередь (кнопка ✕). */
    fun stop()
    /** Без повтора, затем весь список, затем один трек. */
    fun cycleRepeat()
    fun toggleShuffle()
    fun release()

    /** Очередь. Платформы без поддержки очереди могут не переопределять. */
    val queue: StateFlow<List<QueueItem>> get() = EmptyQueue
    fun playAt(index: Int) {}
    fun removeAt(index: Int) {}
}

@Composable
expect fun rememberAudioBackend(): AudioBackend
