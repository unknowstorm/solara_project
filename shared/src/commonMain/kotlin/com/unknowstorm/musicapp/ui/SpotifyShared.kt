package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.resources.Res
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import com.unknowstorm.musicapp.resources.spotify_icon_black
import com.unknowstorm.musicapp.resources.spotify_icon_white
import com.unknowstorm.musicapp.resources.spotify_logo_black
import com.unknowstorm.musicapp.resources.spotify_logo_white
import com.unknowstorm.musicapp.spotify.SpotifyItem
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.parseSpotifyLink
import org.jetbrains.compose.resources.painterResource

/** Full Spotify logo on a black background, unmodified. */
@Composable
internal fun SpotifyLogoBar(modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp)) {
        Image(spotifyMonoLogo(MaterialTheme.colorScheme.background), contentDescription = "Spotify", modifier = Modifier.height(24.dp))
    }
}

/** Spotify cover: no cropping, captions, badges or blur, 4 dp corners. */
@Composable
internal fun SpotifyCover(bitmap: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
internal fun SpotifyTile(item: SpotifyItem, covers: Map<String, ImageBitmap>, onClick: () -> Unit) {
    // Spotify does not provide a cover for top-level shelves: we show a neutral tile with the title
    if (item.imageKey.isNullOrBlank()) {
        Column(Modifier.width(120.dp)) {
            Box(
                Modifier.size(120.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onClick).padding(10.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            TileCaption(null, null)
        }
        return
    }
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        SpotifyCover(covers[item.imageKey], 120.dp)
        TileCaption(item.title, item.subtitle)
    }
}

/** Title (2 lines) and subtitle (1 line) with reserved height: every tile and every row has the same height. */
@Composable
private fun TileCaption(title: String?, subtitle: String?) {
    Spacer(Modifier.height(6.dp))
    Text(
        title ?: "",
        style = MaterialTheme.typography.titleSmall,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        subtitle?.takeIf { it.isNotBlank() } ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = 1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Row of Spotify tiles: at most 20 items, with a link to the Spotify app at the end. */
@Composable
internal fun SpotifyTileRow(
    list: List<SpotifyItem>,
    covers: Map<String, ImageBitmap>,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    onOpenSpotify: (() -> Unit)? = null,
    onClick: (SpotifyItem) -> Unit,
) {
    val shown = list.distinctBy { it.uri }.take(20)
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shown.size, key = { shown[it].uri }) { i -> SpotifyTile(shown[i], covers) { onClick(shown[i]) } }
        if (onOpenSpotify != null) {
            item {
                // Last tile of the row: the same size as the covers, 4 dp corners
                Column(Modifier.width(120.dp)) {
                Column(
                    Modifier.size(120.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenSpotify).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(spotifyMonoIcon(MaterialTheme.colorScheme.surfaceVariant), contentDescription = "Spotify", modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "OPEN SPOTIFY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                TileCaption(null, null)
                }
            }
        }
    }
}

// The layout must fit: playlist/album 25 characters, artist 18, track 23.
// Truncation (ellipsis) is allowed only when there is not enough room; we do not alter metadata.

/** Spotify mode on the “Search” tab: launch by link and recent tracks. No other sources here. */
@Composable
fun SpotifySearchPane(
    remote: SpotifyRemote?,
    onBeforePlay: () -> Unit,
    onOpenSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = LocalSpotifyMini.current
    // The album or playlist whose track list is open on this tab
    var linkContext by remember { mutableStateOf<String?>(null) }
    val live = remote?.state?.collectAsState()?.value
    if (remote != null && live != null && linkContext != null) {
        SpotifyLinkBrowse(
            remote = remote,
            s = live,
            contextUri = linkContext!!,
            onBeforePlay = onBeforePlay,
            onClose = { remote.closeBrowse(); linkContext = null },
            modifier = modifier,
        )
        return
    }
    // Leaving the tab with an open list: the list must not appear on the Home page
    DisposableEffect(Unit) { onDispose { if (linkContext != null) remote?.closeBrowse() } }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SpotifyLogoBar() }
        if (remote == null) {
            Text(S.spotifyIsAvailableOnlyIn)
            return@Column
        }
        val s by remote.state.collectAsState()
        if (!s.connected || s.premium == false) {
            Text(
                if (s.premium == false) S.spotifyControlIsAvailableOnly
                else S.spotifyIsNotConnectedConnect,
            )
            Button(onClick = onOpenSection) { Text(S.openTheSpotifySection) }
            return@Column
        }
        var link by remember { mutableStateOf("") }
        var linkError by remember { mutableStateOf(false) }
        Text(
            S.searchByNameIsNot +
                S.inTheSpotifyAppShare,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it; linkError = false },
            label = { Text(S.spotifyLink) },
            isError = linkError,
            supportingText = { if (linkError) Text(S.doesnTLookLikeA) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // What kind of link this is: worked out locally from the link itself, no Spotify request
        parseSpotifyLink(link)?.let {
            Text(spotifyKindLabel(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Button(onClick = {
            val uri = parseSpotifyLink(link)
            when {
                uri == null -> linkError = true
                // One track: play it right away, the mini player shows it
                uri.startsWith("spotify:track:") -> {
                    onBeforePlay()
                    ui.dismissedUri = null
                    remote.playUri(uri)
                    link = ""
                }
                // Album or playlist: show the track list here, playback starts from the list
                else -> {
                    remote.openByUri(uri)
                    linkContext = uri
                    link = ""
                }
            }
        }) { Text(S.spotifyOpenLink) }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (s.history.isNotEmpty()) {
            Text(S.spotifyRecent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            SpotifyTileRow(s.history, s.covers) { item ->
                if (item.uri.startsWith("spotify:album:") || item.uri.startsWith("spotify:playlist:")) {
                    remote.openItem(item)
                    linkContext = item.uri
                } else {
                    onBeforePlay(); ui.dismissedUri = null; remote.playUri(item.uri)
                }
            }
        }
        TextButton(onClick = onOpenSection) { Text(S.openTheSpotifySection) }
    }
}


/** Monochrome Spotify icon: white on a dark background, black on a light one. Green only on black or white. */
@Composable
internal fun spotifyMonoIcon(background: Color): Painter =
    painterResource(if (background.luminance() < 0.179f) Res.drawable.spotify_icon_white else Res.drawable.spotify_icon_black)

/** “OPEN SPOTIFY” button with an icon, as in the guideline examples. */
@Composable
internal fun SpotifyOpenButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Image(spotifyMonoIcon(MaterialTheme.colorScheme.background), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("OPEN SPOTIFY")
    }
}

/** Monochrome full Spotify logo: white on a dark background, black on a light one. */
@Composable
internal fun spotifyMonoLogo(background: Color): Painter =
    painterResource(if (background.luminance() < 0.179f) Res.drawable.spotify_logo_white else Res.drawable.spotify_logo_black)
