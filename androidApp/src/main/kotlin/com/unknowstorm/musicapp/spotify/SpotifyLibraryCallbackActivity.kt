package com.unknowstorm.musicapp.spotify

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Receives solara://spotify-library from the browser, hands the result to the import and brings the app back. */
class SpotifyLibraryCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpotifyRemoteImpl.current?.onLibraryRedirect(intent?.data)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(it)
        }
        finish()
    }
}
