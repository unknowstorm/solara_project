package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.launch

/**
 * Contents of the “Liked” tab on Home (under the top source selection bar).
 * Shows likes of the current account only: the server does not return other users' rows (RLS),
 * and on sign-out or account switch the list is cleared.
 */
@Composable
fun LikedContent(
    likes: LikesRepository,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onOpenAuth: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val liked by likes.tracks.collectAsState()
    val signedIn by likes.signedIn.collectAsState()
    val error by likes.error.collectAsState()
    val shown = liked.filterNot { DeadTracks.isDead(it) }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(signedIn) { if (signedIn) likes.refresh() }

    Column(Modifier.fillMaxSize()) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        when {
            !signedIn -> Message(S.signInToSaveYour) {
                Button(onClick = onOpenAuth) { Text(S.signIn) }
            }
            shown.isEmpty() -> Message(S.yourFavoritesWillAppearHere)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            S.tracks(shown.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { scope.launch { playLiked(shown, shown.first(), providers, audio) } }) {
                            Text(S.playAll)
                        }
                    }
                }
                items(shown, key = { "${it.provider}:${it.id}" }) { track ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { playLiked(shown, track, providers, audio) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackCover(track, Modifier.size(56.dp), badge = true, badgeSize = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Tapping removes the like, the track immediately disappears from the list
                        IconButton(onClick = { likes.toggle(track) }) {
                            HeartIcon(true, tint = primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: @Composable (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
