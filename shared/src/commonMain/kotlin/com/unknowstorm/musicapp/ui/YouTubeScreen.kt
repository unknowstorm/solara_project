package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.youtube.YouTubeApi
import com.unknowstorm.musicapp.provider.youtube.YouTubeException
import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.youtube_logo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val FLAG_ACCEPTED = "youtube_terms_accepted_v1"
internal const val YOUTUBE_HOME = "https://www.youtube.com"

// Подборки по жанрам: это поиск, один запрос стоит 100 единиц квоты, поэтому грузим только по нажатию
private val GENRES = listOf(
    "Джаз" to "jazz music",
    "Поп" to "pop music",
    "Рок" to "rock music",
    "Хип-хоп" to "hip hop music",
    "Электроника" to "electronic music",
    "Лоу-фай" to "lofi",
)

/** Показывает содержимое, только если пользователь принял условия YouTube. */
@Composable
internal fun YouTubeConsentGate(onLeave: () -> Unit, content: @Composable () -> Unit) {
    val flags = rememberFlagStore()
    var accepted by remember { mutableStateOf(flags.get(FLAG_ACCEPTED)) }
    if (accepted) {
        content()
    } else {
        YouTubeConsentScreen(
            onAccept = {
                flags.set(FLAG_ACCEPTED, true)
                accepted = true
            },
            onBack = onLeave,
        )
    }
}

/** Главная, чип YouTube: популярное и подборки; при открытом видео большой плеер. */
@Composable
fun YouTubeScreen(
    audio: AudioBackend,
    session: YouTubeSession,
    onBack: () -> Unit,
    onServiceChange: (Service) -> Unit,
) {
    YouTubeConsentGate(onLeave = onBack) {
        YouTubeHome(audio, session, onServiceChange)
    }
}

@Composable
private fun YouTubeHome(audio: AudioBackend, session: YouTubeSession, onServiceChange: (Service) -> Unit) {
    val openLink = rememberExternalLinkOpener()

    // Ушли с экрана YouTube: играющее видео переезжает в плавающее окно
    DisposableEffect(Unit) { onDispose { session.minimize() } }

    val showPlayer by remember {
        derivedStateOf { session.video != null && (session.docked || session.progress > 0.01f) }
    }
    PlatformBackHandler(enabled = showPlayer, onBack = { session.minimize() })

    val current = session.video
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        // Лента всегда под плеером: проявляется, пока плеер гаснет, и наоборот.
        // Остаётся в композиции, чтобы не терялась позиция прокрутки.
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (current == null) 1f else (1f - session.progress / 0.5f).coerceIn(0f, 1f)
                },
        ) {
            TopBar(Service.YOUTUBE, onServiceChange, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            YouTubeFeed(audio, session, Modifier.weight(1f))
        }

        if (showPlayer && current != null) {
            // Сам перехватывает жесты (pointerInput в PlayerPane), поэтому лента под ним не нажимается
            PlayerPane(current, session, openLink)
        }
    }
}

@Composable
private fun YouTubeFeed(audio: AudioBackend, session: YouTubeSession, modifier: Modifier = Modifier) {
    val api = remember { YouTubeApi.shared }
    val openLink = rememberExternalLinkOpener()
    val scope = rememberCoroutineScope()

    var popular by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var popularLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Уже загруженные раньше подборки берём из кэша приложения: это не тратит квоту
    val genres = remember {
        mutableStateMapOf<String, List<YouTubeVideo>>().also { map ->
            GENRES.forEach { (title, query) -> api.cachedSearch(query)?.let { map[title] = it } }
        }
    }
    var loadingGenre by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        popularLoading = true
        try {
            popular = api.popularMusic()
        } catch (e: CancellationException) {
            throw e
        } catch (e: YouTubeException) {
            error = e.message
        } catch (e: Exception) {
            error = "Не удалось загрузить популярное: ${e.message}"
        } finally {
            popularLoading = false
        }
    }

    fun play(video: YouTubeVideo, list: List<YouTubeVideo>) {
        // Звук нашего плеера и YouTube не должны играть одновременно
        audio.stop()
        session.open(video, list)
    }

    fun loadGenre(title: String, query: String) {
        if (loadingGenre != null) return
        scope.launch {
            loadingGenre = title
            error = null
            try {
                genres[title] = api.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                error = e.message
            } catch (e: Exception) {
                error = "Не удалось загрузить: ${e.message}"
            } finally {
                loadingGenre = null
            }
        }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "head") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YouTubeLogo(onClick = { openLink(YOUTUBE_HOME) })
                Spacer(Modifier.width(12.dp))
                Text(
                    "Поиск по YouTube: вкладка «Поиск», чип YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (popularLoading) {
            item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        }
        error?.let {
            item(key = "error") {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        if (popular.isNotEmpty()) {
            item(key = "popular-label") { FeedTitle("Популярная музыка") }
            item(key = "popular-strip") { VideoStrip(popular) { play(it, popular) } }
        }
        item(key = "genres-label") { FeedTitle("Подборки по жанрам") }
        item(key = "genres-hint") {
            Text(
                "Подборка загружается по нажатию: у YouTube дневной лимит запросов на всё приложение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        GENRES.forEach { (title, query) ->
            val loaded = genres[title]
            if (loaded != null) {
                item(key = "label:$title") {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                item(key = "strip:$title") { VideoStrip(loaded) { play(it, loaded) } }
            } else {
                item(key = "label:$title") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { loadGenre(title, query) },
                            enabled = loadingGenre == null,
                        ) { Text(if (loadingGenre == title) "Загрузка…" else "Показать") }
                    }
                }
            }
        }
        item(key = "legal") { LegalFooter() }
    }
}

