package com.unknowstorm.musicapp.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track

/** Треки, файлы которых сервис не отдаёт (404/410). Живёт до закрытия приложения. */
object DeadTracks {
    private var keys by mutableStateOf<Set<String>>(emptySet())

    fun mark(provider: ProviderId, id: String) {
        keys = keys + "${provider.name}:$id"
    }

    fun isDead(track: Track): Boolean = "${track.provider.name}:${track.id}" in keys
}
