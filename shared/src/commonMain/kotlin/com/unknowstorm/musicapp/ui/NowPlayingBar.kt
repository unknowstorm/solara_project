package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend

/** Мини-плеер: обложка, название, пауза и закрытие. Тап открывает полный экран. */
@Composable
fun NowPlayingBar(audio: AudioBackend, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by audio.state.collectAsState()
    val title = state.title ?: return
    val showPause = state.isPlaying || state.isBuffering
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(state.artworkUrl, Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.artist?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = { audio.previous() },
                    enabled = state.hasPrevious,
                    modifier = Modifier.size(40.dp),
                ) {
                    SkipIcon(next = false, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = { if (showPause) audio.pause() else audio.resume() },
                    modifier = Modifier.size(40.dp),
                ) {
                    if (rememberDelayedBuffering(state.isBuffering)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        PlayPauseIcon(showPause)
                    }
                }
                IconButton(
                    onClick = { audio.next() },
                    enabled = state.hasNext,
                    modifier = Modifier.size(40.dp),
                ) {
                    SkipIcon(next = true, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { audio.stop() }, modifier = Modifier.size(40.dp)) {
                    CloseIcon()
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }
    }
}
