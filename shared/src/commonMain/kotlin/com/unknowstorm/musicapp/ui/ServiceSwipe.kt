package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Service
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Горизонтальный свайп по свободному месту листает чипы сервисов, как панель: ♡ Любимые (если hasLikes),
 * затем сервисы слева направо. Жесты, которые забрали карусели и списки, сюда не доходят.
 * Содержимое едет за пальцем; после отпускания уезжает, чип меняется, новое содержимое въезжает пружиной.
 * Spotify и «скоро» в порядок не входят (вход через «+»).
 */
@Composable
fun Modifier.serviceSwipe(
    current: Service,
    likesSelected: Boolean,
    enabled: () -> Boolean = { true },
    hasLikes: Boolean = true,
    onSelect: (Service) -> Unit,
    onLikes: () -> Unit,
): Modifier {
    val cur by rememberUpdatedState(current)
    val likes by rememberUpdatedState(likesSelected)
    val on by rememberUpdatedState(enabled)
    val withLikes by rememberUpdatedState(hasLikes)
    val select by rememberUpdatedState(onSelect)
    val openLikes by rememberUpdatedState(onLikes)
    val scope = rememberCoroutineScope()
    val order = remember { Service.entries.filter { it.ready && it != Service.SPOTIFY } }

    var offsetPx by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var job by remember { mutableStateOf<Job?>(null) }

    // direction: -1 = палец ведут влево (следующий чип), 1 = вправо (предыдущий). null = за краем списка
    fun targetFor(direction: Int): Int? {
        val idx = if (likes) -1 else order.indexOf(cur)
        if (!likes && idx < 0) return null
        val t = idx + if (direction < 0) 1 else -1
        val min = if (withLikes) -1 else 0
        return if (t < min || t > order.lastIndex) null else t
    }

    fun slideSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun settleBack(velocity: Float) {
        job?.cancel()
        job = scope.launch {
            animate(offsetPx, 0f, velocity, slideSpring()) { v, _ -> offsetPx = v }
        }
    }

    fun commit(target: Int, direction: Int, velocity: Float) {
        val out = if (direction < 0) -width else width
        job?.cancel()
        job = scope.launch {
            // старый экран быстро уезжает за край
            animate(offsetPx, out, velocity, tween(150, easing = FastOutLinearInEasing)) { v, _ -> offsetPx = v }
            // чип меняется и новый экран появляется с противоположной стороны в том же кадре
            if (target == -1) openLikes() else select(order[target])
            offsetPx = -out
            animate(-out, 0f, 0f, slideSpring()) { v, _ -> offsetPx = v }
        }
    }

    return this
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            val flick = 600.dp.toPx()
            var total = 0f
            var active = false
            detectHorizontalDragGestures(
                onDragStart = {
                    job?.cancel()
                    total = offsetPx
                    tracker.resetTracking()
                    width = size.width.toFloat().coerceAtLeast(1f)
                    active = on()
                },
                onDragEnd = {
                    if (active) {
                        val vx = tracker.calculateVelocity().x
                        val dir = if (total < 0f) -1 else 1
                        val t = targetFor(dir)
                        val far = abs(total) > width * 0.28f
                        val flicked = abs(vx) > flick && (vx < 0f) == (total < 0f)
                        if (t != null && (far || flicked)) commit(t, dir, vx) else settleBack(vx)
                    }
                    active = false
                },
                onDragCancel = {
                    if (active) settleBack(0f)
                    active = false
                },
                onHorizontalDrag = { change, dx ->
                    if (active) {
                        change.consume()
                        total += dx
                        tracker.addPosition(change.uptimeMillis, Offset(total, 0f))
                        // за краем списка экран тянется с сопротивлением
                        offsetPx = if (targetFor(if (total < 0f) -1 else 1) != null) total else total * 0.25f
                    }
                },
            )
        }
        .graphicsLayer {
            translationX = offsetPx
            alpha = 1f - 0.35f * (abs(offsetPx) / width).coerceIn(0f, 1f)
        }
}
