package com.unknowstorm.musicapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.unknowstorm.musicapp.player.rememberAudioBackend
import com.unknowstorm.musicapp.provider.jamendo.JamendoProvider
import com.unknowstorm.musicapp.theme.SolaraTheme
import com.unknowstorm.musicapp.ui.PlayerScreen
import com.unknowstorm.musicapp.ui.SplashScreen
import com.unknowstorm.musicapp.ui.MainScreen
import kotlinx.coroutines.delay

@Composable
fun App() {
    SolaraTheme {
        Surface(Modifier.fillMaxSize()) {
            val provider = remember { JamendoProvider(Secrets.JAMENDO_CLIENT_ID) }
            val audio = rememberAudioBackend()
            var showSplash by remember { mutableStateOf(true) }
            var showPlayer by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            // Снимаем фокус с поля поиска, иначе его «капля» остаётся поверх плеера
            LaunchedEffect(showPlayer) { if (showPlayer) focusManager.clearFocus() }
            LaunchedEffect(Unit) {
                delay(1600)
                showSplash = false
            }
            Box(Modifier.fillMaxSize()) {
                MainScreen(provider, audio, onOpenPlayer = { showPlayer = true })
                AnimatedVisibility(
                    visible = showPlayer,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    PlayerScreen(audio, onClose = { showPlayer = false })
                }
                AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(400))) {
                    SplashScreen()
                }
            }
        }
    }
}
