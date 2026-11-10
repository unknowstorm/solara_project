package com.unknowstorm.musicapp.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Заглушка для ПК: очередь и состояние меняются, но звука пока нет. */
class StubAudioBackend : AudioBackend {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var trackList: List<Track> = emptyList()
    private var index = 0
    private var repeat = RepeatMode.OFF
    private var shuffle = false

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        trackList = tracks
        index = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        publishCurrent(playing = true)
    }

    private fun publishCurrent(playing: Boolean) {
        val track = trackList.getOrNull(index) ?: return
        _state.value = PlaybackState(
            mediaId = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            isPlaying = playing,
            durationMs = track.durationMs,
            hasNext = index < trackList.lastIndex,
            hasPrevious = index > 0,
            repeatMode = repeat,
            shuffle = shuffle,
            provider = track.provider,
        )
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun resume() {
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun next() {
        if (index < trackList.lastIndex) {
            index++
            publishCurrent(playing = true)
        }
    }

    override fun previous() {
        if (index > 0) {
            index--
            publishCurrent(playing = true)
        }
    }

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun stop() {
        trackList = emptyList()
        index = 0
        _state.value = PlaybackState(repeatMode = repeat, shuffle = shuffle)
    }

    override fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = repeat)
    }

    override fun toggleShuffle() {
        shuffle = !shuffle
        _state.value = _state.value.copy(shuffle = shuffle)
    }

    override fun release() {}
}

@Composable
actual fun rememberAudioBackend(): AudioBackend = remember { StubAudioBackend() }
