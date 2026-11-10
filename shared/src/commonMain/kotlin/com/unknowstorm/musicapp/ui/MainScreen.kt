package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.history.HistoryRepository
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.playlists.Playlist
import com.unknowstorm.musicapp.recommendations.RecommendationsFeed
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.spotify.LocalSpotifyRemote

enum class Tab(private val labelFn: () -> String) {
    HOME({ S.home }),
    SEARCH({ S.search }),
    QUEUE({ S.queue }),
    SETTINGS({ S.settings });

    /** Подпись берётся при каждом чтении, поэтому меняется вместе с языком. */
    val label: String get() = labelFn()
}

@Composable
fun MainScreen(
    providers: List<MusicProvider>,
    audio: AudioBackend,
    authRepo: AuthRepository,
    likes: LikesRepository,
    playlists: PlaylistsRepository,
    history: HistoryRepository,
    onOpenPlayer: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var service by remember { mutableStateOf(Service.ALL) }
    var showLikes by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    // Позиция пейджера для цвета острова; старт с текущей страницы, чтобы первый кадр не мигал
    var pagerPos by remember {
        mutableFloatStateOf(pagerOrder(true).indexOf(if (showLikes) null else service).coerceAtLeast(0).toFloat())
    }

    val feeds = remember(providers) {
        providers.mapNotNull { provider ->
            val s = Service.entries.firstOrNull { it.name == provider.id.toString() }
            s?.let { HomeFeed(it, provider, defaultMixes(it)) }
        }
    }
    fun feedsFor(s: Service) =
        if (s == Service.ALL) feeds.filter { it.service.kind == com.unknowstorm.musicapp.model.PlaybackKind.IN_APP } else feeds.filter { it.service == s }

    val recs = remember(providers, history, likes) { RecommendationsFeed(history, likes, providers) }

    val ytScope = rememberCoroutineScope()
    val session = remember { YouTubeSession(ytScope) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // Большой плеер YouTube развёрнут полностью: нижняя панель навигации прячется (портрет и альбом).
    val hideNavBar by remember {
        derivedStateOf {
            session.video != null && session.progress >= 0.999f &&
                tab == Tab.HOME && service == Service.YOUTUBE
        }
    }
    val playback by audio.state.collectAsState()
    val spotify = LocalSpotifyRemote.current
    // Наша музыка или YouTube заиграли: Spotify на паузу
    LaunchedEffect(playback.isPlaying) { if (playback.isPlaying) spotify?.pause() }
    LaunchedEffect(session.video) { if (session.video != null) spotify?.pause() }
    // Включили нашу музыку: видео YouTube закрывается, два звука одновременно не играют
    LaunchedEffect(playback.mediaId) {
        if (playback.mediaId != null && session.video != null) session.close()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
            .onSizeChanged { rootSize = it }
    ) {
    // Делаем фон колонки прозрачным (чтобы не было черных полос)
    Column(Modifier.fillMaxSize().background(Color.Transparent)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> if (service == Service.SPOTIFY) {
                    SpotifyScreen(
                        remote = spotify,
                        onServiceChange = { service = it; showLikes = false },
                        onOpenLikes = { service = Service.ALL; showLikes = true },
                        onBack = { service = Service.ALL },
                        onBeforePlay = { audio.stop(); session.close() },
                    )
                } else {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        TopBar(
                            service,
                            { service = it; showLikes = false },
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            likesSelected = showLikes,
                            onLikes = { showLikes = true },
                            position = { pagerPos },
                        )
                        ServicePager(
                            current = service,
                            likesSelected = showLikes,
                            hasLikes = true,
                            // Развёрнутый плеер YouTube: листание выключено
                            swipeEnabled = !(session.video != null && session.progress >= 0.999f),
                            onSelect = { service = it; showLikes = false },
                            onLikes = { service = Service.ALL; showLikes = true },
                            onPosition = { pagerPos = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                // До этих границ растёт большой плеер YouTube
                                .onGloballyPositioned { session.screenBounds = it.boundsInWindow() },
                        ) { page, active ->
                            when (page) {
                                null -> LikedContent(likes, providers, audio, onOpenAuth)
                                Service.YOUTUBE -> YouTubeScreen(
                                    audio = audio,
                                    session = session,
                                    onBack = { service = Service.ALL },
                                    active = active,
                                )
                                else -> HomeScreen(
                                    feedsFor(page), page, { service = it; showLikes = false }, audio, likes, providers,
                                    false, { showLikes = true }, onOpenAuth, playlists, recs, { openPlaylist = it },
                                    showBar = false,
                                )
                            }
                        }
                    }
                }
                Tab.SEARCH -> TrackListScreen(providers, audio, session, onOpenSpotify = { tab = Tab.HOME; service = Service.SPOTIFY }, likes = likes)
                Tab.QUEUE -> QueueScreen(audio, playlists, onOpenAuth)
                Tab.SETTINGS -> SettingsScreen(authRepo, onOpenAuth)
            }
            openPlaylist?.let { pl ->
                PlaylistScreen(pl, playlists, providers, audio, onBack = { openPlaylist = null })
            }
        }

        // Место под мини-плеер Spotify (рисует его SpotifyExpandablePlayer)
        SpotifyMiniSlot(hidden = playback.title != null)

        // Мини-плеер лежит над нижней панелью
        NowPlayingBar(
            audio = audio,
            onOpen = onOpenPlayer,
            // Убираем лишние отступы, чтобы плеер прилегал вплотную
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // ИСПРАВЛЕНИЕ: Убираем тень у NavigationBar и делаем его прозрачным
        if (!hideNavBar) NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp, // Убирает черную тень!
            modifier = Modifier.background(Color.Transparent)
        ) {
            Tab.entries.forEach { item ->
                NavigationBarItem(
                    selected = tab == item,
                    onClick = { tab = item; openPlaylist = null },
                    icon = {
                        when (item) {
                            Tab.HOME -> HomeIcon()
                            Tab.SEARCH -> SearchIcon()
                            Tab.QUEUE -> QueueIcon()
                            Tab.SETTINGS -> SettingsIcon()
                        }
                    },
                    label = { Text(item.label) },
                )
            }
        }
    }

        // Единственный плеер YouTube: на экране YouTube большой, в остальных местах плавающее окно
        YouTubeOverlay(
            session = session,
            containerOrigin = rootOrigin,
            containerSize = rootSize,
            onExpand = {
                tab = Tab.HOME
                service = Service.YOUTUBE
                session.docked = true
            },
        )
    }
}

