package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(audio: AudioBackend, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val state by audio.state.collectAsState()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var liked by remember(state.mediaId) { mutableStateOf(false) } // пока только визуально
    var showDetails by remember { mutableStateOf(false) }

    // Анимации для свайпа (сдвиг, уменьшение, прозрачность)
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Наклон обложки: tiltX/tiltY от -1 до 1, press от 0 до 1
    // Активируется только после долгого нажатия (1.5 сек)
    var isTilting by remember { mutableStateOf(false) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    val followSpec = remember { spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium) }
    val releaseSpec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 220f) }

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
        Modifier
            .fillMaxSize()
            // Применяем и сдвиг вниз, и уменьшение размера, и прозрачность
            .graphicsLayer {
                translationY = offsetY.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .background(MaterialTheme.colorScheme.background)
            // Не пускаем касания к списку под плеером
            .pointerInput(Unit) { detectTapGestures { } }
            // Свайп вниз для закрытия
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value > 150f) {
                            scope.launch {
                                // Плавно уезжаем вниз, уменьшаемся и растворяемся
                                launch { offsetY.animateTo(1000f, tween(300)) }
                                launch { scale.animateTo(0.5f, tween(300)) }
                                launch { alpha.animateTo(0f, tween(300)) }
                                // Ждем завершения анимации и закрываем
                                delay(300)
                                onClose()
                                // Возвращаем значения (на случай повторного открытия)
                                offsetY.snapTo(0f)
                                scale.snapTo(1f)
                                alpha.snapTo(1f)
                            }
                        } else {
                            // Пружина возвращает всё на место
                            scope.launch { offsetY.animateTo(0f, spring()) }
                            scope.launch { scale.animateTo(1f, spring()) }
                            scope.launch { alpha.animateTo(1f, spring()) }
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || offsetY.value > 0) {
                            scope.launch {
                                offsetY.snapTo(offsetY.value + dragAmount)
                                // Уменьшаем и делаем прозрачнее в зависимости от сдвига
                                val newScale = (1f - (offsetY.value / 2000f)).coerceIn(0.5f, 1f)
                                val newAlpha = (1f - (offsetY.value / 1000f)).coerceIn(0f, 1f)
                                scale.snapTo(newScale)
                                alpha.snapTo(newAlpha)
                            }
                        }
                    }
                )
            }
    ) {
        // Размытый фон
        RetryAsyncImage(
            url = state.artworkUrl,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.15f
                    scaleY = 1.15f
                    this.alpha = 0.5f
                }
                .blur(48.dp),
        )
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Ручка для свайпа
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            }

            Spacer(Modifier.height(12.dp))

            // Обложка по макету: квадрат, в углу кружок "J"
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .aspectRatio(1f)
                        // pointerInput стоит до graphicsLayer, чтобы координаты не искажались поворотом
                        .pointerInput(Unit) {
                            // Долгое нажатие для активации наклона
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    isTilting = true
                                    scope.launch { press.animateTo(1f, followSpec) }
                                },
                                onDragEnd = {
                                    isTilting = false
                                    scope.launch { press.animateTo(0f, releaseSpec) }
                                    scope.launch { tiltX.animateTo(0f, releaseSpec) }
                                    scope.launch { tiltY.animateTo(0f, releaseSpec) }
                                },
                                onDragCancel = {
                                    isTilting = false
                                    scope.launch { press.animateTo(0f, releaseSpec) }
                                    scope.launch { tiltX.animateTo(0f, releaseSpec) }
                                    scope.launch { tiltY.animateTo(0f, releaseSpec) }
                                },
                                onDrag = { change, _ ->
                                    if (isTilting) {
                                        val nx = (change.position.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
                                        val ny = (change.position.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
                                        scope.launch { tiltX.animateTo(nx, followSpec) }
                                        scope.launch { tiltY.animateTo(ny, followSpec) }
                                    }
                                }
                            )
                        }
                        .graphicsLayer {
                            rotationY = tiltX.value * 10f
                            rotationX = -tiltY.value * 10f
                            cameraDistance = 14f * density
                            val k = 1f - 0.04f * press.value
                            scaleX = k
                            scaleY = k
                        }
                ) {
                    CoverImage(state.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(28.dp))
                    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))) {
                        // Блик уходит на сторону, противоположную пальцу
                        if (press.value > 0f) {
                            val center = Offset(
                                size.width * (1f - tiltX.value) / 2f,
                                size.height * (1f - tiltY.value) / 2f,
                            )
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.32f * press.value), Color.Transparent),
                                    center = center,
                                    radius = size.minDimension * 0.75f,
                                )
                            )
                        }
                    }
                    // Кружок с буквой "J" (заглушка аккаунта)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("J", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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