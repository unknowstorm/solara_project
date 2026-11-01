package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Минимум по правилам YouTube (Required Minimum Functionality): окно плеера не меньше 200x200
private val MINI_SIZE = 200.dp
private val STRIP_HEIGHT = 36.dp
private val EDGE = 8.dp
private val MINI_CORNER = 12.dp // подберите под скругление мини-плеера аудио
private val HEADER_ESTIMATE = 56.dp // высота шапки экрана YouTube, пока реальное место плеера неизвестно
private val FLICK_VELOCITY = 1200.dp // скорость рывка в dp/с

/**
 * Текущее видео YouTube на уровне всего приложения.
 *
 * progress: 0 = мини-окно, 1 = видео развёрнуто на экране YouTube. Им управляет палец (dragBy),
 * а после отпускания его довершает пружина (settle), как у ExpandablePlayer.
 * docked: куда движемся (true = развернуть). queue: список для кнопок «предыдущее/следующее».
 */
class YouTubeSession(private val scope: CoroutineScope) {
    var video by mutableStateOf<YouTubeVideo?>(null)
        private set
    var queue by mutableStateOf<List<YouTubeVideo>>(emptyList())
        private set
    var docked by mutableStateOf(false)
    /** Последнее известное место большого плеера в координатах окна (его сообщает экран YouTube). */
    var dockBounds by mutableStateOf<Rect?>(null)
    var errorCode by mutableStateOf<Int?>(null)
    /** Состояние плеера по IFrame API: 1 играет, 2 пауза, 3 буферизация, 0 конец, -1 нет данных. */
    var playerState by mutableStateOf(-1)

    var progress by mutableFloatStateOf(0f)
        private set

    /** Путь пальца, за который progress проходит от 0 до 1. Обновляет оверлей. */
    var travelPx = 800f
    var flickPx = 2500f

    private var dragging = false
    private var settleTarget = -1f
    private var animJob: Job? = null

    private fun currentIndex(): Int = queue.indexOfFirst { it.id == video?.id }

    val hasPrev: Boolean get() = currentIndex() > 0
    val hasNext: Boolean get() = currentIndex().let { it >= 0 && it < queue.size - 1 }

    /** Видео из списка всегда стартует в мини-окне. То же видео, уже играющее в окне, разворачивается. */
    fun open(v: YouTubeVideo, list: List<YouTubeVideo> = listOf(v)) {
        val same = video?.id == v.id
        if (!same) errorCode = null
        queue = if (list.any { it.id == v.id }) list else listOf(v)
        video = v
        docked = same
    }

    /** Только по тапу пользователя. Автоматического перехода в конце ролика нет (правила YouTube). */
    fun next() {
        val i = currentIndex()
        if (i >= 0 && i < queue.size - 1) {
            video = queue[i + 1]
            errorCode = null
        }
    }

    fun previous() {
        val i = currentIndex()
        if (i > 0) {
            video = queue[i - 1]
            errorCode = null
        }
    }

    fun minimize() {
        if (video != null) docked = false
    }

    fun close() {
        animJob?.cancel()
        settleTarget = -1f
        dragging = false
        video = null
        queue = emptyList()
        docked = false
        progress = 0f
        errorCode = null
    }

    private fun settle(target: Float, velocity: Float = 0f) {
        settleTarget = target
        animJob?.cancel()
        animJob = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { v, _ -> progress = v.coerceIn(0f, 1f) }
            settleTarget = -1f
        }
    }

    /** Смена docked без жеста (кнопки, «назад», уход с экрана): просто едем к цели. */
    fun syncTo(expanded: Boolean) {
        val target = if (expanded) 1f else 0f
        if (dragging || video == null) return
        if (settleTarget != target && progress != target) settle(target)
    }

    fun beginDrag() {
        dragging = true
        animJob?.cancel()
        settleTarget = -1f
    }

    /** dy > 0: палец вниз (сворачиваем), dy < 0: вверх (разворачиваем). */
    fun dragBy(dy: Float) {
        progress = (progress - dy / travelPx).coerceIn(0f, 1f)
    }

    /** Возвращает true, если решено развернуть. Дальше пружина довершает движение. */
    fun endDrag(velocityY: Float): Boolean {
        dragging = false
        if (video == null) return false
        val expand = when {
            velocityY < -flickPx -> true
            velocityY > flickPx -> false
            else -> progress > 0.5f
        }
        docked = expand
        settle(if (expand) 1f else 0f, -velocityY / travelPx)
        return expand
    }
}

