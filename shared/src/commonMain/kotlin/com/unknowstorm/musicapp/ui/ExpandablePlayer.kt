package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.likes.asTrack
import com.unknowstorm.musicapp.likes.containsTrack
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.RepeatMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Сколько нужно удерживать обложку, чтобы её можно было вращать. */
private const val HOLD_TO_TILT_MS = 1500L

/** Максимальный угол наклона обложки (градусы). */
private const val MAX_TILT_DEG = 12f

internal val MiniHeight = 64.dp
private val MiniSideMargin = 8.dp
private val MiniInset = 8.dp
private val MiniCoverSize = 48.dp
private val MiniCorner = 16.dp
private val MiniCoverCorner = 8.dp
private val FullCoverCorner = 28.dp

/** Примерная высота блока «название … кнопки», который идёт под обложкой. */
private val BelowCoverBlock = 340.dp
private val ScreenSideMargin = 24.dp
private val CoverDownShift = 40.dp
/**
 * Сюда старый [NowPlayingBar] (теперь невидимая «метка») сообщает, где в MainScreen
 * должен стоять мини-плеер. ExpandablePlayer рисует мини-плеер ровно в этом месте.
 */
@Stable
class MiniPlayerAnchor {
    var bounds by mutableStateOf<Rect?>(null)
}

val LocalMiniPlayerAnchor = staticCompositionLocalOf { MiniPlayerAnchor() }

/**
 * Один компонент вместо PlayerScreen + NowPlayingBar.
 * Мини-плеер и полный экран — это один и тот же контейнер, который плавно растёт
 * (progress 0f..1f), а обложка «перетекает» из маленькой в большую.
 *
 * Ставить в корневой Box поверх основного контента.
 */
