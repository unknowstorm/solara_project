package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class FeedSection(val title: String, val tracks: List<Track>)

/** Подборки источника: заголовок и поисковый запрос (пустой запрос = популярное). */
fun defaultMixes(service: Service): List<Pair<String, String>> = when (service) {
    Service.JAMENDO -> listOf(
        "Популярное" to "",
        "Для расслабления" to "chillout",
        "Электроника" to "electronic",
        "Рок" to "rock",
        "Поп" to "pop",
    )
    Service.AUDIUS -> listOf(
        "Популярное" to "",
        "Электроника" to "electronic",
        "Рок" to "rock",
        "Хип-хоп" to "hip hop",
        "Лоу-фай" to "lofi",
    )
    else -> emptyList()
}

/** Лента одного источника. Живёт на уровне MainScreen, чтобы не грузиться заново при смене вкладок. */
class HomeFeed(
    val service: Service,
    private val provider: MusicProvider,
    private val mixes: List<Pair<String, String>>,
) {
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
            var lastError: String? = null
            val loaded = coroutineScope {
                mixes.map { (title, query) ->
                    async {
                        try {
                            FeedSection(title, provider.search(query).take(12))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            println("Solara: feed ${service.title}/$title failed: ${e.stackTraceToString()}")
                            lastError = e.message
                            null
                        }
                    }
                }.awaitAll()
            }.filterNotNull().filter { it.tracks.isNotEmpty() }
            sections = loaded
            if (loaded.isEmpty() && lastError != null) error = "Ошибка загрузки: $lastError"
        } finally {
            loading = false
        }
    }
}

private data class FeedRow(val key: String, val title: String, val service: Service?, val tracks: List<Track>)

private fun <T> roundRobin(lists: List<List<T>>): List<T> {
    val result = mutableListOf<T>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) {
        for (list in lists) list.getOrNull(i)?.let(result::add)
    }
    return result
}

/** Собирает ряды главной. На вкладке «Все» источники чередуются, а сверху идёт общий микс. */
private fun buildRows(feeds: List<HomeFeed>, mixed: Boolean): List<FeedRow> {
    val perFeed = feeds.map { feed ->
        feed.sections.map { FeedRow("${feed.service.name}:${it.title}", it.title, feed.service, it.tracks.filterNot { t -> DeadTracks.isDead(t) }) }
    }
    val rows = roundRobin(perFeed)
    if (!mixed) return rows
    val firstRows = perFeed.mapNotNull { it.firstOrNull() }
    if (firstRows.size < 2) return rows
    val mix = roundRobin(firstRows.map { it.tracks }).take(16)
    return listOf(FeedRow("mix", "Микс из всех источников", Service.ALL, mix)) + rows
}

@Composable
fun HomeScreen(
    feeds: List<HomeFeed>,
    service: Service,
    onServiceChange: (Service) -> Unit,
    audio: AudioBackend,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(feeds) { feeds.forEach { feed -> launch { feed.loadIfNeeded() } } }
    val mixed = service == Service.ALL
    val rows = buildRows(feeds, mixed)

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
            if (feeds.any { it.loading }) {
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }
            feeds.forEach { feed ->
                feed.error?.let { message ->
                    item(key = "error:${feed.service.name}") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text("${feed.service.title}: $message", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { scope.launch { feed.load() } }) { Text("Повторить") }
                        }
                    }
                }
            }
            rows.forEach { row ->
                item(key = "label:${row.key}") { RowLabel(row, mixed) }
                item(key = "row:${row.key}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(row.tracks, key = { "${it.provider}:${it.id}" }) { track ->
                            TrackCard(track, showBadge = mixed) {
                                audio.playQueue(row.tracks, row.tracks.indexOf(track))
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
private fun RowLabel(row: FeedRow, withService: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withService && row.service != null) {
            ServiceLogo(row.service, Modifier.size(16.dp), color)
            Spacer(Modifier.width(6.dp))
        }
        val prefix = row.service
            ?.takeIf { withService && it != Service.ALL }
            ?.let { "${it.title} · " }
            ?: ""
        Text(prefix + row.title, style = MaterialTheme.typography.labelLarge, color = color)
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
                label = {
                    ServiceLogo(
                        service,
                        Modifier.padding(horizontal = 4.dp).size(22.dp),
                        LocalContentColor.current.copy(alpha = if (service.ready) 1f else 0.45f),
                    )
                },
                modifier = Modifier.semantics { contentDescription = service.title },
            )
        }
    }
}

@Composable
private fun TrackCard(track: Track, showBadge: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        TrackCover(track, Modifier.size(132.dp), badge = showBadge, badgeSize = 24.dp)
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
