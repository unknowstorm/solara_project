package com.unknowstorm.musicapp.provider

import com.unknowstorm.musicapp.model.Track

/** A provider that can give the lyrics of its own tracks. The text is only shown, never stored. */
interface LyricsSource {
    /** The lyrics, or null if the author did not publish any. Throws on network errors. */
    suspend fun fetchLyrics(track: Track): String?
}

/** JamendoProvider puts itself here when it is created, so the player UI can reach it. */
object LyricsRegistry {
    var jamendo: LyricsSource? = null
}
