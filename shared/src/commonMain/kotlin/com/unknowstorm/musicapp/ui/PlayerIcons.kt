package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun PlayPauseIcon(
    playing: Boolean,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (playing) {
            val barW = w * 0.24f
            val radius = CornerRadius(barW * 0.25f)
            drawRoundRect(tint, Offset(w * 0.2f, h * 0.15f), Size(barW, h * 0.7f), radius)
            drawRoundRect(tint, Offset(w * 0.56f, h * 0.15f), Size(barW, h * 0.7f), radius)
        } else {
            val path = Path().apply {
                moveTo(w * 0.25f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.5f)
                lineTo(w * 0.25f, h * 0.85f)
                close()
            }
            drawPath(path, tint)
        }
    }
}

@Composable
fun SkipIcon(
    next: Boolean,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.13f
        val radius = CornerRadius(barW * 0.3f)
        val path = Path()
        if (next) {
            path.moveTo(w * 0.15f, h * 0.18f)
            path.lineTo(w * 0.68f, h * 0.5f)
            path.lineTo(w * 0.15f, h * 0.82f)
            path.close()
            drawRoundRect(tint, Offset(w * 0.74f, h * 0.18f), Size(barW, h * 0.64f), radius)
        } else {
            path.moveTo(w * 0.85f, h * 0.18f)
            path.lineTo(w * 0.32f, h * 0.5f)
            path.lineTo(w * 0.85f, h * 0.82f)
            path.close()
            drawRoundRect(tint, Offset(w * 0.13f, h * 0.18f), Size(barW, h * 0.64f), radius)
        }
        drawPath(path, tint)
    }
}

@Composable
fun CloseIcon(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        drawLine(tint, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), stroke, StrokeCap.Round)
    }
}
