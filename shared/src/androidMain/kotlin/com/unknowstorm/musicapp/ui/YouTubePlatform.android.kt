package com.unknowstorm.musicapp.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun rememberFlagStore(): FlagStore {
    val context = LocalContext.current
    return remember(context) {
        val prefs = context.applicationContext.getSharedPreferences("solara_flags", Context.MODE_PRIVATE)
        object : FlagStore {
            override fun get(key: String): Boolean = prefs.getBoolean(key, false)
            override fun set(key: String, value: Boolean) {
                prefs.edit().putBoolean(key, value).apply()
            }
        }
    }
}

@Composable
actual fun rememberExternalLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                // нет приложения для ссылки
            }
        }
    }
}

/** Принимает коды ошибок плеера из JavaScript и передаёт их в основной поток. */
class YouTubePlayerBridge(
    private val onError: (Int) -> Unit,
    private val onState: (Int) -> Unit = {},
    private val onTime: (Double, Double) -> Unit = { _, _ -> },
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        handler.post { onError(code) }
    }

    @JavascriptInterface
    fun onPlayerState(state: Int) {
        handler.post { onState(state) }
    }

    @JavascriptInterface
    fun onPlayerTime(position: Double, duration: Double) {
        handler.post { onTime(position, duration) }
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d("SolaraYT", message)
    }
}

/** Полноэкранный режим плеера: стандартная кнопка в плеере YouTube разворачивает видео на всё окно. */
private class FullscreenChrome(private val activity: Activity?) : WebChromeClient() {
    private var customView: View? = null
    private var callback: CustomViewCallback? = null

    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
        Log.d("SolaraYT", "console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
        return true
    }