@Composable
fun ExpandablePlayer(
    audio: AudioBackend,
    likes: LikesRepository,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNeedAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onExpandedChange)
    val anchor = LocalMiniPlayerAnchor.current

    // derivedStateOf: рекомпозиция только когда трек появился/пропал, а не на каждый тик позиции
    val playerState = audio.state.collectAsState()
    val hasTrack by remember { derivedStateOf { playerState.value.title != null } }

    val scope = rememberCoroutineScope()

    // Единственный источник правды для анимации: 0 = мини-плеер, 1 = полный экран.
    // Читаем его ТОЛЬКО внутри lambda (layout / graphicsLayer / draw), поэтому кадры анимации
    // не вызывают рекомпозицию.
    val progress = remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val settling = remember { floatArrayOf(-1f) }

    fun settle(target: Float, velocity: Float = 0f) {
        settling[0] = target
        animJob?.cancel()
        animJob = scope.launch {
            animate(
                initialValue = progress.floatValue,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { v, _ -> progress.floatValue = v.coerceIn(0f, 1f) }
            settling[0] = -1f
        }
    }

    // Родитель поменял expanded (тап по мини-плееру, кнопка «назад») — запускаем анимацию.
    // Если анимацию уже запустил жест (с учётом скорости), второй раз не стартуем.
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        if (settling[0] != target && progress.floatValue != target) settle(target)
    }

    // Трек закончился или нажали ✕: всё прячем
    LaunchedEffect(hasTrack) {
        if (!hasTrack) {
            animJob?.cancel()
            settling[0] = -1f
            progress.floatValue = 0f
            onChange(false)
        }
    }
    if (!hasTrack) return

    PlatformBackHandler(enabled = expanded, onBack = { onChange(false) })

    var origin by remember { mutableStateOf(Offset.Zero) }
    var miniOffsetX by remember { mutableFloatStateOf(0f) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    fun animateMiniX(to: Float, after: () -> Unit = {}) {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            animate(initialValue = miniOffsetX, targetValue = to) { v, _ -> miniOffsetX = v }
            after()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        val density = LocalDensity.current
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val fullWidthDp = maxWidth
        val fullHeightDp = maxHeight

        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // --- Где стоит мини-плеер (в координатах этого оверлея) ---
        val fbMargin = with(density) { MiniSideMargin.toPx() }
        val fbBottom = with(density) { (bottomInset + 8.dp).toPx() }
        val miniHpx = with(density) { MiniHeight.toPx() }
        fun miniRect(): Rect {
            val b = anchor.bounds
            return if (b != null) {
                Rect(b.left - origin.x, b.top - origin.y, b.right - origin.x, b.bottom - origin.y)
            } else {
                // Запасной вариант, пока MainScreen не сообщил положение
                Rect(fbMargin, fullH - fbBottom - miniHpx, fullW - fbMargin, fullH - fbBottom)
            }
        }
        val miniNow = miniRect()
        val miniWidthDp = with(density) { miniNow.width.toDp() }

        // --- Геометрия полного экрана (в dp) ---
        val layoutDirection = LocalLayoutDirection.current
        val sideInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val leftInset = with(density) { sideInsets.getLeft(density, layoutDirection).toDp() }
        val rightInset = with(density) { sideInsets.getRight(density, layoutDirection).toDp() }
        val landscape = fullW > fullH

        val coverTop: Dp
        val coverSize: Dp
        val coverLeft: Dp
        if (landscape) {
            // Альбомная ориентация: обложка слева по центру, справа название и кнопки управления
            val availH = maxHeight - topInset - bottomInset
            coverSize = maxOf(120.dp, minOf(availH - 24.dp, maxWidth * 0.42f))
            coverTop = topInset + (availH - coverSize) / 2f
            coverLeft = leftInset + 32.dp
        } else {
            coverTop = topInset + 32.dp + CoverDownShift
            coverSize = maxOf(160.dp, minOf(maxWidth - ScreenSideMargin * 2f, maxHeight - coverTop - bottomInset - BelowCoverBlock))
            coverLeft = (maxWidth - coverSize) / 2f
        }
        val coverTopPx = with(density) { coverTop.toPx() }
        val bigX = with(density) { coverLeft.toPx() }
        val belowCoverStart = coverTop + coverSize + 20.dp
        val panelStart = coverLeft + coverSize + 32.dp

        val miniCornerPx = with(density) { MiniCorner.toPx() }
        val shadowPx = with(density) { 3.dp.toPx() }

        val miniColor = MaterialTheme.colorScheme.secondaryContainer
        val fullColor = MaterialTheme.colorScheme.surface

        // Запоминаем в момент расстановки, стоит ли плеер на измеренном месте: до этого он невидим
        val placedOnAnchor = remember { mutableStateOf(false) }
        val miniVisible by remember { derivedStateOf { progress.floatValue < 0.999f } }
        val fullVisible by remember { derivedStateOf { progress.floatValue > 0.001f } }

        val dragState = rememberDraggableState { delta ->
            val travel = miniRect().top.coerceAtLeast(1f)
            progress.floatValue = (progress.floatValue - delta / travel).coerceIn(0f, 1f)
        }

        Box(
            modifier = Modifier
                // позиция: из мини-плеера в левый верхний угол экрана
                .offset {
                    val m = miniRect()
                    val p = progress.floatValue
                    IntOffset(lerp(m.left, 0f, p).roundToInt(), lerp(m.top, 0f, p).roundToInt())
                }
                // размер: из мини-плеера на весь экран
                .layout { measurable, _ ->
                    placedOnAnchor.value = anchor.bounds != null
                    val m = miniRect()
                    val p = progress.floatValue
                    val w = lerp(m.width, fullW, p).roundToInt().coerceAtLeast(0)
                    val h = lerp(m.height, fullH, p).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                // скругления и тень тоже «стекают» к полному экрану
                .graphicsLayer {
                    val p = progress.floatValue
                    translationX = miniOffsetX
                    // Пока место под мини-плеер не измерено, не рисуем его: иначе он мелькает поверх нижней панели
                    alpha = if (!placedOnAnchor.value && p < 0.01f) 0f else 1f
                    shape = RoundedCornerShape(CornerSize(lerp(miniCornerPx, 0f, p)))
                    clip = true
                    shadowElevation = lerp(shadowPx, 0f, p)
                }
                // цвет: secondaryContainer -> surface (только палитра Material You, без блюра)
                .drawBehind { drawRect(lerpColor(miniColor, fullColor, progress.floatValue)) }
                // Вертикальный жест и в свёрнутом, и в развёрнутом виде:
                // вниз сворачивает, вверх разворачивает вслед за пальцем
                .then(
                    Modifier.draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                animJob?.cancel()
                                settling[0] = -1f
                            },
                            onDragStopped = { velocity ->
                                val p = progress.floatValue
                                val travel = miniRect().top.coerceAtLeast(1f)
                                val target = when {
                                    velocity > 1500f -> 0f
                                    velocity < -1500f -> 1f
                                    p < 0.5f -> 0f
                                    else -> 1f
                                }
                                settle(target, -velocity / travel)
                                onChange(target == 1f)
                            },
                        )
                )
                .then(
                    if (expanded) {
                        Modifier
                    } else {
                        // Свёрнут: свайп в сторону закрывает, тап разворачивает
                        Modifier
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dismissJob?.cancel() },
                                    onDragEnd = {
                                        val m = miniRect()
                                        if (abs(miniOffsetX) > m.width * 0.35f) {
                                            animateMiniX(if (miniOffsetX > 0f) fullW else -fullW) {
                                                audio.stop()
                                                miniOffsetX = 0f
                                            }
                                        } else {
                                            animateMiniX(0f)
                                        }
                                    },
                                    onDragCancel = { animateMiniX(0f) },
                                    onHorizontalDrag = { _, dx -> miniOffsetX += dx },
                                )
                            }
                            .clickable { onChange(true) }
                    }
                )
        ) {
            if (miniVisible) {
                MiniContent(audio = audio, progress = { progress.floatValue }, width = miniWidthDp)
            }
            if (fullVisible) {
                FullContent(
                    audio = audio,
                    likes = likes,
                    onNeedAuth = onNeedAuth,
                    expandProgress = { progress.floatValue },
                    width = fullWidthDp,
                    height = fullHeightDp,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    belowCoverStart = belowCoverStart,
                    landscape = landscape,
                    panelStart = panelStart,
                    rightInset = rightInset,
                )
            }
            // Обложка — общий элемент: одна и та же картинка в мини и в полном плеере
            SharedCover(
                audio = audio,
                expanded = expanded,
                progress = { progress.floatValue },
                coverSize = coverSize,
                bigX = bigX,
                bigY = coverTopPx,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Мини-плеер (содержимое). Обложки здесь нет — вместо неё пустое место, обложку рисует SharedCover.
// ---------------------------------------------------------------------------------------------

@Composable
private fun MiniContent(audio: AudioBackend, progress: () -> Float, width: Dp) {
    val state by audio.state.collectAsState()
    val title = state.title ?: return
    val showPause = state.isPlaying || state.isBuffering

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = width, height = MiniHeight)
                // мини-контент быстро растворяется в начале разворачивания
                .graphicsLayer { alpha = (1f - progress() / 0.25f).coerceIn(0f, 1f) }
                .padding(MiniInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(MiniCoverSize))
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
                        color = MaterialTheme.colorScheme.onPrimary,
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

// ---------------------------------------------------------------------------------------------
// Общая обложка: морфинг 48dp -> большая + наклон после удержания 1.5 с
// ---------------------------------------------------------------------------------------------

@Composable
private fun SharedCover(
    audio: AudioBackend,
    expanded: Boolean,
    progress: () -> Float,
    coverSize: Dp,
    bigX: Float,
    bigY: Float,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val playerState = audio.state.collectAsState()
    val artworkUrl by remember { derivedStateOf { playerState.value.artworkUrl } }

    val sizePx = with(density) { coverSize.toPx() }
    val miniSizePx = with(density) { MiniCoverSize.toPx() }
    val miniXY = with(density) { MiniInset.toPx() }
    val miniCornerPx = with(density) { MiniCoverCorner.toPx() }
    val fullCornerPx = with(density) { FullCoverCorner.toPx() }

    // --- Наклон обложки ---
    var tilting by remember { mutableStateOf(false) }
    var targetX by remember { mutableFloatStateOf(0f) }
    var targetY by remember { mutableFloatStateOf(0f) }
    val followSpec = remember { spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium) }
    val releaseSpec = remember { spring<Float>(dampingRatio = 0.5f, stiffness = 220f) }
    val tiltX = animateFloatAsState(targetX, animationSpec = if (tilting) followSpec else releaseSpec, label = "tiltX")
    val tiltY = animateFloatAsState(targetY, animationSpec = if (tilting) followSpec else releaseSpec, label = "tiltY")
    val press = animateFloatAsState(
        if (tilting) 1f else 0f,
        animationSpec = if (tilting) followSpec else releaseSpec,
        label = "press",
    )

    LaunchedEffect(expanded) {
        if (!expanded) {
            tilting = false
            targetX = 0f
            targetY = 0f
        }
    }

    Box(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(coverSize)
            // морфинг: позиция + масштаб от левого верхнего угла
            .graphicsLayer {
                val p = progress()
                val s = lerp(miniSizePx / sizePx, 1f, p)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s
                scaleY = s
                translationX = lerp(miniXY, bigX, p)
                translationY = lerp(miniXY, bigY, p)
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (expanded) {
                        // Жест стоит до graphicsLayer, чтобы координаты не искажались поворотом.
                        // Не потребляем события, пока не прошло 1.5 с, поэтому свайп вниз работает как обычно.
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Ждём 1.5 с: если палец поднят или сдвинут раньше — это не наш жест
                                val interrupted = withTimeoutOrNull(HOLD_TO_TILT_MS) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed ||
                                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                        ) {
                                            return@withTimeoutOrNull
                                        }
                                    }
                                }
                                if (interrupted != null) return@awaitEachGesture

                                // Прошло 1.5 с: включаем наклон
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tilting = true

                                fun updateTarget(pos: Offset) {
                                    targetX = (pos.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
                                    targetY = (pos.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
                                }
                                updateTarget(down.position)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    updateTarget(change.position)
                                }

                                tilting = false
                                targetX = 0f
                                targetY = 0f
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    rotationY = tiltX.value * MAX_TILT_DEG
                    rotationX = -tiltY.value * MAX_TILT_DEG
                    cameraDistance = 14f * this.density
                    val k = 1f - 0.04f * press.value
                    scaleX = k
                    scaleY = k
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // скругление меняется вместе с морфингом (с поправкой на масштаб)
                    .graphicsLayer {
                        val p = progress()
                        val s = lerp(miniSizePx / sizePx, 1f, p)
                        val r = lerp(miniCornerPx, fullCornerPx, p) / s
                        shape = RoundedCornerShape(CornerSize(r))
                        clip = true
                    }
            ) {
                CoverImage(artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))

                // Блик уходит на сторону, противоположную пальцу
                Canvas(Modifier.fillMaxSize()) {
                    val pr = press.value
                    if (pr > 0f) {
                        val center = Offset(
                            size.width * (1f - tiltX.value) / 2f,
                            size.height * (1f - tiltY.value) / 2f,
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.32f * pr), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 0.75f,
                            )
                        )
                    }
                }

                // Значок источника текущего трека (Jamendo, Audius, Deezer), виден только в полном плеере
                ServiceBadge(
                    playerState.value.provider?.toService(),
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .graphicsLayer { alpha = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f) },
                    36.dp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Полный плеер (содержимое без обложки и без блюра)
