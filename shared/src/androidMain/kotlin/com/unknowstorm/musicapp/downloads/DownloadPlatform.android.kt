package com.unknowstorm.musicapp.downloads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
actual fun rememberDownloadPlatform(): DownloadPlatform {
    val context = LocalContext.current
    return remember { AndroidDownloadPlatform(context) }
}

/** Files go to the private folder of the app: other apps and the user's file manager do not see them. */
class AndroidDownloadPlatform(context: Context) : DownloadPlatform {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "downloads").apply { mkdirs() }

    override suspend fun download(url: String, fileName: String, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val target = File(dir, fileName)
            val part = File(dir, "$fileName.part")
            var done = false
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) error("could not save the file")
                done = true
                target.absolutePath
            } finally {
                conn?.disconnect()
                if (!done) part.delete() // a broken or cancelled download leaves nothing behind
            }
        }

    override fun exists(path: String) = File(path).exists()
    override fun size(path: String) = File(path).length()
    override fun delete(path: String) {
        File(path).delete()
    }

    override fun toast(text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, text, Toast.LENGTH_SHORT).show() }
    }
}
