package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.logo_s
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** How many times to retry loading the image after an error (e.g. the internet disappeared). */
private const val MAX_RETRIES = 10

/** Pause before a retry: 1.5 s, 3 s, 4.5 s, then 6 s each (about a minute in total). */
private fun retryDelayMs(attempt: Int): Long = (1_500L * (attempt + 1)).coerceAtMost(6_000L)

/**
 * An image with automatic retry after an error.
 * [placeholder] is shown only during the first load (usually null: a neutral background).
 * [error] is shown when there is no address or the load failed; during retries it stands
 * instead of a stub so the image does not blink between attempts.
 * [fallbackContent] (optional) is drawn behind the image in the same situations as [error]:
 * no address, a failed load, or a pending retry. It is hidden once the image has loaded.
 */
@Composable
fun RetryAsyncImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallbackContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    var loaded by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, failed, attempt) {
        if (failed && !url.isNullOrBlank() && attempt < MAX_RETRIES) {
            delay(retryDelayMs(attempt))
            failed = false
            attempt++
        }
    }

    val context = LocalPlatformContext.current
    val request = remember(url, attempt) {
        ImageRequest.Builder(context).data(url).crossfade(true).build()
    }

    val image: @Composable (Modifier) -> Unit = { m ->
        // key(attempt) recreates the loader, i.e. the request goes out again
        key(attempt) {
            AsyncImage(
                model = request,
                contentDescription = null,
                placeholder = if (attempt > 0) error else placeholder,
                error = error,
                fallback = error,
                contentScale = contentScale,
                onSuccess = { failed = false; loaded = true },
                onError = { failed = true },
                modifier = m,
            )
        }
    }

    if (fallbackContent == null) {
        image(modifier)
    } else {
        Box(modifier) {
            if (url.isNullOrBlank() || ((failed || attempt > 0) && !loaded)) fallbackContent()
            image(Modifier.fillMaxSize())
        }
    }
}

/** A track cover. Our "S" is shown only if there is no cover or it failed to load. */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        RetryAsyncImage(
            url = url,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallbackContent = { CoverPlaceholder() },
        )
    }
}

/** The logo letter, half the tile size, tinted from the theme: it works on light and dark themes. */
@Composable
private fun BoxScope.CoverPlaceholder() {
    Image(
        painter = painterResource(Res.drawable.logo_s),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.align(Alignment.Center).fillMaxSize(0.5f),
    )
}
