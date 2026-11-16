package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState
import kotlinx.coroutines.delay

/** Caption under the link field: what the pasted link points to. Worked out from the link only. */
internal fun spotifyKindLabel(uri: String): String = when {
    uri.startsWith("spotify:track:") -> S.spotifyLinkTrack
    uri.startsWith("spotify:album:") -> S.spotifyLinkAlbum
    else -> S.spotifyLinkPlaylist
}

/**
 * Track list of an album or playlist opened by link. While the list loads (or if Spotify returns nothing)
 * there is still a way forward: "Play" and "OPEN SPOTIFY".
 */
@Composable
internal fun SpotifyLinkBrowse(
    remote: SpotifyRemote,
    s: SpotifyState,
    contextUri: String,
    onBeforePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    val ui = LocalSpotifyMini.current
    val list = s.browse
    if (list != null) {
        SpotifyTrackList(remote, s, contextUri, onBeforePlay, onClose, modifier, header = s.browseHeader)
        return
    }
    var timedOut by remember(contextUri) { mutableStateOf(false) }
    LaunchedEffect(contextUri) { delay(8000); timedOut = true }
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(S.back) }
            Spacer(Modifier.weight(1f))
            SpotifyLogoBar()
        }
        if (s.error != null || timedOut) {
            Text(s.error ?: S.spotifyReturnedNoTrackList, color = MaterialTheme.colorScheme.error)
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                onBeforePlay(); ui.dismissedUri = null; remote.playInContext(contextUri, 0, null)
            }) { Text(S.spotifyPlayAll) }
            SpotifyOpenButton({ remote.openInSpotify(contextUri) })
        }
    }
}

/** Vertical list: cover, title, artist. A tap plays from that track inside the album or playlist. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SpotifyTrackList(
    remote: SpotifyRemote,
    s: SpotifyState,
    contextUri: String,
    onBeforePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The album or playlist card: its cover is shown next to the title, unchanged. */
    header: com.unknowstorm.musicapp.spotify.SpotifyItem? = null,
    showBack: Boolean = true,
) {
    val ui = LocalSpotifyMini.current
    val list = s.browse ?: return
    val kind = if (contextUri.startsWith("spotify:album:")) S.album else S.playlist
    // Near the end of the list: ask for the next page (Spotify returns tracks in pages)
    val listState = rememberLazyListState()
    val nearEnd by remember(list.size) {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.size - 5 }
    }
    LaunchedEffect(nearEnd, list.size, s.browseMore) { if (nearEnd && s.browseMore) remote.loadMoreBrowse() }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) TextButton(onClick = onClose) { Text(S.back) }
            Spacer(Modifier.weight(1f))
            SpotifyLogoBar(Modifier.padding(end = 8.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (header != null) {
                    SpotifyCover(header.imageKey?.let { s.covers[it] }, 96.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(s.browseTitle ?: kind, style = MaterialTheme.typography.titleLarge)
                    Text(
                        S.spotifyTracksCount(s.browseTotal ?: if (s.browseMore) "${list.size}+" else list.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Wraps by itself on a narrow screen: "OPEN SPOTIFY" moves to the next line
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onBeforePlay(); ui.dismissedUri = null
                    // With shuffle on, "Play" starts from a random track instead of the first one
                    val start = if (s.shuffling && list.size > 1) list.indices.random() else 0
                    remote.playInContext(contextUri, start, list.getOrNull(start))
                }) { Text(S.spotifyPlayAll) }
                SpotifyShuffleButton(s, remote)
                SpotifyRepeatButton(s, remote)
                SpotifyOpenButton({ remote.openInSpotify(contextUri) })
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            itemsIndexed(list, key = { index, item -> "$index:${item.uri}" }) { index, item ->
                val current = s.uri == item.uri
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (item.playable) {
                                onBeforePlay(); ui.dismissedUri = null; remote.playInContext(contextUri, index, item)
                            } else {
                                remote.openItem(item)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpotifyCover(item.imageKey?.let { s.covers[it] }, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
