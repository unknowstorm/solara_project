package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.RepeatMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(audio: AudioBackend, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val state by audio.state.collectAsState()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var liked by remember(state.mediaId) { mutableStateOf(false) } // пока только визуально
    var showDetails by remember { mutableStateOf(false) }

    // Нажали ✕ в мини-плеере или очередь закончилась: закрываем экран
    LaunchedEffect(state.title) { if (state.title == null) onClose() }

    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownProgress = dragValue ?: progress
    val shownPositionMs = dragValue?.let { (it * state.durationMs).toLong() } ?: state.positionMs
    val showPause = state.isPlaying || state.isBuffering
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Не пускаем касания к списку под плеером
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        AsyncImage(
            model = state.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp).alpha(0.5f),
        )
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { ChevronDownIcon() }
                Text(
                    "Сейчас играет",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(48.dp))
            }

            // Обложка по макету: квадрат, в углу значок сервиса
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(1f)) {
                    CoverImage(state.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(28.dp))
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        // Временный значок: потом заменим логотипом сервиса
                        Box(contentAlignment = Alignment.Center) {
                            ServiceLogo(state.provider?.toService(), Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                state.title ?: "",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                state.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showDetails = true }) {
                    MoreIcon()
                }
                Row {
                    IconButton(onClick = { liked = !liked }) {
                        HeartIcon(liked, tint = if (liked) active else inactive)
                    }
                    IconButton(onClick = { audio.toggleShuffle() }) {
                        ShuffleIcon(tint = if (state.shuffle) active else inactive)
                    }
                    IconButton(onClick = { audio.cycleRepeat() }) {
                        RepeatIcon(
                            one = state.repeatMode == RepeatMode.ONE,
                            tint = if (state.repeatMode == RepeatMode.OFF) inactive else active,
                        )
                    }
                }
            }

            // Волнистый таймлайн; поверх невидимый Slider принимает перемотку
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                LinearWavyProgressIndicator(
                    progress = { shownProgress },
                    amplitude = { if (state.isPlaying) 1f else 0f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Slider(
                    value = shownProgress,
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { audio.seekTo((it * state.durationMs).toLong()) }
                        dragValue = null
                    },
                    enabled = state.durationMs > 0,
                    modifier = Modifier.fillMaxWidth().alpha(0f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(shownPositionMs), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { audio.previous() }, enabled = state.hasPrevious, modifier = Modifier.size(56.dp)) {
                    SkipIcon(next = false, modifier = Modifier.size(32.dp))
                }
                FilledIconButton(
                    onClick = { if (showPause) audio.pause() else audio.resume() },
                    modifier = Modifier.size(80.dp),
                ) {
                    if (rememberDelayedFlag(state.isBuffering && !state.isPlaying, SPINNER_DELAY_MS, state.mediaId)) {
                        CircularProgressIndicator(
                            Modifier.size(32.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        PlayPauseIcon(showPause, Modifier.size(40.dp))
                    }
                }
                IconButton(onClick = { audio.next() }, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                    SkipIcon(next = true, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Закрыть") } },
            title = { Text(state.title ?: "") },
            text = { Text("Исполнитель: ${state.artist ?: "—"}\nИсточник: ${state.provider?.toService()?.title ?: "—"}") },
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
