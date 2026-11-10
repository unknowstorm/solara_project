package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.QueueItem
import com.unknowstorm.musicapp.player.RepeatMode
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Вкладка «Очередь»: то, что сейчас стоит в нашем плеере (Jamendo, Audius). */
@Composable
fun QueueScreen(audio: AudioBackend, playlists: PlaylistsRepository, onOpenAuth: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by audio.state.collectAsState()
    val queue by audio.queue.collectAsState()
    val signedIn by playlists.signedIn.collectAsState()
    val myPlaylists by playlists.playlists.collectAsState()
    var addTarget by remember { mutableStateOf<Track?>(null) }
    var newForTrack by remember { mutableStateOf<Track?>(null) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(note) {
        if (note != null) {
            delay(3_000)
            note = null
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            delay(3_000)
            saved = false
        }
    }

    addTarget?.let { target ->
        AddToPlaylistDialog(
            playlists = myPlaylists,
            busy = addBusy,
            error = addError,
            onPick = { pl ->
                scope.launch {
                    addBusy = true
                    addError = null
                    playlists.addTracks(pl.id, listOf(target)).fold(
                        onSuccess = { n ->
                            addTarget = null
                            note = if (n > 0) S.addedTo(pl.name) else S.alreadyIn(pl.name)
                        },
                        onFailure = { addError = it.message },
                    )
                    addBusy = false
                }
            },
            onNew = { newForTrack = target; addTarget = null },
            onDismiss = { addTarget = null; addError = null },
        )
    }
    newForTrack?.let { target ->
        SavePlaylistDialog(
            saving = addBusy,
            error = addError,
            onSave = { name ->
                scope.launch {
                    addBusy = true
                    addError = null
                    playlists.create(name, listOf(target)).fold(
                        onSuccess = { newForTrack = null; note = S.playlistCreated(name.trim()) },
                        onFailure = { addError = it.message },
                    )
                    addBusy = false
                }
            },
            onDismiss = { newForTrack = null; addError = null },
            initialName = target.title.take(60),
        )
    }

    if (showSave) {
        SavePlaylistDialog(
            saving = saving,
            error = saveError,
            onSave = { name ->
                val tracks = queue.mapNotNull { item ->
                    item.provider?.let { Track(item.mediaId, it, item.title, item.artist, item.artworkUrl, 0L) }
                }
                scope.launch {
                    saving = true
                    saveError = null
                    playlists.create(name, tracks).fold(
                        onSuccess = { showSave = false; saved = true },
                        onFailure = { saveError = it.message },
                    )
                    saving = false
                }
            },
            onDismiss = { showSave = false; saveError = null },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.queue, style = MaterialTheme.typography.headlineMedium)
            if (queue.isNotEmpty()) {
                Row {
                    TextButton(onClick = { if (signedIn) showSave = true else onOpenAuth() }) { Text(S.save) }
                    TextButton(onClick = { audio.stop() }) { Text(S.clear) }
                }
            }
        }

        if (saved) {
            Text(
                S.playlistSavedItWillAppear,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        if (queue.isEmpty()) {
            Text(
                S.theQueueIsEmptyPlay,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.shuffle,
                onClick = { audio.toggleShuffle() },
                label = { Text(S.shuffle) },
            )
            FilterChip(
                selected = state.repeatMode != RepeatMode.OFF,
                onClick = { audio.cycleRepeat() },
                label = {
                    Text(
                        when (state.repeatMode) {
                            RepeatMode.OFF -> S.repeatOff
                            RepeatMode.ALL -> S.repeatList
                            RepeatMode.ONE -> S.repeatTrack
                        }
                    )
                },
            )
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(queue, key = { i, item -> "$i:${item.mediaId}" }) { index, item ->
                val current = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { audio.playAt(index) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                        CoverImage(item.artworkUrl, Modifier.fillMaxSize())
                        ServiceBadge(item.provider?.toService(), Modifier.align(Alignment.BottomEnd).padding(3.dp), 18.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.provider == ProviderId.DEEZER) S.s30SPreview(item.artist) else item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.provider == ProviderId.JAMENDO || item.provider == ProviderId.AUDIUS) {
                        IconButton(onClick = { if (signedIn) addTarget = item.toTrack() else onOpenAuth() }) {
                            Text(
                                "+",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { audio.removeAt(index) }) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun QueueItem.toTrack(): Track? =
    provider?.let { Track(mediaId, it, title, artist, artworkUrl, 0L) }
