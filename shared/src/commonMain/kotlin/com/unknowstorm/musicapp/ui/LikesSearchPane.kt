package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.launch

/** Search within your own likes: a local filter, no network requests. */
@Composable
internal fun LikesSearchPane(
    likes: LikesRepository,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    query: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val liked by likes.tracks.collectAsState()
    val signedIn by likes.signedIn.collectAsState()
    val playback by audio.state.collectAsState()
    val q = query.trim()
    val shown = liked
        .filter { it.provider != ProviderId.DEEZER && !DeadTracks.isDead(it) }
        .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }

    when {
        !signedIn -> Text(
            S.signInToSearchYour,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        shown.isEmpty() -> Text(
            if (q.isEmpty()) S.noLikesYetTapIn else S.nothingFoundInYourLikes,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        else -> LazyColumn(modifier) {
            items(shown, key = { "${it.provider}:${it.id}" }) { track ->
                ListItem(
                    leadingContent = { TrackCover(track, Modifier.size(52.dp), badge = true, badgeSize = 18.dp) },
                    headlineContent = { Text(track.title, maxLines = 1) },
                    supportingContent = { Text(track.artist, maxLines = 1) },
                    trailingContent = {
                        val s = track.durationMs / 1000
                        if (s > 0) Text("${s / 60}:${(s % 60).toString().padStart(2, '0')}")
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (playback.mediaId == track.id && playback.provider == track.provider) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier.clickable {
                        scope.launch { playLiked(shown, track, providers, audio) }
                    },
                )
            }
        }
    }
}
