package com.unknowstorm.musicapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.player.rememberAudioBackend
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.provider.audius.AudiusProvider
import com.unknowstorm.musicapp.provider.jamendo.JamendoProvider
import com.unknowstorm.musicapp.theme.SolaraTheme
import com.unknowstorm.musicapp.ui.AuthScreen
import com.unknowstorm.musicapp.ui.ExpandablePlayer
import com.unknowstorm.musicapp.ui.LocalMiniPlayerAnchor
import com.unknowstorm.musicapp.ui.MainScreen
import com.unknowstorm.musicapp.ui.MiniPlayerAnchor
import com.unknowstorm.musicapp.ui.PlatformBackHandler
import com.unknowstorm.musicapp.ui.SplashScreen
import kotlinx.coroutines.delay

@Composable
fun App() {
    SolaraTheme {
        Surface(Modifier.fillMaxSize()) {
            val providers = remember {
                listOf<MusicProvider>(
                    JamendoProvider(Secrets.JAMENDO_CLIENT_ID),
                    AudiusProvider(Secrets.AUDIUS_API_KEY),
                )
            }
            val audio = rememberAudioBackend()
            val authRepo = remember { AuthRepository() }
            val authState by authRepo.state.collectAsState()
            var showSplash by remember { mutableStateOf(true) }
            var showPlayer by remember { mutableStateOf(false) }
            var showAuth by remember { mutableStateOf(false) }
            val miniAnchor = remember { MiniPlayerAnchor() }
            val focusManager = LocalFocusManager.current
            // Снимаем фокус с поля поиска, иначе его «капля» остаётся поверх плеера
            LaunchedEffect(showPlayer) { if (showPlayer) focusManager.clearFocus() }
            LaunchedEffect(Unit) {
                delay(1600)
                showSplash = false
            }
            // Вошли (в том числе через Google после возврата из браузера): закрываем экран входа
            LaunchedEffect(authState) {
                if (authState is AuthState.SignedIn) showAuth = false
            }
            CompositionLocalProvider(LocalMiniPlayerAnchor provides miniAnchor) {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        providers = providers,
                        audio = audio,
                        authRepo = authRepo,
                        onOpenPlayer = { showPlayer = true },
                        onOpenAuth = { showAuth = true },
                    )
                    // Мини-плеер и полный плеер — один компонент: плавно перетекает из одного в другой
                    ExpandablePlayer(
                        audio = audio,
                        expanded = showPlayer,
                        onExpandedChange = { showPlayer = it },
                    )
                    // Экран входа и регистрации поверх всего
                    if (showAuth) {
                        PlatformBackHandler(enabled = true, onBack = { showAuth = false })
                        AuthScreen(repo = authRepo, onClose = { showAuth = false })
                    }
                    AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(400))) {
                        SplashScreen()
                    }
                }
            }
        }
    }
}
