package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.spotify.LibraryStatus
import com.unknowstorm.musicapp.spotify.SpotifyItem
import com.unknowstorm.musicapp.spotify.SpotifyLibPlaylist
import com.unknowstorm.musicapp.spotify.SpotifyLibTrack
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState

private const val SPOTIFY_APPS_URL = "https://www.spotify.com/account/apps/"

private fun tTitle() = tr("Your Spotify library", "Ваша бібліотека Spotify")
private fun tIntro() = tr(
    "Show your playlists and saved tracks here. Read-only access. After the first import it repeats by itself each time you open this section: only an encrypted access key is kept on this device, the library itself is never stored.",
    "Показати тут ваші плейлисти та збережені треки. Лише читання. Після першого імпорту він повторюється сам при кожному відкритті цього розділу: на цьому пристрої зберігається лише зашифрований ключ доступу, сама бібліотека ніколи не зберігається.",
)
private fun tAuto() = tr(
    "Repeats automatically each time you open this section.",
    "Повторюється автоматично при кожному відкритті цього розділу.",
)
private fun tAutoOff() = tr("Turn off", "Вимкнути")
private fun tImport() = tr("Import my Spotify library", "Імпортувати мою бібліотеку Spotify")
private fun tPlaylists() = tr("Your playlists", "Ваші плейлисти")
private fun tSaved(total: Int?) =
    tr("Liked Songs", "Улюблені треки") + (total?.let { " · $it" } ?: "")
private fun tForget() = tr("Remove import and sign out", "Видалити імпорт і вийти")
private fun tRevoke() = tr("Revoke access on Spotify's site", "Відкликати доступ на сайті Spotify")
private fun tOpenInSpotify() = tr("Open in Spotify", "Відкрити в Spotify")
private fun tTracks(n: Int?) = if (n == null) "" else tr("$n tracks", "треків: $n")
private fun tEmpty() = tr("No tracks to show.", "Немає треків для показу.")

/** The library block on the Spotify page: it is only shown there and is never mixed with other sources. */
fun LazyListScope.spotifyLibraryItems(remote: SpotifyRemote, s: SpotifyState, onBeforePlay: () -> Unit) {
    val lib = s.library
    item(key = "lib-title") {
        Text(
            tTitle(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )
    }
    when (lib.status) {
        LibraryStatus.IDLE, LibraryStatus.ERROR -> item(key = "lib-start") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tIntro(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lib.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { remote.importLibrary() }) { Text(tImport()) }
            }
        }
        LibraryStatus.AUTHORIZING, LibraryStatus.LOADING -> item(key = "lib-loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        LibraryStatus.READY -> {
            if (lib.playlists.isNotEmpty()) {
                item(key = "lib-pl-label") { LibLabel(tPlaylists()) }
                item(key = "lib-pl-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(lib.playlists, key = { it.id }) { p ->
                            LibPlaylistTile(p) {
                                // Spotify gives no contents of other people's playlists: open them in Spotify itself
                                if (p.readable) remote.openLibraryPlaylist(p) else remote.openInSpotify(p.uri)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (lib.saved.isNotEmpty()) {
                item(key = "lib-saved-label") { LibLabel(tSaved(lib.savedTotal)) }
                itemsIndexed(lib.saved, key = { i, _ -> "libsaved:$i" }) { _, t ->
                    val ui = LocalSpotifyMini.current
                    LibTrackRow(
                        t,
                        onPlay = { onBeforePlay(); ui.dismissedUri = null; remote.playUri(t.uri) },
                        onOpenSpotify = { remote.openInSpotify(t.uri) },
                    )
                }
            }
            if (lib.autoImport) item(key = "lib-auto") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tAuto(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { remote.disableAutoImport() }) { Text(tAutoOff()) }
                }
            }
            item(key = "lib-actions") {
                val uriHandler = LocalUriHandler.current
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { remote.forgetLibrary() }) { Text(tForget()) }
                    TextButton(onClick = { uriHandler.openUri(SPOTIFY_APPS_URL) }) { Text(tRevoke()) }
                }
            }
        }
    }
}

@Composable
private fun LibLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun LibPlaylistTile(p: SpotifyLibPlaylist, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (p.imageUrl != null) CoverImage(p.imageUrl, Modifier.fillMaxSize(), RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (p.readable) tTracks(p.trackCount) else tOpenInSpotify(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A track row: cover and metadata always come with a link back to the track in Spotify (the icon on the right). */
@Composable
private fun LibTrackRow(t: SpotifyLibTrack, onPlay: () -> Unit, onOpenSpotify: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        leadingContent = {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (t.imageUrl != null) CoverImage(t.imageUrl, Modifier.fillMaxSize(), RoundedCornerShape(4.dp))
            }
        },
        headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onOpenSpotify) {
                Image(
                    spotifyMonoIcon(MaterialTheme.colorScheme.background),
                    contentDescription = tOpenInSpotify(),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** An opened playlist of the user: tracks play in the playlist's own context through the Spotify app. */
@Composable
fun SpotifyLibraryPlaylistView(
    remote: SpotifyRemote,
    s: SpotifyState,
    pl: SpotifyLibPlaylist,
    onBeforePlay: () -> Unit,
) {
    val ui = LocalSpotifyMini.current
    val tracks = s.library.playlistTracks
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "pl-head") {
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (pl.imageUrl != null) CoverImage(pl.imageUrl, Modifier.fillMaxSize(), RoundedCornerShape(4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(pl.owner, tTracks(pl.trackCount).ifBlank { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SpotifyOpenButton({ remote.openInSpotify(pl.uri) })
                }
            }
        }
        s.library.error?.let { message ->
            item(key = "pl-error") {
                Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        when {
            tracks == null -> item(key = "pl-loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            tracks.isEmpty() -> item(key = "pl-empty") {
                Text(tEmpty(), Modifier.padding(16.dp))
            }
            else -> itemsIndexed(tracks, key = { i, _ -> "pltrack:$i" }) { _, t ->
                LibTrackRow(
                    t,
                    onPlay = {
                        onBeforePlay()
                        ui.dismissedUri = null
                        remote.playInContext(pl.uri, t.position, SpotifyItem(t.uri, t.title, t.artist))
                    },
                    onOpenSpotify = { remote.openInSpotify(t.uri) },
                )
            }
        }
    }
}
