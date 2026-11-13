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

/** A row of the playback queue. */
data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val provider: ProviderId?,
    val durationMs: Long = 0L,
)

private val EmptyQueue: StateFlow<List<QueueItem>> = MutableStateFlow(emptyList<QueueItem>()).asStateFlow()

/** Одна полоса эквалайзера: центральная частота в Гц и допустимый диапазон уровня в миллибелах (100 = 1 дБ). */
data class EqualizerBand(val centerHz: Int, val minLevel: Int, val maxLevel: Int)

/** Что умеет эквалайзер устройства: полосы и названия системных пресетов. */
data class EqualizerInfo(val bands: List<EqualizerBand>, val presets: List<String>)

/** Настройки эквалайзера. [levels] идут по полосам, в миллибелах; пустой список = все по нулям. */
data class EqualizerSettings(val enabled: Boolean = false, val levels: List<Int> = emptyList()) {
    fun encode(): String = "e=" + (if (enabled) "1" else "0") + ";l=" + levels.joinToString(",")

    companion object {
        fun decode(raw: String?): EqualizerSettings {
            if (raw.isNullOrBlank()) return EqualizerSettings()
            var enabled = false
            var levels = emptyList<Int>()
            raw.split(';').forEach { part ->
                val pieces = part.split('=', limit = 2)
                val value = pieces.getOrElse(1) { "" }
                when (pieces[0]) {
                    "e" -> enabled = value == "1"
                    "l" -> levels = value.split(',').mapNotNull { it.trim().toIntOrNull() }
                }
            }
            return EqualizerSettings(enabled, levels)
        }
    }
}

/** Управление эквалайзером. Есть только там, где платформа его поддерживает (сейчас Android). */
interface EqualizerController {
    /** null, пока эффект не создан или устройство его не поддерживает. */
    val info: StateFlow<EqualizerInfo?>
    /** Применяет настройки сразу; если эффект ещё не создан, запоминает и применит при создании. */
    fun apply(settings: EqualizerSettings)
    /** Уровни полос системного пресета с номером [index]. */
    fun presetLevels(index: Int): List<Int>
}

/** Sound playback. Each platform has its own implementation. */
interface AudioBackend {
    val state: StateFlow<PlaybackState>

    /** Puts the list into the queue and starts from the track at startIndex. */
    fun playQueue(tracks: List<Track>, startIndex: Int)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    /** Stops playback and clears the queue (the ✕ button). */
    fun stop()
    /** No repeat, then the whole list, then a single track. */
    fun cycleRepeat()
    fun toggleShuffle()
    fun release()

    /** The queue. Platforms without queue support may leave it unimplemented. */
    val queue: StateFlow<List<QueueItem>> get() = EmptyQueue
    fun playAt(index: Int) {}
    fun removeAt(index: Int) {}
    /** Moves a queue item (drag and drop). Platforms without queue support ignore it. */
    fun moveItem(from: Int, to: Int) {}

    /** Эквалайзер. null там, где он не реализован (Desktop). */
    val equalizer: EqualizerController? get() = null
}

@Composable
expect fun rememberAudioBackend(): AudioBackend
