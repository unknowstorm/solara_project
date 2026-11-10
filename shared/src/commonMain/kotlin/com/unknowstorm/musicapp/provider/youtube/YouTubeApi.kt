package com.unknowstorm.musicapp.provider.youtube

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CancellationException
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import com.unknowstorm.musicapp.ui.TextStore

/**
 * A YouTube video for the YouTube section.
 * Deliberately not a Track: YouTube data must not go into likes, playlists, history or recommendations.
 */
@Serializable
data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
)

class YouTubeException(message: String) : Exception(message)

class YouTubeApi(
    private val apiKey: String,
    private val client: HttpClient = createClient(),
) {
    // In memory only and only while the app is running (YouTube rules: API data must not be stored longer than 30 days)
    private val cache = LinkedHashMap<String, List<YouTubeVideo>>()

    /** A search result from the app cache (no request and no quota spent), if there is one. */
    fun cachedSearch(query: String): List<YouTubeVideo>? = cachedByKey("search:${query.trim().lowercase()}")

    private var cacheDay = Long.MIN_VALUE

    /** Memory and disk with a quota-day check: after the quota reset (midnight Pacific Time, 10:00 Kyiv time) old data is not used. */
    private fun cachedByKey(key: String): List<YouTubeVideo>? {
        rollover()
        cache[key]?.let { return it }
        val entry = readDisk()[key] ?: return null
        cache[key] = entry.videos
        return entry.videos
    }

    private fun rollover() {
        val store = disk ?: return
        val today = store.quotaDay(store.nowMillis())
        if (cacheDay != today) { cache.clear(); cacheDay = today }
    }

    /**
     * A shared daily cache of selections on the server (Supabase, updated once a day after the YouTube quota reset).
     * Read with one request: it does not spend YouTube quota. If the server is unavailable or empty, nothing changes.
     */
    suspend fun syncShared(): Boolean {
        val store = disk ?: return false
        rollover()
        val today = store.quotaDay(store.nowMillis())
        return try {
            val rows: List<SharedRow> = client.get("$SHARED_URL/rest/v1/yt_cache") {
                parameter("select", "key,videos,quota_day")
                header("apikey", SHARED_KEY)
            }.body()
            var any = false
            for (r in rows) {
                // Yesterday's data is accepted too: minutes pass between the quota reset and the server update
                if (r.quotaDay < today - 1 || r.videos.isEmpty()) continue
                cache[r.key] = r.videos
                if (r.key.startsWith("search:") || r.key.startsWith("popular:")) saveToDisk(r.key, r.videos)
                any = true
            }
            any
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private var disk: TextStore? = null

    /** Connects persistent storage: searches live in it until the daily YouTube quota resets (midnight Pacific Time). */
    fun attach(store: TextStore) {
        if (disk == null) disk = store
    }

    private fun readDisk(): Map<String, CacheEntry> {
        val store = disk ?: return emptyMap()
        val raw = store.get(DISK_KEY) ?: return emptyMap()
        val today = store.quotaDay(store.nowMillis())
        return runCatching { diskJson.decodeFromString<Map<String, CacheEntry>>(raw) }
            .getOrDefault(emptyMap())
            .filterValues { store.quotaDay(it.savedAt) == today }
    }

    private fun saveToDisk(key: String, value: List<YouTubeVideo>) {
        val store = disk ?: return
        val all = readDisk().toMutableMap()
        all[key] = CacheEntry(store.nowMillis(), value)
        while (all.size > MAX_CACHED) {
            val oldest = all.minByOrNull { it.value.savedAt }?.key ?: break
            all.remove(oldest)
        }
        runCatching { store.set(DISK_KEY, diskJson.encodeToString(all)) }
    }

    /** Search for music videos that may be embedded. One search costs 100 quota units. */
    suspend fun search(query: String): List<YouTubeVideo> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val key = "search:${q.lowercase()}"
        cachedSearch(q)?.let { return it }
        val found: YtSearchResponse = request("search") {
            parameter("part", "id")
            parameter("type", "video")
            parameter("videoCategoryId", "10")
            parameter("videoEmbeddable", "true")
            parameter("videoSyndicated", "true")
            parameter("maxResults", 20)
            parameter("q", q)
        }.body()
        val ids = found.items.mapNotNull { it.id?.videoId }
        return store(key, details(ids))
    }

    /** "Popular music" via videos.list: costs 1 quota unit. May be unavailable for a region. */
    suspend fun popularMusic(region: String = "UA"): List<YouTubeVideo> {
        val key = "popular:$region"
        cachedByKey(key)?.let { return it }
        val response: YtVideosResponse = request("videos") {
            parameter("part", "snippet,contentDetails,status")
            parameter("chart", "mostPopular")
            parameter("videoCategoryId", "10")
            parameter("regionCode", region)
            parameter("maxResults", 25)
        }.body()
        return store(key, response.toVideos())
    }

    private suspend fun details(ids: List<String>): List<YouTubeVideo> {
        if (ids.isEmpty()) return emptyList()
        val response: YtVideosResponse = request("videos") {
            parameter("part", "snippet,contentDetails,status")
            parameter("id", ids.joinToString(","))
        }.body()
        return response.toVideos().sortedBy { ids.indexOf(it.id) }
    }

    private suspend fun request(path: String, block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val response = client.get("$BASE_URL/$path") {
            parameter("key", apiKey)
            block()
        }
        if (!response.status.isSuccess()) {
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            val quota = text.contains("quotaExceeded") || text.contains("dailyLimitExceeded")
            throw YouTubeException(
                if (quota) S.theDailyYoutubeRequestLimit
                else S.youtubeApiError(response.status.value)
            )
        }
        return response
    }

    private fun store(key: String, value: List<YouTubeVideo>): List<YouTubeVideo> {
        cache[key] = value
        while (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
        if (key.startsWith("search:") || key.startsWith("popular:")) saveToDisk(key, value)
        return value
    }

    // Take only public, embeddable videos that are not "made for kids" (those are shown only on youtube.com)
    private fun YtVideosResponse.toVideos(): List<YouTubeVideo> =
        items.filter { v ->
            val s = v.status
            s?.embeddable != false && s?.madeForKids != true && (s?.privacyStatus ?: "public") == "public"
        }.map { v ->
            val thumbs = v.snippet?.thumbnails
            YouTubeVideo(
                id = v.id,
                title = v.snippet?.title.orEmpty(),
                channel = v.snippet?.channelTitle.orEmpty(),
                // medium = 320x180 without black bars, the needed 16:9 format
                thumbnailUrl = thumbs?.medium?.url ?: thumbs?.high?.url ?: thumbs?.default?.url,
                durationMs = parseIsoDuration(v.contentDetails?.duration),
            )
        }

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val MAX_CACHED = 30
        private const val DISK_KEY = "yt_search_cache_v1"
        // The public (publishable) Supabase key: it is made to be embedded in an app, there are no secrets in it
        private const val SHARED_URL = "https://llyjvonszkahqmyxcqbi.supabase.co"
        private const val SHARED_KEY = "sb_publishable_kPXbz4ayg5E2GszNuzdy5g_SmcAhuqn"
        private val diskJson = Json { ignoreUnknownKeys = true }

        /** One instance per app so the cache is not lost when screens change. */
        val shared: YouTubeApi by lazy { YouTubeApi(Secrets.YOUTUBE_API_KEY) }

        private fun createClient() = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
        }
    }
}

