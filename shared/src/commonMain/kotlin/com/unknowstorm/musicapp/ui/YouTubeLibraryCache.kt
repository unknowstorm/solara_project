package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import com.unknowstorm.musicapp.provider.youtube.YtOverview
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * "My YouTube" data of the running app. Memory only: never written to disk, gone with the process,
 * erased when YouTube is disconnected. It only saves the screen from reloading on every visit.
 */
internal object YouTubeLibraryCache {
    private val FRESH = 5.minutes

    var overview: YtOverview? = null
        private set
    private var overviewMark: TimeMark? = null

    private class Entry(val count: Int, val mark: TimeMark, val videos: List<YouTubeVideo>)
    private val lists = HashMap<String, Entry>()

    fun overviewFresh(): Boolean = overviewMark?.let { it.elapsedNow() < FRESH } == true

    fun putOverview(o: YtOverview) {
        overview = o
        overviewMark = TimeSource.Monotonic.markNow()
    }

    /** Videos of a playlist, only if the item count is unchanged and the copy is fresh. */
    fun playlist(id: String, count: Int): List<YouTubeVideo>? =
        lists[id]?.takeIf { it.count == count && it.mark.elapsedNow() < FRESH }?.videos

    fun putPlaylist(id: String, count: Int, videos: List<YouTubeVideo>) {
        lists[id] = Entry(count, TimeSource.Monotonic.markNow(), videos)
    }

    fun clear() {
        overview = null
        overviewMark = null
        lists.clear()
    }
}
