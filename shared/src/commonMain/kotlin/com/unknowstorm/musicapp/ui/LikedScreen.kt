package com.unknowstorm.musicapp.ui

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
import androidx.compose.foundation.lazy.items
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
import com.unknowstorm.musicapp.history.HistoryRepository
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) {
    LIKES("Мои лайки"),
    PLAYLISTS("Мои плейлисты"),
    HISTORY("История"),
    CACHE("Кеш / скачанное"),
}

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
) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val sections = LibraryTab.entries

    if (selected !in sections.indices) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            sections.forEachIndexed { i, section ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = i }
                        .padding(horizontal = 8.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    val section = sections[selected]
    PlatformBackHandler(onBack = { selected = -1 })
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
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
                LibraryTab.LIKES -> LikedContent(likes, providers, audio, onOpenAuth)
                LibraryTab.PLAYLISTS -> PlaylistsTab(playlists, onOpenAuth, onOpenPlaylist)
                LibraryTab.HISTORY -> HistoryTab(likes, history, providers, audio, onOpenAuth)
                LibraryTab.CACHE -> Message(S.cacheSoon)
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
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.id }) { pl ->
                ListItem(
                    headlineContent = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpenPlaylist(pl) },
                )
            }
        }
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
            items(shown, key = { "${it.provider}:${it.id}" }) { track ->
                Row(
                    Modifier
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
) {
    val scope = rememberCoroutineScope()
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
                items(shown, key = { "${it.provider}:${it.id}" }) { track ->
                    Row(
                        Modifier
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
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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
