package com.unknowstorm.musicapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.theme.SolaraTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    SolaraTheme {
        var playing by remember { mutableStateOf(true) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.safeContentPadding().fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Solara", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(32.dp))
                LinearWavyProgressIndicator(
                    progress = { 0.35f },
                    amplitude = { if (playing) 1f else 0f },
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { playing = !playing }) {
                    Text(if (playing) "Pause" else "Play")
                }
                Spacer(Modifier.height(16.dp))
                Text(if (playing) "Playing" else "Paused")
            }
        }
    }
}
