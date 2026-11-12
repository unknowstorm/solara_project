package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import com.unknowstorm.musicapp.auth.AuthRepository
import kotlinx.coroutines.launch
import com.unknowstorm.musicapp.i18n.I18n
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.downloads.DownloadManager
import com.unknowstorm.musicapp.downloads.rememberDownloadPlatform
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

    /** The caption is read on every access, so it changes together with the language. */
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
    // Pager position for the island color; start from the current page so the first frame does not blink
    var pagerPos by remember {
        mutableFloatStateOf(pagerOrder(true).indexOf(if (showLikes) null else service).coerceAtLeast(0).toFloat())
    }

    // After "Save" in the Island editor the selected service may turn out hidden: return to "All"
    // so the pager does not stay on a page that no longer exists. "All" and Spotify (entry via "+") are not hideable.
    val islandVisible = IslandSettings.visible()
    LaunchedEffect(islandVisible) {
        if (service != Service.ALL && service != Service.SPOTIFY && service !in islandVisible) {
            service = Service.ALL
        }
    }

    val feeds = remember(providers, I18n.language) {
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
    // The big YouTube player is fully expanded: the bottom navigation bar hides (portrait and landscape).
    val hideNavBar by remember {
        derivedStateOf {
            session.video != null && session.progress >= 0.999f &&
                    tab == Tab.HOME && service == Service.YOUTUBE
        }
    }
    val playback by audio.state.collectAsState()
    val queueTexts = rememberTextStore()
    val queueStore = remember(queueTexts) { QueueStore(queueTexts) }
    QueuePersistence(audio, queueStore)
    val localLibrary = remember(queueTexts) { LocalLibrary(queueTexts) }
    val downloadPlatform = rememberDownloadPlatform()
    val downloadScope = rememberCoroutineScope()
    val downloads = remember(queueTexts, providers) { DownloadManager(queueTexts, downloadPlatform, downloadScope, providers) }
    SideEffect { DownloadsHolder.manager = downloads }
    val spotify = LocalSpotifyRemote.current
    // Our music or YouTube started playing: pause Spotify
    LaunchedEffect(playback.isPlaying) { if (playback.isPlaying) spotify?.pause() }
    LaunchedEffect(session.video) { if (session.video != null) spotify?.pause() }
    // Our music started: the YouTube video closes, two sounds do not play at once
    LaunchedEffect(playback.mediaId) {
        if (playback.mediaId != null && session.video != null) session.close()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
            .onSizeChanged { rootSize = it }
    ) {
        // Make the column background transparent (so there are no black bars)
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
                                // The expanded YouTube player: swiping is off
                                swipeEnabled = !(session.video != null && session.progress >= 0.999f),
                                onSelect = { service = it; showLikes = false },
                                onLikes = { service = Service.ALL; showLikes = true },
                                onPosition = { pagerPos = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    // The big YouTube player grows up to these bounds
                                    .onGloballyPositioned { session.screenBounds = it.boundsInWindow() },
                            ) { page, active ->
                                when (page) {
                                    null -> LibraryContent(likes, playlists, history, providers, audio, onOpenAuth, onOpenPlaylist = { openPlaylist = it }, local = localLibrary, downloads = downloads)
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
                                        history = history,
                                    )
                                }
                            }
                        }
                    }
                    Tab.SEARCH -> TrackListScreen(providers, audio, session, onOpenSpotify = { tab = Tab.HOME; service = Service.SPOTIFY }, likes = likes, downloads = downloads)
                    Tab.QUEUE -> QueueScreen(audio, playlists, onOpenAuth, providers, queueStore, downloads)
                    Tab.SETTINGS -> SettingsScreen(authRepo, onOpenAuth)
                }
                openPlaylist?.let { pl ->
                    PlaylistScreen(pl, playlists, providers, audio, onBack = { openPlaylist = null })
                }
            }

            // The place for the Spotify mini player (it is drawn by SpotifyExpandablePlayer)
            SpotifyMiniSlot(hidden = playback.title != null)

            // The mini player lies above the bottom bar
            NowPlayingBar(
                audio = audio,
                onOpen = onOpenPlayer,
                // Remove extra padding so the player sits flush
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            // FIX: remove the shadow of the NavigationBar and make it transparent
            if (!hideNavBar) NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp, // Removes the black shadow!
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

        // The only YouTube player: big on the YouTube screen, a floating window everywhere else
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

// TODO: move these captions to S (i18n) together with translations; for now they match the sketch.
private const val SETTINGS_GENERAL = "General"
private const val SETTINGS_SOUNDS = "Sounds"
private const val SETTINGS_STORAGE = "Storage cache"
private const val SETTINGS_STATUS = "System status"

@Composable
private fun SettingsScreen(authRepo: AuthRepository, onOpenAuth: () -> Unit) {
    val scope = rememberCoroutineScope()
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

        val authState by authRepo.state.collectAsState()
        var showChangePassword by remember { mutableStateOf(false) }
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
        Spacer(Modifier.height(20.dp))

        SettingsSection(SETTINGS_GENERAL) {
            GeneralSettings()
        }
        Spacer(Modifier.height(4.dp))

        // Placeholder: no equalizer / sound settings in the code yet
        SettingsSection(SETTINGS_SOUNDS) {
            SettingsGroup(listOf { shape -> SettingsRow(shape, "Coming soon") })
        }
        Spacer(Modifier.height(4.dp))

        // Placeholder: no cache settings in the code yet
        SettingsSection(SETTINGS_STORAGE) {
            SettingsGroup(listOf { shape -> SettingsRow(shape, "Coming soon") })
        }
        Spacer(Modifier.height(4.dp))

        SettingsSection(SETTINGS_STATUS) {
            SettingsGroup(
                Service.entries.filter { it != Service.ALL }.map { service ->
                    val row: @Composable (Shape) -> Unit = { shape ->
                        SettingsRow(
                            shape,
                            service.title,
                            subtitle = if (service.ready) S.available(service.note) else S.comingSoon(service.note),
                            tile = { Text(if (service.ready) "\u2713" else "\u2013", style = MaterialTheme.typography.titleLarge) },
                        )
                    }
                    row
                }
            )
        }
    }
}

/** A settings tab: a plain large row (no card, no arrow) with a softly rounded highlight; the group opens below it. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title, style = MaterialTheme.typography.headlineSmall) },
            // clip first, then clickable: the press highlight gets the rounded shape
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { open = !open },
        )
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 6.dp, bottom = 12.dp)) { content() }
        }
    }
}

/** The list from the reference: the outer corners are large, the corners between neighbours are small, 2dp gaps. */
@Composable
internal fun SettingsGroup(entries: List<@Composable (Shape) -> Unit>) {
    val big = 24.dp
    val small = 4.dp
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEachIndexed { i, entry ->
            val top = if (i == 0) big else small
            val bottom = if (i == entries.lastIndex) big else small
            entry(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        }
    }
}

/** A group item: title and subtitle on the left, an optional rounded-square tile on the right. */
@Composable
internal fun SettingsRow(
    shape: Shape,
    title: String,
    subtitle: String? = null,
    tile: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tile != null) {
                Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) { tile() }
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { body() }
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { body() }
    }
}

/** A group item with arbitrary content (for existing composables like the language picker). */
@Composable
private fun SettingsBox(shape: Shape, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun TileIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
}

private fun settingsVector(path: String): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

private val IconLock: ImageVector by lazy {
    settingsVector(
        "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10" +
                "c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6" +
                "c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
    )
}

private val IconLogout: ImageVector by lazy {
    settingsVector("M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z")
}
