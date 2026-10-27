package com.unknowstorm.musicapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.unknowstorm.musicapp.provider.jamendo.JamendoProvider
import com.unknowstorm.musicapp.ui.TrackListScreen

@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val provider = remember { JamendoProvider(Secrets.JAMENDO_CLIENT_ID) }
            TrackListScreen(provider)
        }
    }
}