internal fun parseIsoDuration(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    val m = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""").matchEntire(iso) ?: return 0L
    val (d, h, mi, s) = m.destructured
    fun n(x: String) = x.toLongOrNull() ?: 0L
    return ((n(d) * 24 + n(h)) * 60 + n(mi)) * 60_000 + n(s) * 1000
}

@Serializable
internal data class YtSearchResponse(val items: List<YtSearchItem> = emptyList())

@Serializable
internal data class YtSearchItem(val id: YtId? = null)

@Serializable
internal data class YtId(val videoId: String? = null)

@Serializable
internal data class YtVideosResponse(val items: List<YtVideo> = emptyList())

@Serializable
internal data class YtVideo(
    val id: String,
    val snippet: YtSnippet? = null,
    val contentDetails: YtContentDetails? = null,
    val status: YtStatus? = null,
)

@Serializable
internal data class YtSnippet(
    val title: String = "",
    val channelTitle: String = "",
    val thumbnails: YtThumbs? = null,
)

@Serializable
internal data class YtThumbs(
    val default: YtThumb? = null,
    val medium: YtThumb? = null,
    val high: YtThumb? = null,
)

@Serializable
internal data class YtThumb(val url: String? = null)

@Serializable
internal data class YtContentDetails(val duration: String? = null)

@Serializable
internal data class YtStatus(
    val embeddable: Boolean? = null,
    val madeForKids: Boolean? = null,
    val privacyStatus: String? = null,
)

@Serializable
internal data class CacheEntry(val savedAt: Long, val videos: List<YouTubeVideo>)

@Serializable
internal data class SharedRow(
    val key: String,
    val videos: List<YouTubeVideo> = emptyList(),
    @SerialName("quota_day") val quotaDay: Long = 0,
)
