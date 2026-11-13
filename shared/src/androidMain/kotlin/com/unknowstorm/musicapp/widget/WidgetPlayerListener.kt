package com.unknowstorm.musicapp.widget

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Attached to the ExoPlayer in PlaybackService: copies track and play state into [WidgetStore]
 * and asks the widgets to redraw. Must be called on the player's (main) thread.
 */
internal class WidgetPlayerListener(context: Context) : Player.Listener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var coverJob: Job? = null
    private var lastKey: String? = null

    override fun onEvents(player: Player, events: Player.Events) {
        if (!events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            )
        ) return
        sync(player.currentMediaItem, player.isPlaying)
    }

    fun sync(item: MediaItem?, isPlaying: Boolean) {
        val meta = item?.mediaMetadata
        val title = meta?.title?.toString()
        val artist = meta?.artist?.toString()
        val url = meta?.artworkUri?.toString()
        val key = "${item?.mediaId}|$url"

        val previous = WidgetStore.data.value
        val sameTrack = key == lastKey
        WidgetStore.data.value = WidgetData(
            title = title,
            artist = artist,
            artworkUrl = url,
            cover = if (sameTrack) previous.cover else null,
            isPlaying = isPlaying,
        )
        WidgetStore.push(appContext)

        if (!sameTrack) {
            lastKey = key
            coverJob?.cancel()
            coverJob = scope.launch {
                val bitmap = WidgetCovers.load(appContext, url)
                val now = WidgetStore.data.value
                // the track may have changed while the cover was loading
                if (now.artworkUrl == url) {
                    WidgetStore.data.value = now.copy(cover = bitmap)
                    WidgetStore.push(appContext)
                }
            }
        }
    }

    fun release() {
        coverJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }
}
