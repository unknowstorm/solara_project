package com.unknowstorm.musicapp.provider

import com.unknowstorm.musicapp.model.Track

/** A source that can give an address for saving a track to the phone (only if the author allowed it). */
interface DownloadSource {
    /** The address of the file, or null if downloading is not allowed for this track. */
    suspend fun resolveDownload(track: Track): String?
}

/**
 * Lets a provider find out that a track is already saved on the phone, without knowing where the files live.
 * The download manager fills it in; the provider asks it before going to the network.
 */
object OfflineLookup {
    var lookup: ((Track) -> String?)? = null
    fun fileUri(track: Track): String? = lookup?.invoke(track)
}
