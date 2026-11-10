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
import com.unknowstorm.musicapp.theme.SolaraTheme
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

// Сколько показываем вращение заставки, когда приложение готово: минимум и потолок (например, нет сети).
// Время считается уже после входа логотипа и первой сборки интерфейса.
private const val SPLASH_MIN_MS = 450L
private const val SPLASH_MAX_MS = 1000L

/** Всё тяжёлое, что создаётся в фоне, пока главный поток рисует заставку. */
private class AppBoot(
    val providers: List<MusicProvider>,
    val authRepo: AuthRepository,
)

@Composable
fun App() {
    SolaraTheme {
        Surface(Modifier.fillMaxSize()) {
            // Язык интерфейса: сохранённый выбор, иначе язык системы (по умолчанию английский)
            val languageStore = rememberTextStore()
            // Язык выставляем синхронно, до первой композиции экранов: иначе значения,
            // вычисляемые один раз, запомнят английский
            remember(languageStore) {
                I18n.language = AppLanguage.fromCode(languageStore.get(LANGUAGE_KEY)) ?: AppLanguage.system()
                Unit
            }
            // HTTP-клиенты, Supabase и провайдеры создаём в фоновом потоке: главный в это время свободен для анимации
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

            // Тяжёлую первую сборку интерфейса запускаем, когда готов фон и логотип уже вошёл:
            // подвисание (если оно будет) приходится на неподвижную заставку, а не на вращение.
            val ready = if (entered) boot else null

            Box(Modifier.fillMaxSize()) {
                if (ready != null) {
                    AppContent(ready)

                    val authState by ready.authRepo.state.collectAsState()
                    // Вход «готов», когда понятно, гость это или вошедший пользователь с загруженным профилем
                    val authSettled = when (val s = authState) {
                        AuthState.Loading -> false
                        is AuthState.SignedIn -> s.profile != null
                        else -> true
                    }
                    val settled by rememberUpdatedState(authSettled)
                    LaunchedEffect(Unit) {
                        // два кадра: тяжёлый первый кадр интерфейса уже нарисован, вращение стартует с чистого листа
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
    // История слушается сама: пишет трек в облако после 30 секунд воспроизведения
    val history = remember(audio) { HistoryRepository(authRepo, audio) }
    val authState by authRepo.state.collectAsState()
    val pending by authRepo.pending.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    val miniAnchor = remember { MiniPlayerAnchor() }
    val spotify = LocalSpotifyRemote.current
    val spotifyMini = remember { SpotifyMiniState() }
    val focusManager = LocalFocusManager.current
    // Снимаем фокус с поля поиска, иначе его «капля» остаётся поверх плеера
    LaunchedEffect(showPlayer) { if (showPlayer) focusManager.clearFocus() }
    // Вошли (в том числе через Google после возврата из браузера): закрываем экран входа
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
            // Мини-плеер и полный плеер — один компонент: плавно перетекает из одного в другой
            ExpandablePlayer(
                audio = audio,
                likes = likes,
                expanded = showPlayer,
                onExpandedChange = { showPlayer = it },
                // Гость нажал ♡: сворачиваем плеер и открываем вход
                onNeedAuth = {
                    showPlayer = false
                    showAuth = true
                },
            )
            // Мини-плеер Spotify: отдельный компонент, не связан с нашим AudioBackend-плеером
            if (spotify != null) {
                SpotifyExpandablePlayer(remote = spotify, audio = audio)
            }
            // Экран входа и регистрации поверх всего
            if (showAuth) {
                PlatformBackHandler(enabled = true, onBack = { showAuth = false })
                AuthScreen(repo = authRepo, onClose = { showAuth = false })
            }
            // После регистрации: «Ожидаем подтверждение письма».
            // Закрывается сам, когда письмо подтверждено и пользователь вошёл.
            pending?.let { p ->
                AwaitEmailScreen(
                    repo = authRepo,
                    pending = PendingSignUp(p.email, p.password),
                    onBack = { authRepo.cancelPending() },
                )
            }
            // Первый вход: ник, аватарка, сервисы, синхронизация палитры.
            // Закрывается сам, когда профиль сохранён (onboarded = true).
            val signedIn = authState as? AuthState.SignedIn
            val profile = signedIn?.profile
            if (profile != null && !profile.onboarded) {
                OnboardingScreen(repo = authRepo, profile = profile)
            }
        }
    }
}