/**
 * Единственный плеер YouTube в приложении. Он всегда рисуется здесь, в одном и том же месте
 * дерева, поэтому при сворачивании видео не перезапускается. Размер и положение считаются
 * по session.progress в layout-фазе, без рекомпозиции на каждый кадр.
 */
@Composable
fun YouTubeOverlay(
    session: YouTubeSession,
    containerOrigin: Offset,
    containerSize: IntSize,
    onExpand: () -> Unit,
) {
    val video = session.video ?: return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val openLink = rememberExternalLinkOpener()

    LaunchedEffect(session.docked) { session.syncTo(session.docked) }
    DisposableEffect(session) {
        YouTubeControl.onState = { session.playerState = it }
        onDispose { YouTubeControl.onState = null }
    }

    val miniPx = with(density) { MINI_SIZE.toPx() }
    val stripPx = with(density) { STRIP_HEIGHT.toPx() }
    val edgePx = with(density) { EDGE.toPx() }
    val cornerPx = with(density) { MINI_CORNER.toPx() }
    val shadowPx = with(density) { 8.dp.toPx() }
    val headerPx = with(density) { HEADER_ESTIMATE.toPx() }
    val reservePx = with(density) { 150.dp.toPx() } // место под нижнюю панель и музыкальный мини-плеер
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()

    // Куда растёт окно: реальное место плеера, а если его ещё не знаем, то оценка (на всю ширину, 16:9)
    val width = containerSize.width.toFloat()
    val dockRect: Rect = session.dockBounds?.translate(-containerOrigin)
        ?: Rect(0f, topInset + headerPx, width, topInset + headerPx + width * 9f / 16f)

    val minX = edgePx
    val minY = topInset + edgePx
    val maxX = (containerSize.width - miniPx - edgePx).coerceAtLeast(minX)
    val maxY = (containerSize.height - miniPx - stripPx - edgePx).coerceAtLeast(minY)
    fun clamp(o: Offset) = Offset(o.x.coerceIn(minX, maxX), o.y.coerceIn(minY, maxY))

    var floatPos by remember { mutableStateOf<Offset?>(null) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    val pos = clamp(floatPos ?: Offset(maxX, maxY - reservePx))

    SideEffect {
        session.travelPx = max(abs(dockRect.top - pos.y), containerSize.height * 0.4f)
        session.flickPx = with(density) { FLICK_VELOCITY.toPx() }
    }

    val stripVisible by remember { derivedStateOf { session.progress < 0.999f } }

    val outer: Modifier = Modifier
        .offset {
            val p = session.progress
            IntOffset(
                lerp(pos.x, dockRect.left, p).roundToInt(),
                lerp(pos.y, dockRect.top, p).roundToInt(),
            )
        }
        .layout { measurable, _ ->
            val p = session.progress
            val w = lerp(miniPx, dockRect.width, p).roundToInt().coerceAtLeast(0)
            val h = lerp(miniPx + stripPx, dockRect.height, p).roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val p = session.progress
            shadowElevation = lerp(shadowPx, 0f, p)
            val r = lerp(cornerPx, 0f, p)
            shape = RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = 0f, bottomStart = 0f)
            clip = true
        }

    // Жест на плашке: вверх тянет окно к большому видео (за пальцем), в стороны и вниз двигает окно
    val onBegin: (Boolean) -> Unit = { expand ->
        if (expand) {
            session.beginDrag()
        } else {
            snapJob?.cancel()
            if (floatPos == null) floatPos = pos
        }
    }
    val onMove: (Offset, Boolean) -> Unit = { delta, expand ->
        if (expand) {
            session.dragBy(delta.y)
        } else {
            floatPos = clamp((floatPos ?: pos) + delta)
        }
    }
    val onEnd: (Float, Boolean) -> Unit = { velocityY, expand ->
        if (expand) {
            if (session.endDrag(velocityY)) onExpand()
        } else {
            val cur = floatPos ?: pos
            val target = if (cur.x + miniPx / 2f < containerSize.width / 2f) minX else maxX
            snapJob?.cancel()
            snapJob = scope.launch {
                Animatable(cur.x).animateTo(
                    target,
                    spring(stiffness = Spring.StiffnessMediumLow),
                ) {
                    floatPos = Offset(value, (floatPos ?: cur).y)
                }
            }
        }
    }

    Box(outer) {
        Column(Modifier.fillMaxSize()) {
            if (stripVisible) {
                // Плашка схлопывается по высоте и гаснет по мере разворачивания
                Box(
                    Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val h = (placeable.height * (1f - session.progress)).roundToInt()
                            layout(placeable.width, h) { placeable.place(0, 0) }
                        }
                        .clipToBounds()
                        .graphicsLayer { alpha = (1f - session.progress / 0.4f).coerceIn(0f, 1f) },
                ) {
                    MiniStrip(
                        onBegin = onBegin,
                        onMove = onMove,
                        onEnd = onEnd,
                        onLogo = { openLink("https://www.youtube.com/watch?v=${video.id}") },
                        onExpand = onExpand,
                        onClose = { session.close() },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color.Black)) {
                // Пока окно в движении, плеер держит большой размер и только масштабируется:
                // перестраивать страницу плеера на каждом кадре дорого (отсюда была шероховатость)
                YouTubePlayerView(
                    videoId = video.id,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val moving = session.progress in 0.002f..0.998f
                            val placeable = if (moving) {
                                measurable.measure(
                                    Constraints.fixed(
                                        dockRect.width.roundToInt().coerceAtLeast(1),
                                        dockRect.height.roundToInt().coerceAtLeast(1),
                                    )
                                )
                            } else {
                                measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
                            }
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            if (session.progress in 0.002f..0.998f && dockRect.width > 0f) {
                                val k = size.width / dockRect.width
                                scaleX = k
                                scaleY = k
                                transformOrigin = TransformOrigin(0f, 0f)
                                translationY = (size.height - dockRect.height * k) / 2f
                            }
                        },
                    onError = { session.errorCode = it },
                )
            }
        }
    }
}

