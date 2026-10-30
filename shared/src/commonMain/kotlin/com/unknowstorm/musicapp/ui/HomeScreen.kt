package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class FeedSection(val title: String, val tracks: List<Track>)

// Пока «подборки для вас» это поиск по жанрам Jamendo
private val MIXES = listOf(
    "Для расслабления" to "chillout",
    "Электроника" to "electronic",
    "Рок" to "rock",
    "Поп" to "pop",
)

/** Лента главного экрана. Живёт на уровне MainScreen, чтобы не грузиться заново при смене вкладок. */
class HomeFeed(private val provider: MusicProvider) {
    var sections by mutableStateOf<List<FeedSection>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun loadIfNeeded() {
        if (sections.isEmpty() && !loading) load()
    }

    suspend fun load() {
        loading = true
        error = null
        try {
            sections = coroutineScope {
                MIXES.map { (title, query) ->
                    async { FeedSection(title, provider.search(query).take(12)) }
                }.awaitAll()
            }.filter { it.tracks.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: feed failed: ${e.stackTraceToString()}")
            error = "Ошибка загрузки: ${e.message}"
        } finally {
            loading = false
        }
    }
}

@Composable
fun HomeScreen(
    feed: HomeFeed,
    service: Service,
    onServiceChange: (Service) -> Unit,
    audio: AudioBackend,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { feed.loadIfNeeded() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ServiceTray(service, onServiceChange, Modifier.padding(vertical = 8.dp))

        if (!service.ready) {
            HintCard("${service.title} скоро появится. ${service.note}.")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { SectionTitle("Вы лайкнули") }
            item { HintCard("Здесь появятся ваши лайки. Они заработают после входа в аккаунт.") }

            item { SectionTitle("Подборки для вас") }
            if (feed.loading) {
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }
            feed.error?.let { message ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { feed.load() } }) { Text("Повторить") }
                    }
                }
            }
            feed.sections.forEach { section ->
                item(key = "label:${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                item(key = "row:${section.title}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.tracks, key = { "${it.provider}:${it.id}" }) { track ->
                            TrackCard(track) {
                                audio.playQueue(section.tracks, section.tracks.indexOf(track))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            item { SectionTitle("Ваши плейлисты") }
            item { HintCard("Плейлисты появятся вместе с аккаунтом. Это следующий этап.") }
        }
    }
}

@Composable
private fun ServiceTray(selected: Service, onSelect: (Service) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Service.entries.toList(), key = { it.name }) { service ->
            FilterChip(
                selected = service == selected,
                onClick = { onSelect(service) },
                label = { Text(if (service.ready) service.title else "${service.title} · скоро") },
            )
        }
    }
}

@Composable
private fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        CoverImage(track.artworkUrl, Modifier.size(132.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun HintCard(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}
