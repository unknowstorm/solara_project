package com.unknowstorm.musicapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.history.HistoryRepository
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.player.rememberAudioBackend
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.provider.audius.AudiusProvider
import com.unknowstorm.musicapp.provider.deezer.DeezerProvider
import com.unknowstorm.musicapp.provider.jamendo.JamendoProvider
import com.unknowstorm.musicapp.spotify.LocalSpotifyRemote
import com.unknowstorm.musicapp.theme.PALETTE_KEY
import com.unknowstorm.musicapp.theme.PaletteSettings
import com.unknowstorm.musicapp.theme.SolaraTheme
import com.unknowstorm.musicapp.ui.ISLAND_KEY
import com.unknowstorm.musicapp.ui.IslandSettings
import com.unknowstorm.musicapp.ui.LocalSpotifyMini
import com.unknowstorm.musicapp.ui.SpotifyExpandablePlayer
import com.unknowstorm.musicapp.ui.SpotifyMiniState
import com.unknowstorm.musicapp.ui.AuthScreen
import com.unknowstorm.musicapp.ui.AwaitEmailScreen
import com.unknowstorm.musicapp.ui.ExpandablePlayer
import com.unknowstorm.musicapp.ui.LocalMiniPlayerAnchor
import com.unknowstorm.musicapp.ui.MainScreen
import com.unknowstorm.musicapp.ui.MiniPlayerAnchor
import com.unknowstorm.musicapp.ui.OnboardingScreen
import com.unknowstorm.musicapp.ui.PendingSignUp
import com.unknowstorm.musicapp.ui.PlatformBackHandler
import com.unknowstorm.musicapp.i18n.AppLanguage
import com.unknowstorm.musicapp.i18n.I18n
import com.unknowstorm.musicapp.i18n.LANGUAGE_KEY
import com.unknowstorm.musicapp.ui.SplashScreen
import com.unknowstorm.musicapp.ui.rememberTextStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// How long the splash spinning is shown once the app is ready: minimum and ceiling (e.g. no network).
// The time is counted after the logo has entered and the first UI composition.
private const val SPLASH_MIN_MS = 450L
private const val SPLASH_MAX_MS = 1000L

/** Everything heavy that is created in the background while the main thread draws the splash. */
private class AppBoot(
    val providers: List<MusicProvider>,
    val authRepo: AuthRepository,
)

