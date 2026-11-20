package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository

/**
 * A playlist tile: the cover (own photo or a collage) fills it, the name sits on a dark gradient at the bottom.
 * It presses in on touch like the library tiles. [compact] is for small tiles in horizontal rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTile(
    repo: PlaylistsRepository,
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val spec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spec)
    val corner by animateDpAsState(
        if (pressed) 16.dp else if (compact) 20.dp else 28.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
    )
    Box(
        modifier
            .zoomSource()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        PlaylistCover(repo, playlist, Modifier.fillMaxSize(), shape = RectangleShape)
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
            playlist.name,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(if (compact) 10.dp else 14.dp),
        )
    }
}

/** Playlists as a grid of square tiles (the same grid as the library start page). */
@Composable
fun PlaylistTilesGrid(
    repo: PlaylistsRepository,
    playlists: List<Playlist>,
    onOpen: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((Playlist) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val played = remember { mutableSetOf<Int>() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(playlists, key = { _, pl -> pl.id }) { index, pl ->
            PlaylistTile(
                repo = repo,
                playlist = pl,
                onClick = { onOpen(pl) },
                onLongClick = onLongClick?.let { cb -> { cb(pl) } },
                modifier = Modifier
                    .animateItem()
                    .staggeredEntrance(index, played, maxIndex = 11)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
    }
}
