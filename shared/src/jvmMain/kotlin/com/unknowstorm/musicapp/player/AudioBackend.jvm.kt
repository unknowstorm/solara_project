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

    private var queue: List<Track> = emptyList()
    private var index = 0

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        index = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        publishCurrent(playing = true)
    }

    private fun publishCurrent(playing: Boolean) {
        val track = queue.getOrNull(index) ?: return
        _state.value = PlaybackState(
            mediaId = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            isPlaying = playing,
            durationMs = track.durationMs,
            hasNext = index < queue.lastIndex,
            hasPrevious = index > 0,
        )
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun resume() {
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun next() {
        if (index < queue.lastIndex) {
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

    override fun release() {}
}

@Composable
actual fun rememberAudioBackend(): AudioBackend = remember { StubAudioBackend() }
