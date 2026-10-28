package com.unknowstorm.musicapp.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Заглушка для ПК: состояние меняется, но звука пока нет. */
class StubAudioBackend : AudioBackend {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override fun play(track: Track, streamUrl: String) {
        _state.value = PlaybackState(
            mediaId = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            isPlaying = true,
            durationMs = track.durationMs,
        )
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun resume() {
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun release() {}
}

@Composable
actual fun rememberAudioBackend(): AudioBackend = remember { StubAudioBackend() }