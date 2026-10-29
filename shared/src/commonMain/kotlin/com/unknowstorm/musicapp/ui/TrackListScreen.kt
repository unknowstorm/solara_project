package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun TrackListScreen(provider: MusicProvider, audio: AudioBackend, onOpenPlayer: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val playback by audio.state.collectAsState()

    LaunchedEffect(query) {
        delay(400)
        loading = true
        error = null
        try {
            tracks = provider.search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: search failed: ${e.stackTraceToString()}")
            error = "Ошибка загрузки: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск (Jamendo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        (error ?: playback.error)?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (!loading && error == null && tracks.isEmpty()) {
            Text(
                text = "Ничего не найдено",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(tracks, key = { "${it.provider}:${it.id}" }) { track ->
                ListItem(
                    leadingContent = { CoverImage(track.artworkUrl, Modifier.size(52.dp)) },
                    headlineContent = { Text(track.title, maxLines = 1) },
                    supportingContent = { Text(track.artist, maxLines = 1) },
                    trailingContent = { Text(formatDuration(track.durationMs)) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (playback.mediaId == track.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier.clickable {
                        val playable = tracks.filter { !it.streamUrl.isNullOrBlank() }
                        val index = playable.indexOfFirst { it.id == track.id }
                        if (index >= 0) audio.playQueue(playable, index)
                    }
                )
            }
        }

        NowPlayingBar(audio, onOpenPlayer, Modifier.padding(8.dp).navigationBarsPadding())
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
