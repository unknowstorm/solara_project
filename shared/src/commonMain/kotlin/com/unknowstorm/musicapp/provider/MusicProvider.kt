package com.unknowstorm.musicapp.provider

import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track

interface MusicProvider {
    val id: ProviderId
    suspend fun search(query: String): List<Track>
    suspend fun resolveStream(track: Track): String
}
