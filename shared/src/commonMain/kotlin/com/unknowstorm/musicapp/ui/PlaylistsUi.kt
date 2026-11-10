package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider

/** Plays a playlist from the first track (audio links are fetched again, as with likes). */
internal suspend fun playPlaylist(
    repo: PlaylistsRepository,
    playlist: Playlist,
    providers: List<MusicProvider>,
    audio: AudioBackend,
) {
    val tracks = repo.loadTracks(playlist.id)
    if (tracks.isEmpty()) return
    playLiked(tracks, tracks.first(), providers, audio)
}

@Composable
internal fun SavePlaylistDialog(
    saving: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = S.myQueue,
    title: String = S.saveAsPlaylist,
    confirmLabel: String = S.save,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text(S.name) },
                    singleLine = true,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = !saving && name.isNotBlank()) {
                Text(if (saving) S.saving else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(S.cancel) }
        },
    )
}

/** Choice of a playlist to add the track to, or creating a new one. */
@Composable
internal fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    busy: Boolean,
    error: String?,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(S.addToPlaylist) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TextButton(onClick = onNew, enabled = !busy) { Text(S.newPlaylist) }
                playlists.forEach { pl ->
                    TextButton(
                        onClick = { onPick(pl) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(S.cancel) } },
    )
}
