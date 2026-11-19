package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S

/**
 * The library start page: a wide "My likes" tile and square tiles under it.
 * [onOpen] gets the section index: 0 likes, 1 playlists, 2 history, 3 files on this phone, 4 cache / downloads
 * (the same order as LibraryTab in LikedScreen.kt).
 */
@Composable
fun LibraryTiles(
    likesCount: Int?,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Индексы плиток, которые уже сыграли каскад: при возврате из раздела они не анимируются повторно
    played: MutableSet<Int> = remember { mutableSetOf() },
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Низкое окно (телефон в альбомной ориентации): широкая плитка и сетка не помещаются, всё идёт одним рядом
        if (maxHeight < 300.dp) LibraryTilesRow(likesCount, onOpen, played) else LibraryTilesGrid(likesCount, onOpen, played)
    }
}

@Composable
private fun LibraryTilesGrid(likesCount: Int?, onOpen: (Int) -> Unit, played: MutableSet<Int>) {
    val cs = MaterialTheme.colorScheme
    // Планшет: ровно 4 колонки на всю ширину (Adaptive оставлял бы пустые колонки справа).
    // На очень широком окне плитки ниже квадрата, чтобы не вырастали до 300 dp.
    val wide = width >= 600.dp
    val tile = Modifier.fillMaxWidth().aspectRatio(if (width >= 900.dp) 1.4f else 1f)
    LazyVerticalGrid(
        columns = if (wide) GridCells.Fixed(4) else GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TileSurface(
                container = cs.primaryContainer,
                content = cs.onPrimaryContainer,
                onClick = { onOpen(0) },
                modifier = Modifier.fillMaxWidth().height(112.dp).staggeredEntrance(0, played),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconDisc(56.dp, cs.onPrimaryContainer) { HeartIcon(true, Modifier.size(30.dp), cs.onPrimaryContainer) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(S.libLikes, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (likesCount != null) {
                            Text(
                                S.tracks(likesCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
        item {
            SquareTile(
                S.libPlaylists, cs.secondaryContainer, cs.onSecondaryContainer, { onOpen(1) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).staggeredEntrance(1, played),
            ) { PlaylistGlyph(Modifier.size(28.dp), it) }
        }
        item {
            SquareTile(
                S.libHistory, cs.tertiaryContainer, cs.onTertiaryContainer, { onOpen(2) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).staggeredEntrance(2, played),
            ) { HistoryGlyph(Modifier.size(28.dp), it) }
        }
        item {
            SquareTile(
                S.libLocal, cs.surfaceVariant, cs.onSurfaceVariant, { onOpen(3) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).staggeredEntrance(3, played),
            ) { FolderGlyph(Modifier.size(28.dp), it) }
        }
        item {
            SquareTile(
                S.libCache, cs.primaryContainer, cs.onPrimaryContainer, { onOpen(4) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).staggeredEntrance(4, played),
            ) { DownloadTrayGlyph(Modifier.size(28.dp), it) }
        }
    }
}

/** Все пять плиток одного размера в один ряд: для низкого окна, где сетка не помещается без прокрутки. */
@Composable
private fun LibraryTilesRow(likesCount: Int?, onOpen: (Int) -> Unit, played: MutableSet<Int>) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tile = Modifier.weight(1f).fillMaxHeight().heightIn(max = 190.dp)
        SquareTile(
            S.libLikes, cs.primaryContainer, cs.onPrimaryContainer, { onOpen(0) },
            modifier = tile.staggeredEntrance(0, played), compact = true, subtitle = likesCount?.let { S.tracks(it) },
        ) { HeartIcon(true, Modifier.size(24.dp), it) }
        SquareTile(S.libPlaylists, cs.secondaryContainer, cs.onSecondaryContainer, { onOpen(1) }, modifier = tile.staggeredEntrance(1, played), compact = true) {
            PlaylistGlyph(Modifier.size(24.dp), it)
        }
        SquareTile(S.libHistory, cs.tertiaryContainer, cs.onTertiaryContainer, { onOpen(2) }, modifier = tile.staggeredEntrance(2, played), compact = true) {
            HistoryGlyph(Modifier.size(24.dp), it)
        }
        SquareTile(S.libLocal, cs.surfaceVariant, cs.onSurfaceVariant, { onOpen(3) }, modifier = tile.staggeredEntrance(3, played), compact = true) {
            FolderGlyph(Modifier.size(24.dp), it)
        }
        SquareTile(S.libCache, cs.primaryContainer, cs.onPrimaryContainer, { onOpen(4) }, modifier = tile.staggeredEntrance(4, played), compact = true) {
            DownloadTrayGlyph(Modifier.size(24.dp), it)
        }
    }
}

@Composable
private fun SquareTile(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    compact: Boolean = false,
    subtitle: String? = null,
    icon: @Composable (Color) -> Unit,
) {
    TileSurface(container, content, onClick, modifier) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconDisc(if (compact) 40.dp else 48.dp, content) { icon(content) }
            Column {
                Text(
                    label,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A tonal container that presses in on touch: it shrinks a little and its corners tighten, on a spring. */
@Composable
private fun TileSurface(
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    body: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
    )
    val corner by animateDpAsState(
        if (pressed) 16.dp else 28.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
    )
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) { body() }
}

@Composable
private fun IconDisc(size: Dp, tint: Color, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

// ---------- Icons: a 24x24 grid, rounded strokes, like the other icons in the app ----------

private fun glyphStroke(width: Float = 2f) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A playlist: three lines and a note. */
@Composable
private fun PlaylistGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val lines = Path().apply {
                moveTo(3f, 6f); lineTo(13f, 6f)
                moveTo(3f, 11f); lineTo(13f, 11f)
                moveTo(3f, 16f); lineTo(9f, 16f)
            }
            drawPath(lines, tint, style = glyphStroke())
            drawCircle(tint, radius = 3f, center = Offset(16f, 17.5f))
            val stem = Path().apply {
                moveTo(19f, 17.5f); lineTo(19f, 5.5f); lineTo(22.5f, 9f)
            }
            drawPath(stem, tint, style = glyphStroke())
        }
    }
}

/** History: a clock with a counter-clockwise arrow. */
@Composable
private fun HistoryGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawArc(
                color = tint,
                startAngle = 200f,
                sweepAngle = 320f,
                useCenter = false,
                topLeft = Offset(3.5f, 3.5f),
                size = Size(17f, 17f),
                style = glyphStroke(),
            )
            val head = Path().apply {
                moveTo(1.3f, 9.2f); lineTo(6.7f, 9.2f); lineTo(4f, 13f); close()
            }
            drawPath(head, tint)
            val hands = Path().apply {
                moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.5f, 14f)
            }
            drawPath(hands, tint, style = glyphStroke())
        }
    }
}

