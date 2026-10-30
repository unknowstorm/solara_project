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

private fun navStroke(width: Float = 2f) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun HomeIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val path = Path().apply {
                moveTo(4f, 11f)
                lineTo(12f, 4f)
                lineTo(20f, 11f)
                moveTo(6f, 10f)
                lineTo(6f, 20f)
                lineTo(18f, 20f)
                lineTo(18f, 10f)
            }
            drawPath(path, tint, style = navStroke())
        }
    }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawCircle(tint, radius = 6f, center = Offset(10.5f, 10.5f), style = navStroke())
            drawLine(tint, Offset(15f, 15f), Offset(20f, 20f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun QueueIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val lines = Path().apply {
                moveTo(4f, 6f)
                lineTo(15f, 6f)
                moveTo(4f, 12f)
                lineTo(15f, 12f)
                moveTo(4f, 18f)
                lineTo(10f, 18f)
            }
            drawPath(lines, tint, style = navStroke())
            val play = Path().apply {
                moveTo(15f, 14f)
                lineTo(21f, 18f)
                lineTo(15f, 22f)
                close()
            }
            drawPath(play, tint)
        }
    }
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val lines = Path().apply {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
            }
            drawPath(lines, tint, style = navStroke())
            drawCircle(tint, radius = 3f, center = Offset(9f, 7f))
            drawCircle(tint, radius = 3f, center = Offset(15f, 17f))
        }
    }
}