@Composable
fun App() {
    // Saved palette is restored before the theme is first composed, so the colors do not blink
    val paletteStore = rememberTextStore()
    remember(paletteStore) { PaletteSettings.restore(paletteStore.get(PALETTE_KEY)); Unit }
    // Порядок сервисов в островке тоже восстанавливается до первой композиции, чтобы панель не мигала
    remember(paletteStore) { IslandSettings.restore(paletteStore.get(ISLAND_KEY)); Unit }
    SolaraTheme(palette = PaletteSettings.choice) {
        Surface(Modifier.fillMaxSize()) {
            // UI language: the saved choice, otherwise the system language (English by default)
            val languageStore = rememberTextStore()
            // The language is set synchronously, before the first composition of the screens: otherwise values
            // computed once would remember the default language
            remember(languageStore) {
                I18n.language = AppLanguage.fromCode(languageStore.get(LANGUAGE_KEY)) ?: AppLanguage.system()
                Unit
            }
            // HTTP clients, Supabase and providers are created on a background thread: the main one is free for animation meanwhile
            val boot by produceState<AppBoot?>(initialValue = null) {
                value = withContext(Dispatchers.Default) {
                    AppBoot(
                        providers = listOf<MusicProvider>(
                            JamendoProvider(Secrets.JAMENDO_CLIENT_ID),
                            AudiusProvider(Secrets.AUDIUS_API_KEY),
                            DeezerProvider(),
                        ),
                        authRepo = AuthRepository(),
                    )
                }
            }
            var entered by remember { mutableStateOf(false) }
            var spinning by remember { mutableStateOf(false) }
            var leaving by remember { mutableStateOf(false) }
            var splashDone by remember { mutableStateOf(false) }

            // The heavy first UI composition is started when the background is ready and the logo has already entered:
            // a stall (if any) falls on the still splash, not on the spinning
            val ready = if (entered) boot else null

            Box(Modifier.fillMaxSize()) {
                if (ready != null) {
                    AppContent(ready)

                    val authState by ready.authRepo.state.collectAsState()
                    // Sign-in is "ready" when it is clear whether this is a guest or a signed-in user with a loaded profile
                    val authSettled = when (val s = authState) {
                        AuthState.Loading -> false
                        is AuthState.SignedIn -> s.profile != null
                        else -> true
                    }
                    val settled by rememberUpdatedState(authSettled)
                    LaunchedEffect(Unit) {
                        // two frames: the heavy first UI frame is already drawn, the spinning starts from a clean slate
                        withFrameNanos { }
                        withFrameNanos { }
                        spinning = true
                        delay(SPLASH_MIN_MS)
                        withTimeoutOrNull(SPLASH_MAX_MS - SPLASH_MIN_MS) {
                            snapshotFlow { settled }.first { it }
                        }
                        leaving = true
                    }
                }
                if (!splashDone) {
                    SplashScreen(
                        spinning = spinning,
                        leaving = leaving,
                        onEntered = { entered = true },
                        onFinished = { splashDone = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppContent(boot: AppBoot) {
    val providers = boot.providers
    val authRepo = boot.authRepo
    val audio = rememberAudioBackend()
    val likes = remember { LikesRepository(authRepo) }
    val playlists = remember { PlaylistsRepository(authRepo) }
    // History listens by itself: writes the track to the cloud after 30 seconds of playback
    val history = remember(audio) { HistoryRepository(authRepo, audio) }
    val authState by authRepo.state.collectAsState()
    val pending by authRepo.pending.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    val miniAnchor = remember { MiniPlayerAnchor() }
    val spotify = LocalSpotifyRemote.current
    val spotifyMini = remember { SpotifyMiniState() }
    val focusManager = LocalFocusManager.current
    // Clear focus from the search field, otherwise its "drop" stays on top of the player
    LaunchedEffect(showPlayer) { if (showPlayer) focusManager.clearFocus() }
    // Signed in (including via Google after returning from the browser): close the sign-in screen
    LaunchedEffect(authState) {
        if (authState is AuthState.SignedIn) showAuth = false
    }
    CompositionLocalProvider(
        LocalMiniPlayerAnchor provides miniAnchor,
        LocalSpotifyMini provides spotifyMini,
    ) {
        Box(Modifier.fillMaxSize()) {
            MainScreen(
                providers = providers,
                audio = audio,
                authRepo = authRepo,
                likes = likes,
                playlists = playlists,
                history = history,
                onOpenPlayer = { showPlayer = true },
                onOpenAuth = { showAuth = true },
            )
            // Mini player and full player are one component: it flows smoothly from one into the other
            ExpandablePlayer(
                audio = audio,
                likes = likes,
                playlists = playlists,
                expanded = showPlayer,
                onExpandedChange = { showPlayer = it },
                // A guest tapped ♡: collapse the player and open sign-in
                onNeedAuth = {
                    showPlayer = false
                    showAuth = true
                },
            )
            // Spotify mini player: a separate component, not connected to our AudioBackend player
            if (spotify != null) {
                SpotifyExpandablePlayer(remote = spotify, audio = audio)
            }
            // Sign-in and registration screen on top of everything
            if (showAuth) {
                PlatformBackHandler(enabled = true, onBack = { showAuth = false })
                AuthScreen(repo = authRepo, onClose = { showAuth = false })
            }
            // After registration: "Waiting for email confirmation".
            // Closes by itself when the email is confirmed and the user is signed in.
            pending?.let { p ->
                AwaitEmailScreen(
                    repo = authRepo,
                    pending = PendingSignUp(p.email, p.password),
                    onBack = { authRepo.cancelPending() },
                )
            }
            // First sign-in: nickname, avatar, services, palette sync.
            // Closes by itself when the profile is saved (onboarded = true).
            val signedIn = authState as? AuthState.SignedIn
            val profile = signedIn?.profile
            if (profile != null && !profile.onboarded) {
                OnboardingScreen(repo = authRepo, profile = profile)
            }
        }
    }
}
