package com.unknowstorm.musicapp.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistEntry
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.launch

/** Экран плейлиста поверх Главной: состав, «Слушать всё», удаление треков, переименование и удаление. */
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
    val name = all.firstOrNull { it.id == playlist.id }?.name ?: playlist.name
    var entries by remember(playlist.id) { mutableStateOf<List<PlaylistEntry>?>(null) }
    var failed by remember(playlist.id) { mutableStateOf(false) }
    var message by remember(playlist.id) { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }

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
            title = "Переименовать плейлист",
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить плейлист?") },
            text = { Text("«$name» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(playlist.id)
                    showDelete = false
                    onBack()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } },
        )
    }

    // Фон и перехват касаний: Главная под экраном не должна быть видна и нажиматься
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDelete = true }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }

            val list = entries
            if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())

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
                ) { Text("Слушать всё") }
                TextButton(onClick = { showRename = true }) { Text("Переименовать") }
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
                    "Не удалось загрузить плейлист",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                list != null && list.isEmpty() -> Text(
                    "В плейлисте нет треков.",
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
                                            message = "Не удалось убрать трек"
                                        }
                                    }
                                }) {
                                    Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
