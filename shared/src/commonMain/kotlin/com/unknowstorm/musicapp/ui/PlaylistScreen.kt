package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistEntry
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.launch

/** A playlist screen above Home: contents, "Play all", deleting tracks, renaming and deleting. */
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    repo: PlaylistsRepository,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val all by repo.playlists.collectAsState()
    val current = all.firstOrNull { it.id == playlist.id } ?: playlist
    val name = current.name
    var entries by remember(playlist.id) { mutableStateOf<List<PlaylistEntry>?>(null) }
    var failed by remember(playlist.id) { mutableStateOf(false) }
    var message by remember(playlist.id) { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showCoverMenu by remember { mutableStateOf(false) }
    var coverBusy by remember { mutableStateOf(false) }
    // The same picker as for avatars: the photo arrives already cropped to a square JPEG
    val pickCover = rememberAvatarPicker { jpeg ->
        scope.launch {
            coverBusy = true
            message = null
            repo.setCover(playlist.id, jpeg).onFailure { message = it.message }
            coverBusy = false
        }
    }

    PlatformBackHandler(onBack = onBack)

    LaunchedEffect(playlist.id) {
        val loaded = repo.loadEntries(playlist.id)
        failed = loaded == null
        entries = loaded ?: emptyList()
    }

    if (showRename) {
        SavePlaylistDialog(
            saving = renaming,
            error = renameError,
            onSave = { newName ->
                scope.launch {
                    renaming = true
                    renameError = null
                    repo.rename(playlist.id, newName).fold(
                        onSuccess = { showRename = false },
                        onFailure = { renameError = it.message },
                    )
                    renaming = false
                }
            },
            onDismiss = { showRename = false; renameError = null },
            initialName = name,
            title = S.renamePlaylist,
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(S.deletePlaylist) },
            text = { Text(S.willBeDeletedPermanently(name)) },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(playlist.id)
                    showDelete = false
                    onBack()
                }) { Text(S.delete) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(S.cancel) } },
        )
    }

    // Background and touch interception: Home under the screen must not be visible or clickable
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val list = entries

            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { BackArrowIcon(Modifier.size(24.dp)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showDelete = true }) {
                    Text(S.delete, color = MaterialTheme.colorScheme.error)
                }
            }

            if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())

            // Big cover (own photo or a collage of tracks) next to the name; tap it to change
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    PlaylistCover(
                        repo = repo,
                        playlist = current,
                        modifier = Modifier
                            .size(120.dp)
                            .clickable(enabled = !coverBusy) { showCoverMenu = true },
                        shape = RoundedCornerShape(20.dp),
                        artworks = list?.map { it.track.artworkUrl },
                    )
                    if (coverBusy) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✎", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    DropdownMenu(expanded = showCoverMenu, onDismissRequest = { showCoverMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(PlaylistCoverText.change) },
                            onClick = { showCoverMenu = false; pickCover() },
                        )
                        if (current.cover != null) {
                            DropdownMenuItem(
                                text = { Text(PlaylistCoverText.remove) },
                                onClick = {
                                    showCoverMenu = false
                                    scope.launch {
                                        coverBusy = true
                                        message = null
                                        repo.removeCover(playlist.id).onFailure { message = it.message }
                                        coverBusy = false
                                    }
                                },
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (list != null) {
                        Text(
                            "♪ ${list.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val tracks = list.orEmpty().map { it.track }
                        if (tracks.isNotEmpty()) scope.launch { playLiked(tracks, tracks.first(), providers, audio) }
                    },
                    enabled = !list.isNullOrEmpty(),
                ) { Text(S.playAll) }
                TextButton(onClick = { showRename = true }) { Text(S.rename) }
            }

            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            when {
                failed -> Text(
                    S.couldNotLoadThePlaylist,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                list != null && list.isEmpty() -> Text(
                    S.thereAreNoTracksIn,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                list != null -> {
                    val tracks = list.map { it.track }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 4.dp, bottom = 16.dp),
                    ) {
                        items(list, key = { it.position }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { playLiked(tracks, entry.track, providers, audio) } }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TrackCover(entry.track, Modifier.size(52.dp), badge = true, badgeSize = 18.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        entry.track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        if (repo.removeEntry(playlist.id, entry.position)) {
                                            message = null
                                            entries = entries?.filterNot { it.position == entry.position }
                                        } else {
                                            message = S.couldNotRemoveTheTrack
                                        }
                                    }
                                }) {
                                    CrossIcon(Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
