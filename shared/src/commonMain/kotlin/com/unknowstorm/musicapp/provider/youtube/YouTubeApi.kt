package com.unknowstorm.musicapp.provider.youtube

import com.unknowstorm.musicapp.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Видео YouTube для раздела YouTube.
 * Намеренно не Track: данные YouTube нельзя класть в лайки, плейлисты, историю и рекомендации.
 */
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
    // Только в памяти и только пока запущено приложение (правила YouTube: данные API нельзя хранить дольше 30 дней)
    private val cache = LinkedHashMap<String, List<YouTubeVideo>>()

    /** Результат поиска из кэша приложения (без запроса и без траты квоты), если он есть. */
    fun cachedSearch(query: String): List<YouTubeVideo>? = cache["search:${query.trim().lowercase()}"]

    /** Поиск музыкальных видео, которые разрешено встраивать. Один поиск стоит 100 единиц квоты. */
    suspend fun search(query: String): List<YouTubeVideo> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val key = "search:${q.lowercase()}"
        cache[key]?.let { return it }
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

    /** «Популярная музыка» через videos.list: стоит 1 единицу квоты. Может быть недоступна для региона. */
    suspend fun popularMusic(region: String = "UA"): List<YouTubeVideo> {
        val key = "popular:$region"
        cache[key]?.let { return it }
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
                if (quota) "Дневной лимит запросов YouTube исчерпан. Попробуйте завтра."
                else "Ошибка YouTube API (${response.status.value})"
            )
        }
        return response
    }

    private fun store(key: String, value: List<YouTubeVideo>): List<YouTubeVideo> {
        cache[key] = value
        while (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
        return value
    }

    // Берём только публичные, встраиваемые видео, не «для детей» (их показываем только на youtube.com)
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
                // medium = 320x180 без чёрных полос, нужный формат 16:9
                thumbnailUrl = thumbs?.medium?.url ?: thumbs?.high?.url ?: thumbs?.default?.url,
                durationMs = parseIsoDuration(v.contentDetails?.duration),
            )
        }

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val MAX_CACHED = 30

        /** Один экземпляр на приложение, чтобы кэш не терялся при смене экранов. */
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
