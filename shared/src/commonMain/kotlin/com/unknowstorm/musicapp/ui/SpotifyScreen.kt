package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.spotify.LibraryStatus
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.spotify.SpotifyItem
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState

/**
 * The Spotify page on Home: a plain header without the other services' logos (brand rules) and rows of selections.
 * Spotify rows take a whole line and are not mixed with other sources. Launching by link lives on the "Search" tab.
 */
@Composable
fun SpotifyScreen(
    remote: SpotifyRemote?,
    onServiceChange: (Service) -> Unit,
    onOpenLikes: () -> Unit,
    onBack: () -> Unit,
    onBeforePlay: () -> Unit,
) {
    val ui = LocalSpotifyMini.current
    val s = remote?.state?.collectAsState()?.value
    PlatformBackHandler(
        enabled = !ui.expanded,
        onBack = {
            when {
                s?.library?.openPlaylist != null -> remote.closeLibraryPlaylist()
                s?.library?.savedOpen == true -> remote.closeSavedTracks()
                LibraryPanels.spotify -> LibraryPanels.spotify = false
                s?.browse != null -> remote.closeBrowse()
                else -> onBack()
            }
        },
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Spotify rules: its brand does not stand next to the brands of other services, so there is no bar with their logos here
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                when {
                    s?.library?.openPlaylist != null -> remote.closeLibraryPlaylist()
                    s?.library?.savedOpen == true -> remote.closeSavedTracks()
                    LibraryPanels.spotify -> LibraryPanels.spotify = false
                    s?.browse != null -> remote.closeBrowse()
                    else -> onBack()
                }
            }) { Text(S.back) }
        }
        if (remote == null || s == null) {
            StatusColumn {
                SpotifyHeader()
                Text(S.theSpotifySectionIsAvailable, Modifier.padding(horizontal = 16.dp))
            }
            return@Column
        }
        when {
            !s.appInstalled -> StatusColumn {
                SpotifyHeader()
                Text(
                    S.thisSectionNeedsTheSpotify,
                    Modifier.padding(horizontal = 16.dp),
                )
                Button(onClick = remote::getSpotifyApp, modifier = Modifier.padding(horizontal = 16.dp)) { Text("GET SPOTIFY FREE") }
            }
            !s.connected -> StatusColumn {
                SpotifyHeader()
                Text(
                    S.theSpotifyAppPlaysThe +
                        S.solaraDoesNotStoreAnything,
                    Modifier.padding(horizontal = 16.dp),
                )
                s.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = remote::connect,
                    enabled = !s.connecting,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(if (s.connecting) S.connecting else S.connectSpotify) }
            }
            s.premium == false -> StatusColumn {
                SpotifyHeader()
                Text(S.playbackControlIsAvailableOnly, Modifier.padding(horizontal = 16.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotifyOpenButton(remote::openSpotifyApp)
                    TextButton(onClick = remote::disconnect) { Text(S.disconnectSpotify) }
                }
            }
            else -> SpotifyHome(remote, s, onBeforePlay)
        }
    }
}