    override fun onShowCustomView(view: View, requestedCallback: CustomViewCallback) {
        val act = activity
        val host = act?.window?.decorView as? FrameLayout
        if (act == null || host == null || customView != null) {
            requestedCallback.onCustomViewHidden()
            return
        }
        customView = view
        callback = requestedCallback
        host.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        callback?.onCustomViewHidden()
        callback = null
        activity?.let { act ->
            WindowCompat.getInsetsController(act.window, act.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun exitFullscreen() {
        if (customView != null) onHideCustomView()
    }
}

private fun playerHtml(videoId: String, origin: String): String {
    val safeId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>
  html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
  #wrap { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: #000; }
  #wrap iframe, #player { position: absolute !important; left: 0; top: 0; width: 100% !important; height: 100% !important; border: 0; }
</style>
</head>
<body>
<div id="wrap"><div id="player"></div></div>
<script>
  var player; var pendingId = null;
  function solaraLoad(id) { if (player && player.loadVideoById) { player.loadVideoById(id); } else { pendingId = id; } }
  window.onerror = function (m, s, l) { SolaraBridge.log('js error: ' + m + ' @' + l); };
  SolaraBridge.log('page start');
  var tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  document.head.appendChild(tag);
  function onYouTubeIframeAPIReady() {
    SolaraBridge.log('api ready');
    player = new YT.Player('player', {
      width: window.innerWidth,
      height: window.innerHeight,
      videoId: '$safeId',
      playerVars: { controls: 0, playsinline: 1, autoplay: 1, origin: '$origin' },
      events: {
        onReady: function () {
          SolaraBridge.log('player ready');
          if (pendingId) { player.loadVideoById(pendingId); pendingId = null; }
          var f = document.getElementsByTagName('iframe')[0];
          if (f) { var r = f.getBoundingClientRect(); SolaraBridge.log('iframe ' + r.width + 'x' + r.height + ', window ' + window.innerWidth + 'x' + window.innerHeight); }
          else { SolaraBridge.log('no iframe'); }
        },
        onStateChange: function (e) { SolaraBridge.log('state ' + e.data); SolaraBridge.onPlayerState(e.data); if (!window.solaraTimer) { window.solaraTimer = setInterval(function () { try { SolaraBridge.onPlayerTime(player.getCurrentTime(), player.getDuration()); } catch (err) {} }, 250); } },
        onError: function (e) { SolaraBridge.log('error ' + e.data); SolaraBridge.onPlayerError(e.data); }
      }
    });
  }
</script>
</body>
</html>
""".trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun YouTubePlayerView(videoId: String, modifier: Modifier, onError: (Int) -> Unit) {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    val chrome = remember(context) { FullscreenChrome(context.findActivity()) }

    val webView = remember {
        // Referer = идентификатор приложения в виде https://com.example.app (требование YouTube)
        val origin = "https://" + context.packageName.lowercase()
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            WebView.setWebContentsDebuggingEnabled((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false // автозапуск только после тапа по видео в списке
            webChromeClient = chrome
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d("SolaraYT", "page finished: $url")
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
                ) {
                    Log.d("SolaraYT", "load error: ${request?.url} -> ${error?.errorCode} ${error?.description}")
                }

                override fun onReceivedHttpError(
                    view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?
                ) {
                    Log.d("SolaraYT", "http error: ${request?.url} -> ${errorResponse?.statusCode}")
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val req = request ?: return false
                    if (!req.isForMainFrame) return false
                    // Ссылки из плеера не блокируем, а открываем во внешнем приложении
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, req.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: ActivityNotFoundException) {
                        // нет приложения для ссылки
                    }
                    return true
                }
            }
            addJavascriptInterface(
                YouTubePlayerBridge({ code -> currentOnError(code) }, { st -> YouTubeControl.onState?.invoke(st) }, { pos, dur -> YouTubeControl.onTime?.invoke(pos, dur) }),
                "SolaraBridge",
            )
            loadDataWithBaseURL(origin, playerHtml(videoId, origin), "text/html", "utf-8", null)
        }
    }

    // Другое видео: тот же плеер, новый ролик (старое остаётся на экране, пока новое не загрузится)
    val loadedId = remember { arrayOf(videoId) }
    LaunchedEffect(videoId) {
        if (videoId != loadedId[0]) {
            loadedId[0] = videoId
            val safe = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            webView.evaluateJavascript("solaraLoad('$safe')", null)
        }
    }

    // Фоновое воспроизведение YouTube запрещено: при уходе из приложения ставим на паузу
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        webView.evaluateJavascript("try { player.pauseVideo(); } catch (e) {}", null)
        webView.onPause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }

    DisposableEffect(webView) {
        YouTubeControl.send = { cmd ->
            val js = when (cmd) {
                "play" -> "try { player.playVideo(); } catch (e) {}"
                "pause" -> "try { player.pauseVideo(); } catch (e) {}"
                else -> cmd.removePrefix("seek:").toDoubleOrNull()
                    ?.takeIf { cmd.startsWith("seek:") }
                    ?.let { "try { player.seekTo($it, true); } catch (e) {}" }
            }
            if (js != null) webView.evaluateJavascript(js, null)
        }
        onDispose {
            YouTubeControl.send = null
            YouTubeControl.onState?.invoke(-1)
            chrome.exitFullscreen()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // factory вызывается один раз за жизнь AndroidView, поэтому при смене WebView его нужно пересоздать
    key(webView) {
        AndroidView(factory = { webView }, modifier = modifier)
    }
}

@androidx.compose.runtime.Composable
actual fun rememberTextStore(): TextStore {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return androidx.compose.runtime.remember {
        val prefs = context.getSharedPreferences("solara_text_store", android.content.Context.MODE_PRIVATE)
        object : TextStore {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun set(key: String, value: String) {
                prefs.edit().putString(key, value).apply()
            }
            override fun nowMillis(): Long = System.currentTimeMillis()
            override fun quotaDay(millis: Long): Long {
                val tz = java.util.TimeZone.getTimeZone("America/Los_Angeles")
                return Math.floorDiv(millis + tz.getOffset(millis), 86_400_000L)
            }
        }
    }
}
