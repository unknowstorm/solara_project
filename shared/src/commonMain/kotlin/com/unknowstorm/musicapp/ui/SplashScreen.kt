package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.logo
import org.jetbrains.compose.resources.painterResource

private val LogoSize = 160.dp
// Inversion for the light theme: light becomes black (the disc), dark or pale is boosted to white (the letter S).
// Transparency is untouched. 6375 = 25 * 255.
private val InvertedLogo = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -25f, 0f, 0f, 0f, 6375f,
            0f, -25f, 0f, 0f, 6375f,
            0f, 0f, -25f, 0f, 6375f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)
private const val ENTER_MS = 380 // the logo "slides in": it fades in, grows and finishes rotating to a stop
private const val EXIT_MS = 280 // the splash dissolves, the logo grows a little
private const val SPIN_UP_SEC = 0.45f // the record accelerates from rest to working speed
private const val SPIN_DEG_PER_SEC = 400f // working rotation speed
private const val MAX_FRAME_SEC = 0.05f // a stalled frame must not "jump" half a turn

/**
 * Splash: the logo record.
 *
 * Phases:
 *  1. enter: the logo fades in and finishes rotating to rest ([onEntered] is called when done);
 *  2. [spinning]: the record accelerates smoothly from rest, without a jerk;
 *  3. [leaving]: the splash dissolves ([onFinished] is called when done).
 * Angle and scale are read only in the draw phase (graphicsLayer): rotation does not cause recomposition.
 */
@Composable
fun SplashScreen(
    spinning: Boolean,
    leaving: Boolean,
    onEntered: () -> Unit,
    onFinished: () -> Unit,
) {
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var angle by remember { mutableFloatStateOf(0f) }
    val entered by rememberUpdatedState(onEntered)
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(ENTER_MS, easing = FastOutSlowInEasing))
        entered()
    }

    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        var last = withFrameNanos { it }
        var t = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SEC)
            last = now
            t += dt
            val k = (t / SPIN_UP_SEC).coerceIn(0f, 1f)
            val speed = SPIN_DEG_PER_SEC * k * k * (3f - 2f * k) // smoothstep: the speed grows smoothly
            angle = (angle + speed * dt) % 360f
        }
    }

    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        exit.animateTo(1f, tween(EXIT_MS, easing = FastOutLinearInEasing))
        finished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = null,
            colorFilter = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) InvertedLogo else null,
            modifier = Modifier
                .size(LogoSize)
                .graphicsLayer {
                    val e = enter.value
                    val s = lerp(0.85f, 1f, e) * (1f + 0.12f * exit.value)
                    scaleX = s
                    scaleY = s
                    alpha = e
                    rotationZ = angle - (1f - e) * 120f
                },
        )
    }
}
