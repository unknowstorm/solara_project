package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend

@Composable
fun NowPlayingBar(audio: AudioBackend, modifier: Modifier = Modifier) {
    val state by audio.state.collectAsState()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val title = state.title ?: return
    val showPause = state.isPlaying || state.isBuffering
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownPositionMs = dragValue?.let { (it * state.durationMs).toLong() } ?: state.positionMs

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    state.artist?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
                TextButton(onClick = { audio.previous() }, enabled = state.hasPrevious) {
                    Text("⏮")
                }
                Button(onClick = { if (showPause) audio.pause() else audio.resume() }) {
                    Text(if (showPause) "Pause" else "Play")
                }
                TextButton(onClick = { audio.next() }, enabled = state.hasNext) {
                    Text("⏭")
                }
            }
            Slider(
                value = dragValue ?: progress,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { audio.seekTo((it * state.durationMs).toLong()) }
                    dragValue = null
                },
                enabled = state.durationMs > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(shownPositionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
