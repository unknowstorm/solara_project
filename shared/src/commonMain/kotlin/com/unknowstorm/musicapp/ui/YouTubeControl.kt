package com.unknowstorm.musicapp.ui

/**
 * Bridge between our buttons and the single embedded YouTube player.
 * The platform (Android) provides send and calls onState when the player state changes.
 * Commands: "play" and "pause" (methods of the official IFrame Player API).
 */
object YouTubeControl {
    var send: ((String) -> Unit)? = null
    var onState: ((Int) -> Unit)? = null
    /** Position and duration in seconds: the player reports them several times per second. */
    var onTime: ((Double, Double) -> Unit)? = null
}