@Composable
private fun ColumnScope.StatusColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The full Spotify logo (content attribution) and an optional hint. */
@Composable
private fun SpotifyHeader(hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpotifyLogoBar()
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpotifySectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SpotifyRowLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SpotifyHome(remote: SpotifyRemote, s: SpotifyState, onBeforePlay: () -> Unit) {
    val ui = LocalSpotifyMini.current
    LaunchedEffect(Unit) { if (s.shelves == null) remote.loadRecommended() }
    LaunchedEffect(Unit) { remote.autoImportLibrary() }

    val play: (SpotifyItem) -> Unit = { item ->
        onBeforePlay(); ui.dismissedUri = null; remote.playUri(item.uri)
    }
    // The album or playlist opened from a shelf: tracks inside it play in its context
    val browseContext = s.browseUri
    // The card of the opened collection (its cover is shown above the track list)
    val browseHeader = s.browseHeader
    val tap: (SpotifyItem) -> Unit = { item ->
        val isCollection = item.uri.startsWith("spotify:album:") || item.uri.startsWith("spotify:playlist:")
        if (item.playable && !isCollection) {
            val c = browseContext
            val index = s.browse?.indexOfFirst { it.uri == item.uri } ?: -1
            if (c != null && index >= 0 && (c.startsWith("spotify:album:") || c.startsWith("spotify:playlist:"))) {
                onBeforePlay(); ui.dismissedUri = null; remote.playInContext(c, index, item)
            } else {
                play(item)
            }
        } else {
            remote.openItem(item)
        }
    }
    val pad = PaddingValues(horizontal = 16.dp)

    // Kept outside the branch below so the scroll position of Home survives opening and closing a list
    val homeListState = rememberLazyListState()
    val openContext = browseContext
    if (s.browse != null && openContext != null &&
        (openContext.startsWith("spotify:album:") || openContext.startsWith("spotify:playlist:"))
    ) {
        // Album or playlist: the same list as on the Search tab, with the cover of the collection
        SpotifyTrackList(
            remote = remote,
            s = s,
            contextUri = openContext,
            onBeforePlay = onBeforePlay,
            onClose = remote::closeBrowse,
            header = browseHeader,
            showBack = false,
        )
        return
    }

    s.library.openPlaylist?.let { pl ->
        SpotifyLibraryPlaylistView(remote, s, pl, onBeforePlay)
        return
    }

    // The personal library is its own sub-screen: the card at the top of Home opens it, "Back" closes it
    if (LibraryPanels.spotify && !s.library.savedOpen && s.library.openPlaylist == null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            spotifyLibraryItems(remote, s, onBeforePlay)
        }
        return
    }

    if (s.library.savedOpen) {
        SpotifySavedTracksView(remote, s, onBeforePlay)
        return
    }

    LazyColumn(Modifier.fillMaxSize(), state = homeListState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") {
            SpotifyHeader(S.youCanPlayMusicFrom)
        }

        s.error?.let { message ->
            item(key = "error") {
                Text(message, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
            }
        }

        if (s.browse == null) item(key = "library-entry") {
            val lib = s.library
            val subtitle = when (lib.status) {
                LibraryStatus.READY -> {
                    val n = lib.savedTotal ?: lib.saved.size
                    listOfNotNull(lib.accountName, tr("$n liked tracks", "$n улюблених треків")).joinToString(" · ")
                }
                LibraryStatus.LOADING, LibraryStatus.AUTHORIZING -> tr("Loading…", "Завантаження…")
                else -> tr("Playlists and liked tracks", "Плейлисти та улюблені треки")
            }
            LibraryEntryCard(
                title = tr("Your Spotify library", "Ваша бібліотека Spotify"),
                subtitle = subtitle,
                onClick = { LibraryPanels.spotify = true },
            )
        }

        val browse = s.browse
        if (browse != null) {
            // An internal selection is open: show it instead of the rows
            item(key = "browse-head") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    browseHeader?.let { h ->
                        SpotifyCover(h.imageKey?.let { s.covers[it] }, 64.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(s.browseTitle ?: S.collection, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = remote::closeBrowse) { Text(S.backToCollections) }
                }
            }
            item(key = "browse-row") {
                SpotifyTileRow(browse, s.covers, contentPadding = pad, onOpenSpotify = remote::openSpotifyApp, onClick = tap)
            }
        } else {
            val recent = s.history
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    Column {
                        SpotifySectionTitle(S.spotifyRecent)
                        SpotifyTileRow(recent, s.covers, contentPadding = pad, onOpenSpotify = remote::openSpotifyApp, onClick = tap)
                    }
                }
            }

            item(key = "shelves-title") { SpotifySectionTitle(S.picksForYou) }
            val shelves = s.shelves
            when {
                shelves == null -> item(key = "loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
                shelves.isEmpty() -> item(key = "empty") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(S.spotifyReturnedNoCollectionsOpen)
                        TextButton(onClick = remote::loadRecommended) { Text(S.retry) }
                    }
                }
                else -> shelves.forEachIndexed { i, shelf ->
                    item(key = "shelf:$i") {
                        Column {
                            SpotifyRowLabel(shelf.title)
                            SpotifyTileRow(shelf.items, s.covers, contentPadding = pad, onOpenSpotify = remote::openSpotifyApp, onClick = tap)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpotifyOpenButton(remote::openSpotifyApp)
                TextButton(onClick = remote::disconnect) { Text(S.disconnectSpotify) }
            }
        }
    }
}
