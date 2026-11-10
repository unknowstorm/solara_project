package com.unknowstorm.musicapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.CompositionLocalProvider
import com.unknowstorm.musicapp.auth.handleAuthDeepLink
import com.unknowstorm.musicapp.spotify.LocalSpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyRemoteImpl

class MainActivity : ComponentActivity() {
    private lateinit var spotify: SpotifyRemoteImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The app may have been opened via a link solara://auth (email confirmation, Google)
        if (intent?.data != null) handleAuthDeepLink(intent)

        spotify = SpotifyRemoteImpl(this)
        setContent {
            CompositionLocalProvider(LocalSpotifyRemote provides spotify) { App() }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        spotify.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        spotify.autoConnect()
    }

    override fun onDestroy() {
        spotify.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) handleAuthDeepLink(intent)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
