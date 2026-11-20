package com.unknowstorm.musicapp.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.ui.TextStore

/**
 * Tracks whose files the service does not serve (404/410, restricted, broken file).
 * They are hidden in the feeds and lists. The list is kept on the device for a week, so a dead track
 * does not come back after a restart; after that it gets one more chance (the service may have fixed it).
 */
object DeadTracks {
    private const val KEY = "dead_tracks_v1"
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX = 400

    private var keys by mutableStateOf<Set<String>>(emptySet())
    /** key -> the time it was marked (insertion order = oldest first). */
    private val stamps = LinkedHashMap<String, Long>()
    private var store: TextStore? = null

    /** Called once at app start: loads the saved list and from now on saves every change. */
    fun attach(store: TextStore) {
        if (this.store === store) return
        this.store = store
        val now = store.nowMillis()
        store.get(KEY)?.lineSequence()?.forEach { line ->
            val i = line.lastIndexOf('|')
            if (i <= 0) return@forEach
            val t = line.substring(i + 1).toLongOrNull() ?: return@forEach
            if (now - t in 0 until TTL_MS) stamps.putIfAbsent(line.substring(0, i), t)
        }
        // marks made before the store was attached get the current time
        stamps.entries.forEach { if (it.value < 0) it.setValue(now) }
        keys = stamps.keys.toSet()
        save()
    }

    fun mark(provider: ProviderId, id: String) {
        val k = "${provider.name}:$id"
        if (k in stamps) return
        stamps[k] = store?.nowMillis() ?: -1L
        while (stamps.size > MAX) stamps.remove(stamps.keys.first())
        keys = stamps.keys.toSet()
        save()
    }

    fun isDead(track: Track): Boolean = "${track.provider.name}:${track.id}" in keys

    private fun save() {
        val s = store ?: return
        s.set(KEY, stamps.entries.filter { it.value >= 0 }.joinToString("\n") { "${it.key}|${it.value}" })
    }
}
