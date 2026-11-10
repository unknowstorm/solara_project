package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.PlaybackKind
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicHub
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.provider.SourceResult
import com.unknowstorm.musicapp.provider.interleave
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var pagerPos by remember {
        mutableFloatStateOf(pagerOrder(likes != null).indexOf(if (likesSelected) null else selected).coerceAtLeast(0).toFloat())
    }
    // Результаты по страницам пейджера: соседние источники грузятся заранее
    val cache = remember(providers) { mutableStateMapOf<Service, Map<ProviderId, SourceResult>>() }
    val loadingFor = remember(providers) { mutableStateMapOf<Service, Boolean>() }
    val loadedFor = remember(providers) { mutableMapOf<Service, Pair<String, Int>>() }
    val playback by audio.state.collectAsState()
    val hub = remember(providers) { MusicHub(providers) }

    fun searchable(s: Service): Boolean = when (s) {
        Service.ALL -> true
        Service.YOUTUBE, Service.SPOTIFY -> false
        else -> s.ready && providers.any { it.id.name == s.name }
    }

    LaunchedEffect(query, selected, likesSelected, retry, hub) {
        if (selected == Service.SPOTIFY) return@LaunchedEffect
        val order = pagerOrder(likes != null)
        val here = if (likesSelected) 0 else order.indexOf(selected)
        val targets = listOf(here, here - 1, here + 1)
            .mapNotNull { order.getOrNull(it) }
            .filter { searchable(it) && loadedFor[it] != (query to retry) }
        if (targets.isEmpty()) return@LaunchedEffect
        delay(400)
        coroutineScope {
            targets.forEach { svc ->
                launch {
                    loadingFor[svc] = true
                    val fresh = mutableMapOf<ProviderId, SourceResult>()
                    try {
                        val sourceId = ProviderId.entries.firstOrNull { it.name == svc.name }
                        // «Все» ищет только там, где играют полные треки; превью Deezer живут на своём чипе
                        val sources = sourceId?.let { setOf(it) }
                            ?: providers.filter { p ->
                                Service.entries.firstOrNull { it.name == p.id.name }?.kind == PlaybackKind.IN_APP
                            }.map { it.id }.toSet()
                        hub.search(query, sources).collect { result ->
                            fresh[result.provider] = result
                            cache[svc] = fresh.toMap()
                        }
                        loadedFor[svc] = query to retry
                    } finally {
                        loadingFor[svc] = false
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selected == Service.SPOTIFY) {
            // Правила Spotify: его бренд не соседствует с брендами других сервисов
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { selected = Service.ALL }) { Text("← Назад") }
            }
        } else {
            TopBar(
                selected,
                { selected = it; likesSelected = false },
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                likesSelected = likesSelected,
                onLikes = if (likes != null) ({ likesSelected = true; selected = Service.ALL }) else null,
                position = { pagerPos },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(S.search) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (selected == Service.YOUTUBE) ytQuery = query.trim() },
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        playback.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (selected == Service.SPOTIFY) {
            // Выдача Spotify отдельная: с другими источниками не смешивается
            SpotifySearchPane(
                remote = com.unknowstorm.musicapp.spotify.LocalSpotifyRemote.current,
                onBeforePlay = { audio.stop(); session?.close() },
                onOpenSection = onOpenSpotify,
                modifier = Modifier.weight(1f),
            )
        } else {
            ServicePager(
                current = selected,
                likesSelected = likesSelected,
                hasLikes = likes != null,
                swipeEnabled = true,
                onSelect = { selected = it; likesSelected = false },
                onLikes = { likesSelected = true; selected = Service.ALL },
                onPosition = { pagerPos = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { svc, active ->
                when {
                    svc == null -> if (likes != null) {
                        LikesSearchPane(likes, providers, audio, query, Modifier.fillMaxSize())
                    }
                    svc == Service.YOUTUBE && session != null -> {
                        // Выдача YouTube отдельная: правила запрещают смешивать её с другими источниками
                        YouTubeSearchPane(
                            submitted = ytQuery,
                            audio = audio,
                            session = session,
                            onLeave = { selected = Service.ALL; likesSelected = false },
                            active = active,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    searchable(svc) -> SearchResultsPage(
                        results = cache[svc],
                        loading = loadingFor[svc] == true,
                        providers = providers,
                        audio = audio,
                        onRetry = { retry++ },
                    )
                    else -> Text(
                        text = if (svc == Service.YOUTUBE) "YouTube ищется на своём экране: Главная → YouTube."
                        else "${svc.title} скоро появится. ${svc.note}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsPage(
    results: Map<ProviderId, SourceResult>?,
    loading: Boolean,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onRetry: () -> Unit,
) {
    val playback by audio.state.collectAsState()
    val visible = interleave(providers.mapNotNull { results?.get(it.id)?.tracks })
        .filterNot { DeadTracks.isDead(it) }
    val failed = providers.mapNotNull { p -> results?.get(p.id)?.takeIf { it.error != null } }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

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
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
        }

        if (!loading && results != null && results.isNotEmpty() && visible.isEmpty() && failed.isEmpty()) {
            Text(
                text = "Ничего не найдено",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
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
