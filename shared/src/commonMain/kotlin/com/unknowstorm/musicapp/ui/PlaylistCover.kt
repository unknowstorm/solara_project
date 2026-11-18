package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TODO: move these two labels into i18n/Strings.kt (UA + EN) like the rest of the texts.
 * They live here only because Strings.kt was not part of the files this change was made from.
 */
internal object PlaylistCoverText {
    const val change = "Change cover"
    const val remove = "Remove cover"
}

private class CoverLoad(val loading: Boolean, val image: ImageBitmap?)

/**
 * Playlist cover: the user's own photo if there is one, otherwise a collage of the first tracks' covers
 * (4 different ones: 2×2, fewer: the first one, none: the logo letter).
 *
 * [artworks] are the track cover URLs when the caller already has them (they are always fresh);
 * with null the cover URLs of the first tracks are fetched (and cached) by the repository, which suits list tiles.
 */
@Composable
fun PlaylistCover(
    repo: PlaylistsRepository,
    playlist: Playlist,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    artworks: List<String?>? = null,
) {
    val own by produceState(CoverLoad(playlist.cover != null, null), playlist.id, playlist.cover) {
        if (playlist.cover == null) {
            value = CoverLoad(false, null)
            return@produceState
        }
        value = CoverLoad(true, null)
        val bytes = repo.loadCover(playlist)
        value = CoverLoad(false, bytes?.let { withContext(Dispatchers.Default) { decodeImageBitmap(it) } })
    }
    val fetched by produceState(emptyList<String>(), playlist.id, artworks == null) {
        value = if (artworks == null) repo.loadPreview(playlist.id) else emptyList()
    }

    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val image = own.image
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // The own photo is still loading: just the neutral background, so the collage does not blink
            own.loading -> Unit
            else -> {
                val urls = (artworks ?: fetched)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)
                CoverCollage(urls)
            }
        }
    }
}

@Composable
private fun CoverCollage(urls: List<String>) {
    if (urls.size >= 4) {
        Column(Modifier.fillMaxSize()) {
            for (r in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0..1) {
                        CoverImage(urls[r * 2 + c], Modifier.weight(1f).fillMaxHeight(), RectangleShape)
                    }
                }
            }
        }
    } else {
        // One cover, or none: CoverImage itself draws the logo letter when there is no address
        CoverImage(urls.firstOrNull(), Modifier.fillMaxSize(), RectangleShape)
    }
}
