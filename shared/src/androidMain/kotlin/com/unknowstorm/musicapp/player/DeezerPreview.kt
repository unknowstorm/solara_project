package com.unknowstorm.musicapp.player

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Deezer preview links live about 15 minutes. If a link has expired, we fetch a new one by track id. */
object DeezerPreview {
    private const val MARGIN_S = 60L
    private val cache = ConcurrentHashMap<String, Uri>()
    private val exp = Regex("exp=(\\d+)")

    private fun expiry(uri: Uri): Long =
        exp.find(uri.getQueryParameter("hdnea").orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** null means "keep the original link". Called on the loading thread, so blocking is fine. */
    fun fresh(uri: Uri): Uri? {
        val id = uri.fragment?.removePrefix("deezer=")?.takeIf { it.isNotBlank() } ?: return null
        val now = System.currentTimeMillis() / 1000
        if (expiry(uri) - now > MARGIN_S) return null
        cache[id]?.takeIf { expiry(it) - now > MARGIN_S }?.let { return it }
        return try {
            val conn = URL("https://api.deezer.com/track/$id").openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val preview = JSONObject(body).optString("preview")
            if (preview.isBlank()) null else Uri.parse(preview).also { cache[id] = it }
        } catch (e: Exception) {
            android.util.Log.w("Solara", "deezer preview refresh failed: ${e.message}")
            null
        }
    }
}
