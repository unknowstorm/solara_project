package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.logo_s
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val LogoSize = 180.dp
private const val ENTER_MS = 320 // the letter fades in; the springy scale runs in parallel
private const val EXIT_MS = 280 // the splash dissolves, the letter grows a little
private const val SHINE_MS = 520 // one pass of the highlight
private const val SHINE_PAUSE_MS = 250L // pause between passes while the app is still loading

/**
 * Splash: the "S" letter logo.
 *
 * Phases:
 *  1. enter: the letter fades in and settles with a small spring ([onEntered] is called when the fade is done,
 *     the spring does not delay the app start);
 *  2. [spinning]: a diagonal highlight sweeps over the letter (clipped to its shape) and repeats while loading;
 *  3. [leaving]: the splash dissolves ([onFinished] is called when done).
 * The letter color comes from the theme, so light and dark themes both work. All animated values are read in
 * the draw/layer phase only: no recomposition per frame.
 */
@Composable
fun SplashScreen(
    spinning: Boolean,
    leaving: Boolean,
    onEntered: () -> Unit,
    onFinished: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val scale = remember { Animatable(0.82f) }
    val exit = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val entered by rememberUpdatedState(onEntered)
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        appear.animateTo(1f, tween(ENTER_MS, easing = FastOutSlowInEasing))
        entered()
    }

    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(SHINE_MS, easing = FastOutSlowInEasing))
            delay(SHINE_PAUSE_MS)
        }
    }

    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        exit.animateTo(1f, tween(EXIT_MS, easing = FastOutLinearInEasing))
        finished()
    }

    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // Dark theme: a slightly dimmed light letter, so the white highlight stays visible on it
    val letter = if (dark) scheme.onBackground.copy(alpha = 0.78f) else scheme.onBackground
    val shineColor = Color.White.copy(alpha = if (dark) 0.95f else 0.55f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(scheme.background)
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.logo_s),
            contentDescription = null,
            colorFilter = ColorFilter.tint(letter),
            modifier = Modifier
                .size(LogoSize)
                .graphicsLayer {
                    val s = scale.value * (1f + 0.12f * exit.value)
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                    // Offscreen layer: SrcAtop below paints the highlight only where the letter is
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val p = shine.value
                    if (p > 0f && p < 1f) {
                        val band = size.width * 0.55f
                        val cx = lerp(-band, size.width + band, p)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, shineColor, Color.Transparent),
                                start = Offset(cx - band / 2f, 0f),
                                end = Offset(cx + band / 2f, size.height * 0.35f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )
    }
}