// ---------------------------------------------------------------------------------------------

@Composable
private fun FullContent(
    audio: AudioBackend,
    likes: LikesRepository,
    onNeedAuth: () -> Unit,
    expandProgress: () -> Float,
    width: Dp,
    height: Dp,
    topInset: Dp,
    bottomInset: Dp,
    belowCoverStart: Dp,
    landscape: Boolean,
    panelStart: Dp,
    rightInset: Dp,
) {
    val state by audio.state.collectAsState()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val likedTracks by likes.tracks.collectAsState()
    val signedIn by likes.signedIn.collectAsState()
    val likeError by likes.error.collectAsState()
    val currentTrack = state.asTrack()
    val liked = likedTracks.containsTrack(currentTrack)
    var showDetails by remember { mutableStateOf(false) }

    val trackProgress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownProgress = dragValue ?: trackProgress
    val shownPositionMs = dragValue?.let { (it * state.durationMs).toLong() } ?: state.positionMs
    val showPause = state.isPlaying || state.isBuffering
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = width, height = height)
                // полный контент проявляется во второй половине разворачивания
                .graphicsLayer { alpha = ((expandProgress() - 0.3f) / 0.5f).coerceIn(0f, 1f) }
        ) {
            // Ручка для свайпа
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )

            Column(
                modifier = if (landscape) {
                    // Альбом: обложка слева, всё остальное в правой колонке по центру высоты
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = panelStart,
                            end = rightInset + 32.dp,
                            top = topInset + 16.dp,
                            bottom = bottomInset + 8.dp,
                        )
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = ScreenSideMargin)
                        .padding(bottom = bottomInset)
                },
                verticalArrangement = if (landscape) Arrangement.Center else Arrangement.Top,
            ) {
                // Здесь сверху лежит общая обложка (её рисует SharedCover)
                if (!landscape) Spacer(Modifier.height(belowCoverStart))

                Text(
                    state.title ?: "",
                    style = if (landscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    maxLines = if (landscape) 1 else 2,
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

                (state.error ?: likeError)?.let {
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
                        IconButton(enabled = currentTrack?.provider != com.unknowstorm.musicapp.model.ProviderId.DEEZER, onClick = {
                            when {
                                currentTrack == null -> Unit
                                !signedIn -> onNeedAuth()
                                else -> likes.toggle(currentTrack)
                            }
                        }) {
                            HeartIcon(
                                liked,
                                tint = if (currentTrack?.provider == com.unknowstorm.musicapp.model.ProviderId.DEEZER) inactive.copy(alpha = 0.3f) else if (liked) active else inactive,
                            )
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

                if (landscape) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(1f))

                Row(
                    if (landscape) {
                        Modifier.fillMaxWidth().padding(top = 8.dp)
                    } else {
                        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 80.dp)
                    },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { audio.previous() }, enabled = state.hasPrevious, modifier = Modifier.size(60.dp)) {
                        SkipIcon(next = false, modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(
                        onClick = { if (showPause) audio.pause() else audio.resume() },
                        modifier = Modifier.size(86.dp),
                    ) {
                        if (rememberDelayedFlag(state.isBuffering && !state.isPlaying, SPINNER_DELAY_MS, state.mediaId)) {
                            CircularProgressIndicator(
                                Modifier.size(40.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            PlayPauseIcon(showPause, Modifier.size(48.dp))
                        }
                    }
                    IconButton(onClick = { audio.next() }, enabled = state.hasNext, modifier = Modifier.size(60.dp)) {
                        SkipIcon(next = true, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(S.close) } },
            title = { Text(state.title ?: "") },
            text = { Text(S.artistSource(state.artist ?: "—", state.provider?.toService()?.title ?: "—")) },
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}