package com.unknowstorm.musicapp.player

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks that the server serves only in full (it answers 200 to a request for part of the file).
 * The playback service and the player live in one process, so a shared object is enough.
 */
object RangeSupport {
    private val noRange = ConcurrentHashMap.newKeySet<String>()

    fun set(key: String, unsupported: Boolean) {
        if (unsupported) noRange.add(key) else noRange.remove(key)
    }

    fun isNoRange(key: String): Boolean = key in noRange
}
