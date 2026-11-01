package com.unknowstorm.musicapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.unknowstorm.musicapp.auth.handleAuthDeepLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Приложение могли открыть по ссылке solara://auth (подтверждение почты, Google)
        if (intent?.data != null) handleAuthDeepLink(intent)

        setContent {
            App()
        }
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
