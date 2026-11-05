package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicHub
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.provider.SourceResult
import com.unknowstorm.musicapp.provider.interleave
import kotlinx.coroutines.delay

@Composable
fun TrackListScreen(
    providers: List<MusicProvider>,
    audio: AudioBackend,
    session: YouTubeSession? = null,
    onOpenSpotify: () -> Unit = {},
    likes: LikesRepository? = null,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(Service.ALL) }
    var likesSelected by remember { mutableStateOf(false) }
    var ytQuery by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<Map<ProviderId, SourceResult>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    val playback by audio.state.collectAsState()
    val hub = remember(providers) { MusicHub(providers) }

    // ALL ищет во всех подключённых источниках, остальные чипы только в своём
    val sourceId = ProviderId.entries.firstOrNull { it.name == selected.name }
    val canSearch = selected == Service.ALL ||
        (selected.ready && providers.any { it.id == sourceId })

    LaunchedEffect(query, selected, retry, hub, likesSelected) {
        if (!canSearch || likesSelected) {
            results = emptyMap()
            loading = false
            return@LaunchedEffect
        }
        delay(400)
        loading = true
        val fresh = mutableMapOf<ProviderId, SourceResult>()
        try {
            // «Все» ищет только там, где играют полные треки; превью Deezer живут на своём чипе
            val sources = sourceId?.let { setOf(it) }
                ?: providers.filter { p ->
                    Service.entries.firstOrNull { it.name == p.id.name }?.kind ==
                        com.unknowstorm.musicapp.model.PlaybackKind.IN_APP
                }.map { it.id }.toSet()
            hub.search(query, sources).collect { result ->
                fresh[result.provider] = result
                results = fresh.toMap() // новые треки появляются, как только ответил любой источник
            }
        } finally {
            loading = false
        }
    }

    val visible = interleave(providers.mapNotNull { results[it.id]?.tracks })
        .filterNot { DeadTracks.isDead(it) }
    val failed = providers.mapNotNull { p -> results[p.id]?.takeIf { it.error != null } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(
            selected,
            { selected = it; likesSelected = false },
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            likesSelected = likesSelected,
            onLikes = if (likes != null) ({ likesSelected = true; selected = Service.ALL }) else null,
        )

        if (selected != Service.SPOTIFY) OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { if (selected == Service.YOUTUBE) ytQuery = query.trim() },
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (!canSearch && selected != Service.SPOTIFY && !(selected == Service.YOUTUBE && session != null)) {
            Text(
                text = if (selected == Service.YOUTUBE) "YouTube ищется на своём экране: Главная → YouTube." else "${selected.title} скоро появится. ${selected.note}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Ошибка одного источника не мешает остальным
        failed.forEach { result ->
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${result.provider.toService()?.title ?: result.provider.name}: ${result.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { retry++ }) { Text("Повторить") }
            }
        }

        playback.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (canSearch && !loading && results.isNotEmpty() && visible.isEmpty() && failed.isEmpty()) {
            Text(
                text = "Ничего не найдено",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (likesSelected && likes != null) {
            LikesSearchPane(likes, providers, audio, query, Modifier.weight(1f))
            return@Column
        }

        if (selected == Service.SPOTIFY) {
            // Выдача Spotify отдельная: с другими источниками не смешивается
            SpotifySearchPane(
                remote = com.unknowstorm.musicapp.spotify.LocalSpotifyRemote.current,
                onBeforePlay = { audio.stop(); session?.close() },
                onOpenSection = onOpenSpotify,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        if (selected == Service.YOUTUBE && session != null) {
            // Выдача YouTube отдельная: правила запрещают смешивать её с другими источниками
            YouTubeSearchPane(
                submitted = ytQuery,
                audio = audio,
                session = session,
                onLeave = { selected = Service.ALL },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { "${it.provider}:${it.id}" }) { track ->
                ListItem(
                    leadingContent = {
                        TrackCover(track, Modifier.size(52.dp), badge = true, badgeSize = 18.dp)
                    },
                    headlineContent = { Text(track.title, maxLines = 1) },
                    supportingContent = { Text(track.artist, maxLines = 1) },
                    trailingContent = { Text(formatDuration(track.durationMs)) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (playback.mediaId == track.id && playback.provider == track.provider) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier.clickable {
                        val playable = visible.filter { !it.streamUrl.isNullOrBlank() }
                        val index = playable.indexOfFirst {
                            it.id == track.id && it.provider == track.provider
                        }
                        if (index >= 0) audio.playQueue(playable, index)
                    }
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
