package com.unknowstorm.musicapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import com.unknowstorm.musicapp.player.rememberAudioBackend
import com.unknowstorm.musicapp.provider.jamendo.JamendoProvider
import com.unknowstorm.musicapp.theme.SolaraTheme
import com.unknowstorm.musicapp.ui.TrackListScreen
import com.unknowstorm.musicapp.ui.SplashScreen

@Composable
fun App() {
    SolaraTheme {
        Surface(Modifier.fillMaxSize()) {
            val provider = remember { JamendoProvider(Secrets.JAMENDO_CLIENT_ID) }
            val audio = rememberAudioBackend()
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1600)
                showSplash = false
            }
            Box(Modifier.fillMaxSize()) {
                TrackListScreen(provider, audio)
                AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(400))) {
                    SplashScreen()
                }
            }
        }
    }
}