/**
 * Жест на нашей плашке (на плеер жесты не вешаем: правило Mouseovers).
 * fixedMode: 1 = окно едет за пальцем в любую сторону, 2 = палец вверх тянет окно к большому видео.
 */
@Composable
private fun Modifier.dragHandle(
    fixedMode: Int,
    onBegin: (expand: Boolean) -> Unit,
    onMove: (delta: Offset, expand: Boolean) -> Unit,
    onEnd: (velocityY: Float, expand: Boolean) -> Unit,
): Modifier {
    val begin by rememberUpdatedState(onBegin)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(fixedMode) {
        val tracker = VelocityTracker()
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = Offset.Zero
                begin(fixedMode == 2)
            },
            onDragEnd = { end(tracker.calculateVelocity().y, fixedMode == 2) },
            onDragCancel = { end(0f, fixedMode == 2) },
            onDrag = { change, amount ->
                change.consume()
                total += amount
                tracker.addPosition(change.uptimeMillis, total)
                move(amount, fixedMode == 2)
            },
        )
    }
}

/**
 * Полоса над видео: логотип YouTube (обязателен по правилам бренда), развернуть, закрыть.
 * Окно двигают за логотип; тап или свайп вверх по остальной плашке разворачивает его.
 */
@Composable
private fun MiniStrip(
    onBegin: (Boolean) -> Unit,
    onMove: (Offset, Boolean) -> Unit,
    onEnd: (Float, Boolean) -> Unit,
    onLogo: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .dragHandle(2, onBegin, onMove, onEnd)
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeLogo(modifier = Modifier.dragHandle(1, onBegin, onMove, onEnd), onClick = onLogo)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(32.dp).clickable(onClick = onExpand), contentAlignment = Alignment.Center) {
            ChevronDownIcon(Modifier.size(20.dp).rotate(180f))
        }
        Box(Modifier.size(32.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            CloseIcon(Modifier.size(20.dp))
        }
    }
}

/** Значок «предыдущее/следующее»: треугольник с чёрточкой. */
@Composable
internal fun YtSkipGlyph(
    next: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = w * 0.16f
        val tri = Path().apply {
            if (next) {
                moveTo(0f, 0f)
                lineTo(w - bar * 1.5f, h / 2f)
                lineTo(0f, h)
            } else {
                moveTo(w, 0f)
                lineTo(bar * 1.5f, h / 2f)
                lineTo(w, h)
            }
            close()
        }
        drawPath(tri, tint)
        drawRect(tint, topLeft = Offset(if (next) w - bar else 0f, 0f), size = Size(bar, h))
    }
}
