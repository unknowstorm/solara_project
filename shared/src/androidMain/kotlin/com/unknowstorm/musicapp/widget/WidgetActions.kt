package com.unknowstorm.musicapp.widget

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.unknowstorm.musicapp.player.PlaybackService

/**
 * Sends a command to the player. If the service is running, uses its player directly;
 * otherwise connects a MediaController (which also starts the service).
 */
private fun sendToPlayer(context: Context, command: (Player) -> Unit) {
    val main = Handler(Looper.getMainLooper())
    val live = WidgetStore.player
    if (live != null) {
        main.post { command(live) }
        return
    }
    val app = context.applicationContext
    val future = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()
    future.addListener({
        try {
            command(future.get())
        } catch (_: Exception) {
        }
        // give the session time to receive the command before disconnecting
        main.postDelayed({ MediaController.releaseFuture(future) }, 2000)
    }, ContextCompat.getMainExecutor(app))
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendToPlayer(context) { p -> if (p.isPlaying) p.pause() else p.play() }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendToPlayer(context) { p -> p.seekToNextMediaItem() }
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendToPlayer(context) { p -> p.seekToPreviousMediaItem() }
    }
}