@Composable
private fun FeedTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun VideoStrip(videos: List<YouTubeVideo>, onClick: (YouTubeVideo) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(videos) { video -> VideoCard(video) { onClick(video) } }
    }
}

@Composable
private fun VideoCard(video: YouTubeVideo, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).clickable(onClick = onClick)) {
        // Миниатюра без обрезки, скруглений и значков поверх: по правилам её нельзя менять
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            video.channel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (video.durationMs > 0) {
            Text(
                formatYtDuration(video.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Большой экран видео в раскладке основного плеера. Само видео рисует общий слой YouTubeOverlay. */
@Composable
private fun PlayerPane(video: YouTubeVideo, session: YouTubeSession, openLink: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val playing = session.playerState == 1 || session.playerState == 3

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = ((session.progress - 0.2f) / 0.6f).coerceIn(0f, 1f) }
            // Свайп вниз сворачивает видео за пальцем. Плеер перекрывает свою область и жестов не получает.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        total = 0f
                        tracker.resetTracking()
                        session.beginDrag()
                    },
                    onDragEnd = { session.endDrag(tracker.calculateVelocity().y) },
                    onDragCancel = { session.endDrag(0f) },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        total += amount
                        tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                        session.dragBy(amount)
                    },
                )
            }
            .padding(horizontal = 24.dp),
    ) {
        // Ручка: показывает, что экран сворачивается, и сворачивает по тапу
        Box(
            Modifier.fillMaxWidth().height(28.dp).clickable { session.minimize() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
        }

        // Место под видео (как у обложки в основном плеере). Плеер рисуется поверх этого места.
        // Вокруг плеера нет наложений: наши элементы стоят только ниже него.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .onGloballyPositioned { session.dockBounds = it.boundsInWindow() },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            video.channel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        session.errorCode?.let {
            Text(playerErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showMenu = true }) { MoreIcon() }
            // Логотип YouTube ведёт на это видео в YouTube
            YouTubeLogo(onClick = { openLink("$YOUTUBE_HOME/watch?v=${video.id}") })
        }

        // На месте таймлайна основного плеера: у YouTube прогресс и перемотка внутри его плеера
        Spacer(Modifier.height(56.dp))

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { session.previous() }, enabled = session.hasPrev, modifier = Modifier.size(60.dp)) {
                SkipIcon(next = false, modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = { YouTubeControl.send?.invoke(if (playing) "pause" else "play") },
                modifier = Modifier.size(86.dp),
            ) { PlayPauseIcon(playing, Modifier.size(48.dp)) }
            IconButton(onClick = { session.next() }, enabled = session.hasNext, modifier = Modifier.size(60.dp)) {
                SkipIcon(next = true, modifier = Modifier.size(36.dp))
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Готово") } },
            title = { Text(video.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("Канал: ${video.channel}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { openLink("https://music.youtube.com/watch?v=${video.id}") }) {
                        Text("Открыть в YouTube Music")
                    }
                    TextButton(onClick = { openLink("$YOUTUBE_HOME/watch?v=${video.id}") }) {
                        Text("Открыть в YouTube")
                    }
                    TextButton(onClick = {
                        showMenu = false
                        session.close()
                    }) { Text("Закрыть видео") }
                    Text(
                        "Видео играет, пока оно видно на экране: в полном виде или в плавающем окне. " +
                            "Если свернуть приложение или выключить экран, видео встаёт на паузу (правила YouTube). " +
                            "Фоновое воспроизведение работает в официальных приложениях YouTube и YouTube Music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
internal fun VideoRow(video: YouTubeVideo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Миниатюра без обрезки и без значков поверх: по правилам её нельзя менять
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(168.dp).aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                video.channel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.durationMs > 0) {
                Text(
                    formatYtDuration(video.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Официальный логотип YouTube на сплошном белом фоне, без изменений. Ведёт на YouTube. */
@Composable
fun YouTubeLogo(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(Res.drawable.youtube_logo),
            contentDescription = "YouTube",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).height(24.dp),
        )
    }
}

private fun playerErrorText(code: Int): String = when (code) {
    101, 150 -> "Автор запретил встраивание этого видео. Откройте его в YouTube."
    100 -> "Видео недоступно: удалено, закрыто или недоступно в вашем регионе."
    153 -> "YouTube не смог проверить приложение. Откройте видео в YouTube."
    5 -> "Ошибка плеера. Попробуйте ещё раз или откройте видео в YouTube."
    else -> "Видео не удалось воспроизвести (код $code)."
}

internal fun formatYtDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}
