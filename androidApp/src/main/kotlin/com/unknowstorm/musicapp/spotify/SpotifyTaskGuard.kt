package com.unknowstorm.musicapp.spotify

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/** Fires when Solara is swiped away from recents: pauses Spotify even if the activity did not get onDestroy. */
class SpotifyTaskGuard : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("SpotifyGuard", "task removed, current=${SpotifyRemoteImpl.current != null}")
        SpotifyRemoteImpl.current?.pauseNow()
        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 700)
        super.onTaskRemoved(rootIntent)
    }
}
