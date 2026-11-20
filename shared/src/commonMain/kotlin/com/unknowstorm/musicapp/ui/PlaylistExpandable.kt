package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlin.math.roundToInt

private val TileCorner = 28.dp
private val CoverCorner = 20.dp

/** The size of the cover on PlaylistScreen (also the fallback until its real position is measured). */
private val ScreenCoverSize = 120.dp

/**
 * The playlist screen expands out of the tapped tile the same way the mini player expands into the full player:
 * one container whose real size/position/corners go from the tile to the whole area (progress 0f..1f, the same
 * spring), the cover flows from the tile to its place on the screen, the tile's name dissolves at the start and
 * the screen content fades in during the second half.
 *
 * Put it in the same Box where the playlist screen used to be; [playlist] == null means closed.
 */
@Composable
fun PlaylistExpandable(
    playlist: Playlist?,
    repo: PlaylistsRepository,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<Playlist?>(null) }
    var tile by remember { mutableStateOf(Rect.Zero) }
    // 0 = the tile, 1 = the full screen. Read it ONLY inside lambdas (layout / graphicsLayer / draw).
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playlist) {
        if (playlist != null) {
            tile = ZoomOrigin.rect
            shown = playlist
        }
        animate(
            initialValue = progress.floatValue,
            targetValue = if (playlist != null) 1f else 0f,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        ) { v, _ -> progress.floatValue = v.coerceIn(0f, 1f) }
        if (playlist == null) shown = null
    }

    val pl = shown
    var origin by remember { mutableStateOf(Offset.Zero) }
    // [0] = the full-size content root, [1] = the cover on PlaylistScreen
    val coords = remember { arrayOfNulls<LayoutCoordinates>(2) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        if (pl == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // The tile in this overlay's coordinates (a centered square if the tile is unknown)
        val src = if (tile.width > 0f) {
            Rect(tile.left - origin.x, tile.top - origin.y, tile.right - origin.x, tile.bottom - origin.y)
        } else {
            val side = fullW * 0.4f
            Rect(fullW / 2f - side / 2f, fullH / 2f - side / 2f, fullW / 2f + side / 2f, fullH / 2f + side / 2f)
        }
        val tileCornerPx = with(density) { TileCorner.toPx() }
        val coverCornerPx = with(density) { CoverCorner.toPx() }
        val coverLayoutPx = with(density) { ScreenCoverSize.toPx() }
        val fallbackPos = with(density) { Offset(16.dp.toPx(), (topInset + 68.dp).toPx()) }
        val fullWdp = with(density) { fullW.toDp() }
        val fullHdp = with(density) { fullH.toDp() }
        val tileWdp = with(density) { src.width.toDp() }
        val tileHdp = with(density) { src.height.toDp() }
        val bg = MaterialTheme.colorScheme.background

        // Where the cover stands on PlaylistScreen (relative to the content root)
        fun coverTarget(): Rect {
            val a = coords[0]
            val c = coords[1]
            return if (a != null && c != null && a.isAttached && c.isAttached) {
                Rect(a.localPositionOf(c, Offset.Zero), c.size.toSize())
            } else {
                Rect(fallbackPos, Size(coverLayoutPx, coverLayoutPx))
            }
        }

        // Home dims behind the opening screen
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.3f * progress.floatValue }
                .background(Color.Black)
        )

        Box(
            Modifier
                // position: from the tile to the top-left corner
                .offset {
                    val p = progress.floatValue
                    IntOffset(lerp(src.left, 0f, p).roundToInt(), lerp(src.top, 0f, p).roundToInt())
                }
                // size: from the tile to the whole area
                .layout { measurable, _ ->
                    val p = progress.floatValue
                    val w = lerp(src.width, fullW, p).roundToInt().coerceAtLeast(0)
                    val h = lerp(src.height, fullH, p).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                // corners flow from the tile's to the square ones
                .graphicsLayer {
                    shape = RoundedCornerShape(CornerSize(lerp(tileCornerPx, 0f, progress.floatValue)))
                    clip = true
                }
                .drawBehind { drawRect(bg) }
        ) {
            // The screen itself: always full size, fades in during the second half of the expansion
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(fullWdp, fullHdp)
                    .onGloballyPositioned { coords[0] = it }
                    .graphicsLayer { alpha = ((progress.floatValue - 0.3f) / 0.5f).coerceIn(0f, 1f) }
            ) {
                PlaylistScreen(
                    playlist = pl,
                    repo = repo,
                    providers = providers,
                    audio = audio,
                    onBack = onBack,
                    // the real cover is hidden while the shared one flies; it takes over at the very end
                    coverAlpha = { if (progress.floatValue >= 0.999f) 1f else 0f },
                    onCoverLayout = { coords[1] = it },
                )
            }

            // The shared cover: the tile's picture flows into the big cover
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(ScreenCoverSize)
                    .graphicsLayer {
                        val p = progress.floatValue
                        val t = coverTarget()
                        val s = lerp(src.width, t.width, p) / coverLayoutPx
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = s
                        scaleY = s
                        translationX = lerp(0f, t.left, p)
                        translationY = lerp(0f, t.top, p)
                        shape = RoundedCornerShape(CornerSize(lerp(tileCornerPx, coverCornerPx, p) / s))
                        clip = true
                        alpha = if (p >= 0.999f) 0f else 1f
                    }
            ) {
                PlaylistCover(repo, pl, Modifier.fillMaxSize(), shape = RectangleShape)
            }

            // The tile's gradient and name: pinned to the tile's size, dissolve at the start
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(tileWdp, tileHdp)
                    .graphicsLayer { alpha = (1f - progress.floatValue / 0.3f).coerceIn(0f, 1f) }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f),
                            )
                        )
                )
                Text(
                    pl.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                )
            }
        }
    }
}
