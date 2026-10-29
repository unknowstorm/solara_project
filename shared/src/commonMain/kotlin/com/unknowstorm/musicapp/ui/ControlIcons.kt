package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

// Все иконки нарисованы в сетке 24x24 и масштабируются под размер

private fun lineStyle(width: Float = 2f) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun Path.triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
    lineTo(x3, y3)
    close()
}

@Composable
fun HeartIcon(
    filled: Boolean,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val heart = Path().apply {
                moveTo(12f, 21.35f)
                lineTo(10.55f, 20.03f)
                cubicTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                cubicTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                cubicTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
                cubicTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                cubicTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                cubicTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.03f)
                lineTo(12f, 21.35f)
                close()
            }
            if (filled) drawPath(heart, tint) else drawPath(heart, tint, style = lineStyle(1.8f))
        }
    }
}

@Composable
fun MoreIcon(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawCircle(tint, 2f, Offset(12f, 5f))
            drawCircle(tint, 2f, Offset(12f, 12f))
            drawCircle(tint, 2f, Offset(12f, 19f))
        }
    }
}

@Composable
fun ChevronDownIcon(
    modifier: Modifier = Modifier.size(28.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val path = Path().apply {
                moveTo(5f, 9f)
                lineTo(12f, 16f)
                lineTo(19f, 9f)
            }
            drawPath(path, tint, style = lineStyle(2.2f))
        }
    }
}

@Composable
fun ShuffleIcon(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val lines = Path().apply {
                moveTo(3f, 7f)
                lineTo(7f, 7f)
                cubicTo(11f, 7f, 12f, 17f, 16.5f, 17f)
                moveTo(3f, 17f)
                lineTo(7f, 17f)
                cubicTo(11f, 17f, 12f, 7f, 16.5f, 7f)
            }
            drawPath(lines, tint, style = lineStyle())
            val heads = Path().apply {
                triangle(16f, 13.8f, 21f, 17f, 16f, 20.2f)
                triangle(16f, 3.8f, 21f, 7f, 16f, 10.2f)
            }
            drawPath(heads, tint)
        }
    }
}

@Composable
fun RepeatIcon(
    one: Boolean,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val loop = Path().apply {
                moveTo(5f, 12f)
                lineTo(5f, 10f)
                quadraticTo(5f, 7f, 8f, 7f)
                lineTo(17.5f, 7f)
                moveTo(19f, 12f)
                lineTo(19f, 14f)
                quadraticTo(19f, 17f, 16f, 17f)
                lineTo(6.5f, 17f)
            }
            drawPath(loop, tint, style = lineStyle())
            val heads = Path().apply {
                triangle(17f, 4f, 21.5f, 7f, 17f, 10f)
                triangle(7f, 14f, 2.5f, 17f, 7f, 20f)
            }
            drawPath(heads, tint)
            if (one) {
                val digit = Path().apply {
                    moveTo(10.6f, 10.6f)
                    lineTo(12.2f, 9.4f)
                    lineTo(12.2f, 14.6f)
                }
                drawPath(digit, tint, style = lineStyle(1.6f))
            }
        }
    }
}
