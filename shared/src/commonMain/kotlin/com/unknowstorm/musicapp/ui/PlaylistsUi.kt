package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider

/** Играет плейлист с первого трека (ссылки на звук берутся заново, как у лайков). */
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
    initialName: String = "Моя очередь",
    title: String = "Сохранить как плейлист",
    confirmLabel: String = "Сохранить",
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
                    label = { Text("Название") },
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
                Text(if (saving) "Сохраняю…" else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Отмена") }
        },
    )
}
