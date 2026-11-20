package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.unknowstorm.musicapp.i18n.S
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
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.provider.youtube.YouTubeLinkRepository
import com.unknowstorm.musicapp.provider.youtube.YtLinkException
import com.unknowstorm.musicapp.provider.youtube.YtPlaylist
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.unknowstorm.musicapp.resources.youtube_logo_dark_bg
import com.unknowstorm.musicapp.resources.youtube_logo_light_bg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val FLAG_ACCEPTED = "youtube_terms_accepted_v1"
internal const val YOUTUBE_HOME = "https://www.youtube.com"

// Genre mixes: these are searches, one request costs 100 quota units. Normally we take ready-made ones from the server,
// and we make our own requests only on swipe-down, if something is missing on the server
private val GENRES: List<Pair<String, String>>
    get() = listOf(
    S.jazz to "jazz music",
    S.pop to "pop music",
    S.rock to "rock music",
    S.hipHop to "hip hop music",
    S.electronic to "electronic music",
    S.loFi to "lofi",
)

/** Shows the content only if the user has accepted the YouTube terms. */
@Composable
internal fun YouTubeConsentGate(onLeave: () -> Unit, peek: Boolean = false, content: @Composable () -> Unit) {
    val flags = rememberFlagStore()
    var accepted by remember { mutableStateOf(flags.get(FLAG_ACCEPTED)) }
    if (accepted) {
        content()
    } else if (peek) {
        // Neighboring page while paging: the consent screen does not flicker
        Box(Modifier.fillMaxSize())
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

/** Home, YouTube chip: popular and mixes; with a video open, the big player. */
@Composable
fun YouTubeScreen(
    audio: AudioBackend,
    session: YouTubeSession,
    onBack: () -> Unit,
    active: Boolean = true,
) {
    YouTubeConsentGate(onLeave = onBack, peek = !active) {
        YouTubeHome(audio, session)
    }
}

@Composable
private fun YouTubeHome(audio: AudioBackend, session: YouTubeSession) {
    // Left the YouTube screen: the playing video moves to the floating window
    DisposableEffect(Unit) { onDispose { session.minimize(); LibraryPanels.youtube = false } }

    val showPlayer by remember {
        derivedStateOf { session.video != null && (session.docked || session.progress > 0.01f) }
    }
    PlatformBackHandler(enabled = showPlayer, onBack = { session.minimize() })

    // The feed is always in place. The big player is drawn by YouTubeOverlay: the container grows over the feed
    // to the bounds of this screen, like the main player.
    // The chips bar and the screen bounds for the player are now handled by the pager in MainScreen
    YouTubeFeed(audio, session, Modifier.fillMaxSize())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeFeed(audio: AudioBackend, session: YouTubeSession, modifier: Modifier = Modifier) {
    val store = rememberTextStore()
    val api = remember { YouTubeApi.shared.also { it.attach(store) } }
    val openLink = rememberExternalLinkOpener()
    val scope = rememberCoroutineScope()

    var popular by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    // First load when the screen opens: while it runs, we do not draw mixes and buttons, so nothing flickers
    var loading by remember { mutableStateOf(true) }
    // Refresh by swiping down or with a button
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Mixes loaded earlier are taken from the app cache: this does not spend quota
    val genres = remember {
        mutableStateMapOf<String, List<YouTubeVideo>>().also { map ->
            GENRES.forEach { (title, query) -> api.cachedSearch(query)?.let { map[title] = it } }
        }
    }

    // fillMissing = false: only the shared server cache (on open). true: also our own requests for what is missing (swipe)
    suspend fun load(fillMissing: Boolean) {
        error = null
        try {
            api.syncShared()
            GENRES.forEach { (title, query) ->
                if (!genres.containsKey(title)) api.cachedSearch(query)?.let { genres[title] = it }
            }
            popular = api.popularMusic()
            if (fillMissing) {
                GENRES.forEach { (title, query) ->
                    if (!genres.containsKey(title)) genres[title] = api.search(query)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: YouTubeException) {
            error = e.message
        } catch (e: Exception) {
            error = S.couldNotLoad(e.message)
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            load(fillMissing = false)
        } finally {
            loading = false
        }
    }

    fun refresh() {
        if (loading || refreshing) return
        scope.launch {
            refreshing = true
            try {
                load(fillMissing = true)
            } finally {
                refreshing = false
            }
        }
    }

    fun play(video: YouTubeVideo, list: List<YouTubeVideo>) {
        // The sound of our player and YouTube must not play at the same time
        audio.stop()
        session.open(video, list)
    }

    val missing = GENRES.any { !genres.containsKey(it.first) }

    // The personal library is its own sub-screen: the card at the top of the feed opens it
    if (LibraryPanels.youtube) {
        YouTubeLibraryPanel(onPlay = { v, l -> play(v, l) }, modifier = modifier.fillMaxSize())
        return
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh() }, modifier = modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "head") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YouTubeLogo(onClick = { openLink(YOUTUBE_HOME) })
                    Spacer(Modifier.width(12.dp))
                    Text(
                        S.searchYoutubeTheSearchTab,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "library-entry") {
                LibraryEntryCard(
                    title = tr("My YouTube", "Мій YouTube"),
                    subtitle = tr("Playlists and liked videos", "Плейлисти та вподобані відео"),
                    onClick = { LibraryPanels.youtube = true },
                )
            }
            if (loading) {
                item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            }
            error?.let {
                item(key = "error") {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            if (popular.isNotEmpty()) {
                item(key = "popular-label") { FeedTitle(S.popularMusic) }
                item(key = "popular-strip") { VideoStrip(popular) { play(it, popular) } }
            }
            if (genres.isNotEmpty()) {
                item(key = "genres-label") { FeedTitle(S.genrePicks) }
                GENRES.forEach { (title, _) ->
                    val loaded = genres[title] ?: return@forEach
                    item(key = "label:$title") {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                    item(key = "strip:$title") { VideoStrip(loaded) { play(it, loaded) } }
                }
            }
            if (!loading && missing) {
                item(key = "missing") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text(
                            S.notAllPicksLoadedPull,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { refresh() }, enabled = !refreshing) {
                            Text(if (refreshing) S.loading else S.refresh)
                        }
                    }
                }
            }
            item(key = "legal") { LegalFooter() }
        }
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
        // Thumbnail without cropping, rounding or badges on top: the rules do not allow changing it
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Spacer(Modifier.height(6.dp))
        // Reserved height (2 title lines, 1 channel line, 1 duration line): all cards in a row are equally tall,
        // so the row does not jump when it is scrolled sideways
        Text(video.title, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            video.channel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (video.durationMs > 0) formatYtDuration(video.durationMs) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
        )
    }
}

@Composable
internal fun VideoRow(video: YouTubeVideo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Thumbnail without cropping or badges on top: the rules do not allow changing it
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

/** Official YouTube logo on a solid white background, unmodified. Leads to YouTube. */
@Composable
fun YouTubeLogo(modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Official full-color logos on one solid plate: almost black logo on white, white logo on Almost Black
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        onClick = onClick,
        color = if (dark) Color(0xFF282828) else Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(
                if (dark) Res.drawable.youtube_logo_dark_bg else Res.drawable.youtube_logo_light_bg
            ),
            contentDescription = "YouTube",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).height(24.dp),
        )
    }
}

internal fun playerErrorText(code: Int): String = when (code) {
    101, 150 -> S.theAuthorDisabledEmbeddingFor
    100 -> S.videoUnavailableDeletedPrivateOr
    153 -> S.youtubeCouldNotVerifyThe
    5 -> S.playerErrorTryAgainOr
    else -> S.theVideoCouldNotBe(code)
}

internal fun formatYtDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

// ---------- My YouTube (own account, read-only) ----------

/** The library as its own sub-screen (opened from the card at the top of the feed). */
@Composable
private fun YouTubeLibraryPanel(onPlay: (YouTubeVideo, List<YouTubeVideo>) -> Unit, modifier: Modifier = Modifier) {
    PlatformBackHandler(enabled = true, onBack = { LibraryPanels.youtube = false })
    Column(modifier.verticalScroll(rememberScrollState())) {
        TextButton(onClick = { LibraryPanels.youtube = false }, modifier = Modifier.padding(horizontal = 8.dp)) { BackLabel(S.back) }
        MyYouTubeBlock(onPlay)
        LegalFooter()
    }
}

/** Playlists and liked videos of the connected YouTube account. Memory only, not mixed with anything else. */
@Composable
private fun MyYouTubeBlock(onPlay: (YouTubeVideo, List<YouTubeVideo>) -> Unit) {
    val repo = remember { YouTubeLinkRepository() }
    val scope = rememberCoroutineScope()
    val openLink = rememberExternalLinkOpener()
    // Start from the in-memory copy so the library shows at once when you come back to the screen
    var linked by remember { mutableStateOf(YouTubeLibraryCache.overview != null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf<List<YtPlaylist>>(YouTubeLibraryCache.overview?.playlists ?: emptyList()) }
    var liked by remember { mutableStateOf<List<YouTubeVideo>>(YouTubeLibraryCache.overview?.liked ?: emptyList()) }
    var opened by remember { mutableStateOf<Pair<YtPlaylist, List<YouTubeVideo>>?>(null) }

    LaunchedEffect(linked, reloadKey) {
        if (!linked) {
            YouTubeLibraryCache.clear()
            playlists = emptyList(); liked = emptyList(); opened = null
            return@LaunchedEffect
        }
        val cached = YouTubeLibraryCache.overview
        val force = reloadKey > 0
        // A fresh in-memory copy is already on screen: no request. Otherwise refresh quietly if there is something to show.
        if (cached != null && !force && YouTubeLibraryCache.overviewFresh()) return@LaunchedEffect
        busy = cached == null || force
        message = null
        try {
            val o = repo.overview()
            YouTubeLibraryCache.putOverview(o)
            playlists = o.playlists
            liked = o.liked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val revoked = (e as? YtLinkException)?.code == "revoked"
            if (revoked) linked = false
            // A failed quiet refresh keeps the copy on screen and says nothing
            if (cached == null || force || revoked) message = ytLinkMessage(e)
        } finally {
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        FeedTitle(tr("My YouTube", "Мій YouTube"))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YouTubeLogo(onClick = { openLink(YOUTUBE_HOME) })
            Spacer(Modifier.width(12.dp))
            Text(
                tr(
                    "Optional. Your own playlists and liked videos, shown only here and kept only in memory.",
                    "За бажанням. Ваші плейлисти та вподобані відео: показуються лише тут і зберігаються лише в пам’яті.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        YouTubeLinkRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onLinked = { linked = it })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (linked && !busy) {
            TextButton(onClick = { reloadKey++ }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(tr("Refresh", "Оновити")) }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (linked) {
            if (liked.isNotEmpty()) {
                Text(
                    tr("Liked videos", "Вподобані відео"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                VideoStrip(liked) { onPlay(it, liked) }
            }
            if (playlists.isNotEmpty()) {
                Text(
                    tr("Playlists", "Плейлисти"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val videos = YouTubeLibraryCache.playlist(p.id, p.count)
                                        ?: repo.playlist(p.id).also { YouTubeLibraryCache.putPlaylist(p.id, p.count, it) }
                                    opened = p to videos
                                    message = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    message = ytLinkMessage(e)
                                }
                            }
                        }) { Text("${p.title} (${p.count})", maxLines = 1) }
                    }
                }
            }
            opened?.let { (p, videos) ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { opened = null }) { Text(tr("Close", "Закрити")) }
                }
                if (videos.isEmpty()) {
                    Text(
                        tr("No videos that can be played here.", "Немає відео, які можна відтворити тут."),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    VideoStrip(videos) { onPlay(it, videos) }
                }
            }
        }
    }
}
