package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.model.toService

/** Логотип сервиса цветом темы. Если контура нет, показываем букву. */
@Composable
fun ServiceLogo(
    service: Service?,
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = LocalContentColor.current,
) {
    when {
        service == null -> Unit
        service == Service.ALL -> GlobeIcon(modifier, tint)
        else -> {
            val path = remember(service) {
                logoPathData(service.name)?.let { data ->
                    runCatching { PathParser().parsePathString(data).toPath() }.getOrNull()
                }
            }
            if (path != null) {
                Canvas(modifier) {
                    val origin = logoOrigin(service.name)
                    scale(size.width / logoBox(service.name), pivot = Offset.Zero) {
                        translate(-origin.x, -origin.y) { drawPath(path, tint) }
                    }
                }
            } else {
                BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
                    val fs = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.8f).toSp() }
                    Text(
                        service.title.first().toString(),
                        color = tint,
                        fontSize = fs,
                        lineHeight = fs,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
fun GlobeIcon(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val line = Stroke(1.8f, cap = StrokeCap.Round)
            drawCircle(tint, 9f, Offset(12f, 12f), style = line)
            drawOval(tint, Offset(7.5f, 3f), Size(9f, 18f), style = line)
            drawLine(tint, Offset(3f, 12f), Offset(21f, 12f), 1.8f, StrokeCap.Round)
        }
    }
}

/** Кружок с логотипом источника для угла обложки. */
@Composable
fun ServiceBadge(service: Service?, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    if (service == null) return
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ServiceLogo(service, Modifier.size(size * 0.58f), Color.White)
        }
    }
}

/** Обложка трека, при необходимости со значком источника. */
@Composable
fun TrackCover(
    track: Track,
    modifier: Modifier = Modifier,
    badge: Boolean = true,
    badgeSize: Dp = 22.dp,
) {
    Box(modifier) {
        CoverImage(track.artworkUrl, Modifier.fillMaxSize())
        if (badge) {
            ServiceBadge(
                track.provider.toService(),
                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                badgeSize,
            )
        }
    }
}