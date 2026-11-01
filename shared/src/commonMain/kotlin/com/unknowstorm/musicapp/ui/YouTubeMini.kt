package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Минимум по правилам YouTube (Required Minimum Functionality): окно плеера не меньше 200x200
private val MINI_SIZE = 200.dp
private val STRIP_HEIGHT = 36.dp
private val EDGE = 8.dp
private val MINI_CORNER = 12.dp // подберите под скругление мини-плеера аудио

// Рывок вверх по плашке разворачивает окно. Скорость в dp/с, путь пальца вверх в dp.
private val FLICK_VELOCITY = 1200.dp
private val FLICK_DISTANCE = 24.dp

/**
 * Текущее видео YouTube на уровне всего приложения.
 * docked = true: видео развёрнуто на экране YouTube; false: плавающее окно поверх приложения.
 * queue: список, из которого выбрано видео (для кнопок «предыдущее/следующее» на большом экране).
 */
class YouTubeSession {
    var video by mutableStateOf<YouTubeVideo?>(null)
        private set
    var queue by mutableStateOf<List<YouTubeVideo>>(emptyList())
        private set
    var docked by mutableStateOf(false)
    var dockBounds by mutableStateOf<Rect?>(null)
    var errorCode by mutableStateOf<Int?>(null)

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
        dockBounds = null
    }

    fun close() {
        video = null
        queue = emptyList()
        docked = false
        dockBounds = null
        errorCode = null
    }
}

/**
 * Единственный плеер YouTube в приложении. Он всегда рисуется здесь, в одном и том же месте
 * дерева, поэтому при сворачивании видео не перезапускается.
 *
 * Анимация устроена так же, как у ExpandablePlayer: один progress (0 = мини-окно, 1 = большой экран),
 * та же пружина, progress читается только в layout/graphicsLayer, без рекомпозиции на каждый кадр.
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

    // 0 = мини-окно, 1 = видео развёрнуто на экране YouTube
    val progress = remember { mutableFloatStateOf(0f) }
    // Последнее известное место под плеер: нужно и при сворачивании, когда большой экран уже исчез
    val dockHolder = remember { arrayOfNulls<Rect>(1) }
    session.dockBounds?.let { dockHolder[0] = it }
    val wantDocked = session.docked && session.dockBounds != null

    LaunchedEffect(wantDocked) {
        animate(
            initialValue = progress.floatValue,
            targetValue = if (wantDocked) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) { v, _ -> progress.floatValue = v.coerceIn(0f, 1f) }
    }
    val stripVisible by remember { derivedStateOf { progress.floatValue < 0.999f } }

    val miniPx = with(density) { MINI_SIZE.toPx() }
    val stripPx = with(density) { STRIP_HEIGHT.toPx() }
    val edgePx = with(density) { EDGE.toPx() }
    val cornerPx = with(density) { MINI_CORNER.toPx() }
    val shadowPx = with(density) { 8.dp.toPx() }
    val reservePx = with(density) { 150.dp.toPx() } // место под нижнюю панель и музыкальный мини-плеер
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()

    val minX = edgePx
    val minY = topInset + edgePx
    val maxX = (containerSize.width - miniPx - edgePx).coerceAtLeast(minX)
    val maxY = (containerSize.height - miniPx - stripPx - edgePx).coerceAtLeast(minY)
    fun clamp(o: Offset) = Offset(o.x.coerceIn(minX, maxX), o.y.coerceIn(minY, maxY))

    var floatPos by remember { mutableStateOf<Offset?>(null) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    val pos = clamp(floatPos ?: Offset(maxX, maxY - reservePx))

    val outer: Modifier = Modifier
        .offset {
            val p = progress.floatValue
            val d = dockHolder[0]
            if (d != null) {
                val tl = d.topLeft - containerOrigin
                IntOffset(lerp(pos.x, tl.x, p).roundToInt(), lerp(pos.y, tl.y, p).roundToInt())
            } else {
                IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
            }
        }
        .layout { measurable, _ ->
            val p = progress.floatValue
            val d = dockHolder[0]
            val w = (if (d != null) lerp(miniPx, d.width, p) else miniPx).roundToInt().coerceAtLeast(0)
            val h = (if (d != null) lerp(miniPx + stripPx, d.height, p) else miniPx + stripPx)
                .roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val p = progress.floatValue
            shadowElevation = lerp(shadowPx, 0f, p)
            shape = RoundedCornerShape(lerp(cornerPx, 0f, p))
            clip = true
        }

    val onDragStart: () -> Unit = {
        snapJob?.cancel()
        if (floatPos == null) floatPos = pos
    }
    val onDrag: (Offset) -> Unit = { delta -> floatPos = clamp((floatPos ?: pos) + delta) }
    val onDragEnd: (Boolean) -> Unit = { flickUp ->
        snapJob?.cancel()
        if (flickUp) {
            onExpand()
        } else {
            val cur = floatPos ?: pos
            val target = if (cur.x + miniPx / 2f < containerSize.width / 2f) minX else maxX
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
                // Плашка схлопывается по высоте и гаснет (как содержимое мини-плеера у аудио)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val h = (placeable.height * (1f - progress.floatValue)).roundToInt()
                            layout(placeable.width, h) { placeable.place(0, 0) }
                        }
                        .clipToBounds()
                        .graphicsLayer { alpha = (1f - progress.floatValue / 0.4f).coerceIn(0f, 1f) },
                ) {
                    MiniStrip(
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onLogo = { openLink("https://www.youtube.com/watch?v=${video.id}") },
                        onExpand = onExpand,
                        onClose = { session.close() },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                YouTubePlayerView(
                    videoId = video.id,
                    modifier = Modifier.fillMaxSize(),
                    onError = { session.errorCode = it },
                )
            }
        }
    }
}

/**
 * Перетаскивание окна и рывок вверх. Вешается только на нашу плашку, не на плеер (правило Mouseovers).
 * Скорость считается по пути пальца, а не по позиции внутри плашки: плашка едет вместе с пальцем.
 */
@Composable
private fun Modifier.dragHandle(
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (flickUp: Boolean) -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) {
        val tracker = VelocityTracker()
        var total = Offset.Zero
        val flickVelocity = FLICK_VELOCITY.toPx()
        val flickDistance = FLICK_DISTANCE.toPx()
        detectDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = Offset.Zero
                start()
            },
            onDragEnd = {
                val v = tracker.calculateVelocity()
                end(v.y < -flickVelocity && abs(v.y) > 2f * abs(v.x) && total.y < -flickDistance)
            },
            onDragCancel = { end(false) },
            onDrag = { change, amount ->
                change.consume()
                total += amount
                tracker.addPosition(change.uptimeMillis, total)
                drag(amount)
            },
        )
    }
}

/**
 * Полоса над видео: логотип YouTube (обязателен по правилам бренда), развернуть, закрыть.
 * Тап или рывок вверх по полосе разворачивает окно, медленное перетаскивание двигает его.
 */
@Composable
private fun MiniStrip(
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Boolean) -> Unit,
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
            .dragHandle(onDragStart, onDrag, onDragEnd)
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeLogo(onClick = onLogo)
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
