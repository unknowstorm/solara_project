package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import java.util.prefs.Preferences

@Composable
actual fun rememberFlagStore(): FlagStore = remember {
    val prefs = Preferences.userRoot().node("com/unknowstorm/musicapp")
    object : FlagStore {
        override fun get(key: String): Boolean = prefs.getBoolean(key, false)
        override fun set(key: String, value: Boolean) {
            prefs.putBoolean(key, value)
        }
    }
}

@Composable
actual fun rememberExternalLinkOpener(): (String) -> Unit = remember {
    { url: String ->
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (e: Exception) {
            println("Solara: could not open link: ${e.message}")
        }
    }
}

@Composable
actual fun YouTubePlayerView(videoId: String, modifier: Modifier, onError: (Int) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            S.theBuiltInYoutubePlayer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@androidx.compose.runtime.Composable
actual fun rememberTextStore(): TextStore = androidx.compose.runtime.remember {
    val dir = java.io.File(System.getProperty("user.home"), ".solara").also { it.mkdirs() }
    object : TextStore {
        override fun get(key: String): String? =
            runCatching { java.io.File(dir, "$key.txt").takeIf { it.exists() }?.readText() }.getOrNull()
        override fun set(key: String, value: String) {
            runCatching { java.io.File(dir, "$key.txt").writeText(value) }
        }
        override fun nowMillis(): Long = System.currentTimeMillis()
        override fun quotaDay(millis: Long): Long {
            val tz = java.util.TimeZone.getTimeZone("America/Los_Angeles")
            return Math.floorDiv(millis + tz.getOffset(millis), 86_400_000L)
        }
    }
}
