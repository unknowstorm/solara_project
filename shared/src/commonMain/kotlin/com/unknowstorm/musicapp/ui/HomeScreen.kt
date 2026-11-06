package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.recommendations.RecommendationsFeed
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// --- ВАШИ СУЩЕСТВУЮЩИЕ КЛАССЫ И ФУНКЦИИ (БЕЗ ИЗМЕНЕНИЙ) ---

data class FeedSection(val title: String, val tracks: List<Track>)

/** Запрос подборки с повторами: на холодном старте сеть часто ещё не готова (DNS, таймаут). */
private suspend fun searchWithRetry(provider: MusicProvider, query: String): List<Track> {
    var last: Exception? = null
    repeat(3) { attempt ->
        try {
            return provider.search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            last = e
            delay(800L * (attempt + 1))
        }
    }
    throw last ?: IllegalStateException("search failed")
}

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
    Service.DEEZER -> listOf(
        "Чарт" to "",
        "Электроника" to "electronic",
        "Рок" to "rock",
        "Поп" to "pop",
        "Хип-хоп" to "hip hop",
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
        if (sections.isEmpty() && !loading) {
            load()
            // Если сеть так и не ответила, пробуем ещё пару раз сами, без нажатия «Повторить»
            var tries = 0
            while (sections.isEmpty() && error != null && tries < 2) {
                tries++
                delay(4000)
                load()
            }
        }
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
                            FeedSection(title, searchWithRetry(provider, query).take(12))
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

// --- ОБНОВЛЕННЫЙ HOME SCREEN ---

@Composable
fun HomeScreen(
    feeds: List<HomeFeed>,
    service: Service,
    onServiceChange: (Service) -> Unit,
    audio: AudioBackend,
    likes: LikesRepository,
    providers: List<MusicProvider>,
    likesSelected: Boolean,
    onOpenLikes: () -> Unit,
    onOpenAuth: () -> Unit,
    playlists: PlaylistsRepository? = null,
    recs: RecommendationsFeed? = null,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(feeds) { feeds.forEach { feed -> launch { feed.loadIfNeeded() } } }
    val mixed = service == Service.ALL
    val noPlaylists = androidx.compose.runtime.remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<Playlist>()) }
    val myPlaylists by (playlists?.playlists ?: noPlaylists).collectAsState()
    val rows = buildRows(feeds, mixed)
    val signedInForRecs by likes.signedIn.collectAsState()
    LaunchedEffect(recs, signedInForRecs) { recs?.ensureLoaded(signedInForRecs) }
    // На чипе источника рекомендации только его, на «Все» смешанные
    // Deezer: только превью, общие лайки и рекомендации на его чипе не показываем
    val isDeezer = service == Service.DEEZER
    val recProvider = when (service) {
        Service.JAMENDO -> com.unknowstorm.musicapp.model.ProviderId.JAMENDO
        Service.AUDIUS -> com.unknowstorm.musicapp.model.ProviderId.AUDIUS
        else -> null
    }
    val shownRecs = recs?.tracks.orEmpty().let { list ->
        if (recProvider == null) list else list.filter { it.provider == recProvider }
    }
    val recArtists = recs?.artists.orEmpty()
        .filter { a -> shownRecs.any { it.artist.contains(a.removePrefix("@"), ignoreCase = true) } }
        .ifEmpty { recs?.artists.orEmpty() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Новый верхний бар
        TopBar(
            service,
            onServiceChange,
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            likesSelected = likesSelected,
            onLikes = onOpenLikes,
        )

        // Вкладка «Любимые» стоит в панели слева от источников и заменяет ленту
        if (likesSelected) {
            LikedContent(likes, providers, audio, onOpenAuth)
            return@Column
        }

        if (service == Service.DEEZER) {
            HintCard("Deezer: только 30-секундные превью. Они живут на своём чипе и не попадают в общий поиск и очередь.")
        }

        if (!service.ready) {
            HintCard("${service.title} скоро появится. ${service.note}.")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Секция "Вы лайкнули" с кнопкой фильтра
            if (!isDeezer) item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Вы лайкнули", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (!isDeezer) item(key = "likes") {
                val liked by likes.tracks.collectAsState()
                val signedIn by likes.signedIn.collectAsState()
                val shown = liked
                    .filterNot { DeadTracks.isDead(it) }
                    .filter { recProvider == null || it.provider == recProvider }
                when {
                    !signedIn -> HintCard("Войдите в аккаунт, и треки, которые вы лайкнете (♡ в плеере), будут храниться здесь.")
                    shown.isEmpty() -> HintCard(
                        if (recProvider != null && liked.isNotEmpty()) "На этом сервисе лайков пока нет."
                        else "Нажмите ♡ в плеере, и трек появится здесь."
                    )
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shown, key = { "like:${it.provider}:${it.id}" }) { track ->
                            HomeTrackCard(track, showBadge = recProvider == null) {
                                scope.launch { playLiked(shown, track, providers, audio) }
                            }
                        }
                    }
                }
            }

            // Подборки
            item { SectionTitle("Подборки для вас") }
            if (!isDeezer && shownRecs.isNotEmpty()) {
                item(key = "recs-label") {
                    Text(
                        "Ещё от тех, кого вы слушаете: " + recArtists.joinToString(", "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                item(key = "recs-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shownRecs, key = { "rec:${it.provider}:${it.id}" }) { track ->
                            HomeTrackCard(track, showBadge = true) {
                                audio.playQueue(shownRecs, shownRecs.indexOf(track))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (feeds.any { it.loading }) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
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
                            HomeTrackCard(track, showBadge = mixed) {
                                audio.playQueue(row.tracks, row.tracks.indexOf(track))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Плейлисты
            item { SectionTitle("Ваши плейлисты") }
            if (myPlaylists.isEmpty()) item(key = "no-playlists") {
                HintCard("Плейлистов пока нет. На вкладке «Очередь» можно сохранить очередь как плейлист.")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(myPlaylists.size) { index ->
                        val pl = myPlaylists.getOrNull(index) ?: return@items
                        val colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                        PlaylistCard(
                            pl.name,
                            colors[index % colors.size],
                            onClick = { scope.launch { playlists?.let { playPlaylist(it, pl, providers, audio) } } },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Лайки хранят только метаданные, поэтому ссылку на звук берём заново у источника:
 * у Deezer она протухает. Что не удалось получить, пропускаем.
 */
internal suspend fun playLiked(
    tracks: List<Track>,
    start: Track,
    providers: List<MusicProvider>,
    audio: AudioBackend,
) {
    // Окно вокруг выбранного трека: так работает и выбор из хвоста длинного списка
    val from = tracks.indexOfFirst { it.provider == start.provider && it.id == start.id }.coerceAtLeast(0)
    val window = tracks.drop((from - 5).coerceAtLeast(0)).take(30)
    val resolved = coroutineScope {
        window.map { track ->
            async {
                val provider = providers.firstOrNull { it.id == track.provider } ?: return@async null
                val url = try {
                    withTimeoutOrNull(6_000) { provider.resolveStream(track) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                url?.takeIf { it.isNotBlank() }?.let { track.copy(streamUrl = it) }
            }
        }.awaitAll()
    }.filterNotNull()
    val index = resolved.indexOfFirst { it.provider == start.provider && it.id == start.id }
    if (index >= 0) audio.playQueue(resolved, index)
}

// --- НОВЫЕ КОМПОНЕНТЫ ---

@Composable
fun TopBar(
    selected: Service,
    onSelect: (Service) -> Unit,
    modifier: Modifier = Modifier,
    // Кнопка «Любимые» показывается, только если передан onLikes (на экране YouTube её нет)
    likesSelected: Boolean = false,
    onLikes: (() -> Unit)? = null,
) {
    val menuOpen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val active = Service.entries.filter { it.ready && it != Service.SPOTIFY }
    val soon = Service.entries.filter { !it.ready }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // «Любимые»: самая левая кнопка
        if (onLikes != null) {
            IconButton(
                onClick = onLikes,
                modifier = Modifier.clip(CircleShape)
                    .background(if (likesSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            ) {
                HeartIcon(
                    likesSelected,
                    Modifier.size(22.dp),
                    if (likesSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        active.forEach { service ->
            val isSelected = !likesSelected && selected == service
            IconButton(
                onClick = { onSelect(service) },
                modifier = Modifier.clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            ) {
                ServiceLogo(
                    service,
                    Modifier.size(22.dp),
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (soon.isNotEmpty() || Service.SPOTIFY.ready) {
            Box {
                val spotifyOn = !likesSelected && selected == Service.SPOTIFY
                IconButton(
                    onClick = { menuOpen.value = true },
                    modifier = Modifier.clip(CircleShape)
                        .background(if (spotifyOn) MaterialTheme.colorScheme.primary else Color.Transparent),
                ) {
                    AddIcon(
                        Modifier.size(24.dp),
                        if (spotifyOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen.value,
                    onDismissRequest = { menuOpen.value = false },
                ) {
                    if (Service.SPOTIFY.ready) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Spotify") },
                            onClick = { menuOpen.value = false; onSelect(Service.SPOTIFY) },
                        )
                    }
                    soon.forEach { service ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(service.title + " · скоро") },
                            leadingIcon = { ServiceLogo(service, Modifier.size(20.dp)) },
                            onClick = {
                                menuOpen.value = false
                                onSelect(service)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTrackCard(track: Track, showBadge: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))) {
            CoverImage(track.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(16.dp))
            if (showBadge) {
                ServiceBadge(track.provider.toService(), Modifier.align(Alignment.BottomEnd).padding(6.dp), 20.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistCard(title: String, color: Color, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AddIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        drawLine(tint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), stroke, StrokeCap.Round)
    }
}

// --- ВАШИ СУЩЕСТВУЮЩИЕ КОМПОНЕНТЫ (БЕЗ ИЗМЕНЕНИЙ) ---

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
fun ServiceTray(selected: Service, onSelect: (Service) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Service.entries.filter { it != Service.SPOTIFY }, key = { it.name }) { service ->
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
        // Spotify вне ряда логотипов: вход через «+», только текстом
        item(key = "more") {
            val open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selected == Service.SPOTIFY,
                    onClick = { open.value = true },
                    label = { Text(if (selected == Service.SPOTIFY) "+ Spotify" else "+") },
                )
                androidx.compose.material3.DropdownMenu(expanded = open.value, onDismissRequest = { open.value = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Spotify") },
                        onClick = { open.value = false; onSelect(Service.SPOTIFY) },
                    )
                }
            }
        }
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