@Composable
private fun InfoScreen(title: String, body: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(authRepo: AuthRepository, onOpenAuth: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(S.settings, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        var editingProfile by remember { mutableStateOf(false) }
        AccountBlock(authRepo, onOpenAuth, onEditProfile = { editingProfile = true })

        // Кнопка смены пароля: только для вошедшего пользователя
        val authState by authRepo.state.collectAsState()
        var showChangePassword by remember { mutableStateOf(false) }
        if (authState is AuthState.SignedIn) {
            TextButton(onClick = { showChangePassword = true }) { Text(S.changePassword) }
        }
        val editProfile = (authState as? AuthState.SignedIn)?.profile
        if (editingProfile && editProfile != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { editingProfile = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                PlatformBackHandler(onBack = { editingProfile = false })
                OnboardingScreen(repo = authRepo, profile = editProfile, onDone = { editingProfile = false })
            }
        }
        if (showChangePassword) {
            ChangePasswordDialog(repo = authRepo, onDismiss = { showChangePassword = false })
        }
        Spacer(Modifier.height(24.dp))

        LanguageSetting()
        Spacer(Modifier.height(24.dp))

        LegalLinks()
        Spacer(Modifier.height(24.dp))
        Text(S.musicSources, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Service.entries.filter { it != Service.ALL }.forEach { service ->
            ListItem(
                headlineContent = { Text(service.title) },
                supportingContent = {
                    Text(if (service.ready) S.available(service.note) else S.comingSoon(service.note))
                },
            )
        }
    }
}