package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Whether the personal library sub-screen is open on the Spotify and YouTube pages.
 * Lives only in memory (a process-wide holder), so the page can be recreated (tab switch) and still show the same screen.
 */
internal object LibraryPanels {
    var spotify by mutableStateOf(false)
    var youtube by mutableStateOf(false)
}

/** One-line entry to the user's own library: the same on the Spotify and YouTube pages. */
@Composable
internal fun LibraryEntryCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { LibraryGlyph(cs.onPrimaryContainer, Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChevronGlyph(cs.onSurfaceVariant, Modifier.size(20.dp))
        }
    }
}

/** Three books on a shelf (24x24 grid). */
@Composable
private fun LibraryGlyph(tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawRoundRect(tint, Offset(3f, 4f), Size(4.5f, 16f), CornerRadius(1.5f))
            drawRoundRect(tint, Offset(9.75f, 4f), Size(4.5f, 16f), CornerRadius(1.5f))
            drawRoundRect(tint, Offset(16.5f, 7f), Size(4.5f, 13f), CornerRadius(1.5f))
        }
    }
}

@Composable
private fun ChevronGlyph(tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val p = Path().apply { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
            drawPath(p, tint, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