/** Files on the phone: a folder with a small note. */
@Composable
private fun FolderGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val folder = Path().apply {
                moveTo(3f, 19f); lineTo(3f, 5.5f); lineTo(9.5f, 5.5f); lineTo(11.8f, 8.5f)
                lineTo(21f, 8.5f); lineTo(21f, 19f); close()
            }
            drawPath(folder, tint, style = glyphStroke())
            drawCircle(tint, radius = 1.7f, center = Offset(11.2f, 15.6f))
            val note = Path().apply {
                moveTo(12.9f, 15.6f); lineTo(12.9f, 11.6f); lineTo(15.5f, 12.6f)
            }
            drawPath(note, tint, style = glyphStroke(1.6f))
        }
    }
}

/** Cache / downloads: an arrow going into a tray. */
@Composable
private fun DownloadTrayGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val arrow = Path().apply {
                moveTo(12f, 3.5f); lineTo(12f, 14.5f)
                moveTo(7.5f, 10.2f); lineTo(12f, 14.7f); lineTo(16.5f, 10.2f)
            }
            drawPath(arrow, tint, style = glyphStroke())
            val tray = Path().apply {
                moveTo(4f, 15f); lineTo(4f, 19.5f); lineTo(20f, 19.5f); lineTo(20f, 15f)
            }
            drawPath(tray, tint, style = glyphStroke())
        }
    }
}
