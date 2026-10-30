package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend
import kotlinx.coroutines.launch

/** Мини-плеер: обложка, название, пауза и закрытие. Тап открывает полный экран. Свайп скрывает. */
@Composable
fun NowPlayingBar(audio: AudioBackend, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by audio.state.collectAsState()
    val title = state.title ?: return
    val showPause = state.isPlaying || state.isBuffering

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    // Кастомный свайп влево/вправо для закрытия
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX.value > 300f || offsetX.value < -300f) {
                            scope.launch {
                                offsetX.animateTo(if (offsetX.value > 0) 2000f else -2000f)
                                audio.stop()
                                offsetX.snapTo(0f)
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f) }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            }
            .graphicsLayer { translationX = offsetX.value }
    ) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(state.artworkUrl, Modifier.size(48.dp), RoundedCornerShape(8.dp))
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
                    onClick = { if (showPause) audio.pause() else audio.resume() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    if (rememberDelayedFlag(state.isBuffering && !state.isPlaying, SPINNER_DELAY_MS, state.mediaId)) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        PlayPauseIcon(showPause, Modifier.size(22.dp), MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { audio.stop() }, modifier = Modifier.size(40.dp)) {
                    CloseIcon(Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}