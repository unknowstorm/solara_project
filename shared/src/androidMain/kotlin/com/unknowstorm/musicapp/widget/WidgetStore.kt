package com.unknowstorm.musicapp.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the widgets show. */
data class WidgetData(
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val cover: Bitmap? = null,
    val isPlaying: Boolean = false,
)

/**
 * Process-wide state shared by PlaybackService (writes) and the widgets (read).
 * The service and the widget callbacks live in the same process, so a plain object is enough.
 */
internal object WidgetStore {
    val data = MutableStateFlow(WidgetData())

    /** The ExoPlayer of PlaybackService; null while the service is not running. */
    @Volatile
    var player: Player? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Redraws every placed widget. */
    fun push(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { SolaraWidget2x2().updateAll(app) }
            runCatching { SolaraWidget4x1().updateAll(app) }
        }
    }

    fun launchBackground(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}
