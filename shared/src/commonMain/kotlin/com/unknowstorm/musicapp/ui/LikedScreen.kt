package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import com.unknowstorm.musicapp.i18n.S
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.downloads.DownloadManager
import com.unknowstorm.musicapp.history.HistoryRepository
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LibraryTab {
    LIKES, PLAYLISTS, HISTORY, LOCAL, CACHE;

    // Свойство, а не параметр конструктора: название берётся из текущего языка при каждой отрисовке
    val label: String
        @Composable get() = when (this) {
            LIKES -> S.libLikes
            PLAYLISTS -> S.libPlaylists
            HISTORY -> S.libHistory
            LOCAL -> S.libLocal
            CACHE -> S.libCache
        }
}

/**
 * Элемент выезжает снизу с лёгким масштабом на пружине. [played] хранит уже показанные индексы:
 * при прокрутке и возвращении они не анимируются повторно. Индексы больше [maxIndex] не анимируются
 * (в длинных списках каскад нужен только для первых строк).
 */
@Composable
internal fun Modifier.staggeredEntrance(
    index: Int,
    played: MutableSet<Int>,
    maxIndex: Int = Int.MAX_VALUE,
    stepMs: Long = 55L,
    shift: Dp = 40.dp,
    fromScale: Float = 0.9f,
): Modifier {
    if (index > maxIndex) return this
    val progress = remember { Animatable(if (index in played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (index !in played) {
            played.add(index)
            delay(index * stepMs)
            progress.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
        }
    }
    val shiftPx = with(LocalDensity.current) { shift.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = (p * 1.4f).coerceIn(0f, 1f)
        translationY = (1f - p) * shiftPx
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}

/** Строка списка: плавно появляется, исчезает и сдвигается на место удалённой. */
internal fun LazyItemScope.animatedRow(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(250),
    placementSpec = spring<IntOffset>(0.8f, Spring.StiffnessMediumLow),
    fadeOutSpec = tween(150),
)

/**
 * «Моя библиотека»: первая страница верхней панели Главной. Список разделов
 * «Мои лайки», «Мои плейлисты», «История», «Кеш / скачанное» (пока заглушка);
 * нажатие открывает раздел, стрелка или «назад» возвращает к списку.
 */
@Composable
fun LibraryContent(
    likes: LikesRepository,
    playlists: PlaylistsRepository?,
    history: HistoryRepository?,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onOpenAuth: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    local: LocalLibrary? = null,
    downloads: DownloadManager? = null,
) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val sections = LibraryTab.entries

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            val opening = targetState >= 0 && initialState < 0
            val closing = targetState < 0 && initialState >= 0
            val slide = spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            val transition = when {
                opening ->
                    (slideInHorizontally(slide) { it / 3 } + fadeIn(tween(240, delayMillis = 80))) togetherWith
                        (slideOutHorizontally(slide) { -it / 8 } + fadeOut(tween(120)))
                closing ->
                    (slideInHorizontally(slide) { -it / 8 } + fadeIn(tween(240, delayMillis = 80))) togetherWith
                        (slideOutHorizontally(slide) { it / 3 } + fadeOut(tween(120)))
                else -> fadeIn(tween(200)) togetherWith fadeOut(tween(120))
            }
            // При закрытии уходящий раздел остаётся поверх плиток
            transition.targetContentZIndex = if (closing) -1f else 0f
            transition.using(SizeTransform(clip = false))
        },
        label = "library",
        modifier = Modifier.fillMaxSize(),
    ) { sel ->
        if (sel !in sections.indices) {
            val liked by likes.tracks.collectAsState()
            val signedIn by likes.signedIn.collectAsState()
            LibraryTiles(
                likesCount = if (signedIn) liked.count { !DeadTracks.isDead(it) } else null,
                onOpen = { selected = it },
            )
        } else {
            val section = sections[sel]
            PlatformBackHandler(onBack = { selected = -1 })
            val headerPlayed = remember { mutableSetOf<Int>() }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .staggeredEntrance(0, headerPlayed, shift = 16.dp, fromScale = 0.98f)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selected = -1 }) { Text("←", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        section.label,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        LibraryTab.LIKES -> LikedContent(likes, providers, audio, onOpenAuth, downloads)
                        LibraryTab.PLAYLISTS -> PlaylistsTab(playlists, onOpenAuth, onOpenPlaylist)
                        LibraryTab.HISTORY -> HistoryTab(likes, history, providers, audio, onOpenAuth)
                        LibraryTab.LOCAL -> if (local != null) LocalFilesContent(local, audio) else Message(S.noLocalFiles)
                        LibraryTab.CACHE -> if (downloads != null) DownloadsContent(downloads, audio) else Message(S.cacheSoon)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: PlaylistsRepository?,
    onOpenAuth: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
) {
    if (playlists == null) {
        Message(S.playlistsUnavailable)
        return
    }
    val signedIn by playlists.signedIn.collectAsState()
    val list by playlists.playlists.collectAsState()
    LaunchedEffect(signedIn) { if (signedIn) playlists.refresh() }
    when {
        !signedIn -> Message(S.signInToCreatePlaylists) {
            Button(onClick = onOpenAuth) { Text(S.signIn) }
        }
        list.isEmpty() -> Message(S.noPlaylistsYet)
        else -> PlaylistTilesGrid(playlists, list, onOpen = onOpenPlaylist)
    }
}

@Composable
private fun HistoryTab(
    likes: LikesRepository,
    history: HistoryRepository?,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onOpenAuth: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val played = remember { mutableSetOf<Int>() }
    val signedIn by likes.signedIn.collectAsState()
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(signedIn, history) {
        tracks = null
        if (signedIn && history != null) {
            // Один и тот же трек показываем один раз: самое свежее прослушивание
            tracks = history.recent()
                .filterNot { DeadTracks.isDead(it) }
                .distinctBy { "${it.provider}:${it.id}" }
        }
    }
    val shown = tracks
    when {
        history == null -> Message(S.historyUnavailable)
        !signedIn -> Message(S.signInToKeepHistory) {
            Button(onClick = onOpenAuth) { Text(S.signIn) }
        }
        shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        shown.isEmpty() -> Message(
            S.historyEmpty
        )
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(shown, key = { _, t -> "${t.provider}:${t.id}" }) { index, track ->
                Row(
                    Modifier
                        .then(animatedRow())
                        .staggeredEntrance(index, played, maxIndex = 10, stepMs = 35L, shift = 24.dp, fromScale = 0.96f)
                        .fillMaxWidth()
                        .clickable { scope.launch { playLiked(shown, track, providers, audio) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackCover(track, Modifier.size(56.dp), badge = true, badgeSize = 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
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
            }
        }
    }
}

/**
 * Содержимое вкладки «Мои лайки» в библиотеке (под верхней панелью выбора источников).
 * Показывает лайки только текущего аккаунта: сервер чужие строки не отдаёт (RLS),
 * а при выходе или смене аккаунта список очищается.
 */
@Composable
fun LikedContent(
    likes: LikesRepository,
    providers: List<MusicProvider>,
    audio: AudioBackend,
    onOpenAuth: () -> Unit,
    downloads: DownloadManager? = null,
) {
    val scope = rememberCoroutineScope()
    val played = remember { mutableSetOf<Int>() }
    val liked by likes.tracks.collectAsState()
    val signedIn by likes.signedIn.collectAsState()
    val error by likes.error.collectAsState()
    val shown = liked.filterNot { DeadTracks.isDead(it) }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(signedIn) { if (signedIn) likes.refresh() }

    Column(Modifier.fillMaxSize()) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        when {
            !signedIn -> Message(S.signInToSaveYour) {
                Button(onClick = onOpenAuth) { Text(S.signIn) }
            }
            shown.isEmpty() -> Message(S.yourFavoritesWillAppearHere)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            S.tracks(shown.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { scope.launch { playLiked(shown, shown.first(), providers, audio) } }) {
                            Text(S.playAll)
                        }
                    }
                }
                itemsIndexed(shown, key = { _, t -> "${t.provider}:${t.id}" }) { index, track ->
                    Row(
                        Modifier
                            .then(animatedRow())
                            .staggeredEntrance(index, played, maxIndex = 10, stepMs = 35L, shift = 24.dp, fromScale = 0.96f)
                            .fillMaxWidth()
                            .clickable { scope.launch { playLiked(shown, track, providers, audio) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackCover(track, Modifier.size(56.dp), badge = true, badgeSize = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
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
                        if (downloads != null) DownloadButton(track, downloads)
                        // Tapping removes the like, the track immediately disappears from the list
                        IconButton(onClick = { likes.toggle(track) }) {
                            HeartIcon(true, tint = primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: @Composable (() -> Unit)? = null) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.8f, Spring.StiffnessLow)) }
    Box(
        Modifier.fillMaxSize().padding(32.dp).graphicsLayer {
            alpha = appear.value
            val s = 0.94f + 0.06f * appear.value
            scaleX = s
            scaleY = s
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
