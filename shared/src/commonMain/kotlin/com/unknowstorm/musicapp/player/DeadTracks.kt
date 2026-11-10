package com.unknowstorm.musicapp.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track

/** Tracks whose files the service does not serve (404/410). Lives until the app is closed. */
object DeadTracks {
    private var keys by mutableStateOf<Set<String>>(emptySet())

    fun mark(provider: ProviderId, id: String) {
        keys = keys + "${provider.name}:$id"
    }

    fun isDead(track: Track): Boolean = "${track.provider.name}:${track.id}" in keys
}
