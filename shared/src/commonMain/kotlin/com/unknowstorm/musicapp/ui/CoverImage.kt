package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.logo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** Сколько раз повторять загрузку картинки после ошибки (например, пропал интернет). */
private const val MAX_RETRIES = 10

/** Пауза перед повтором: 1.5 с, 3 с, 4.5 с, потом по 6 с (всего около минуты). */
private fun retryDelayMs(attempt: Int): Long = (1_500L * (attempt + 1)).coerceAtMost(6_000L)

/**
 * Картинка с автоматическим повтором после ошибки.
 * [placeholder] показывается только во время первой загрузки (обычно null: нейтральный фон).
 * [error] показывается, когда адреса нет или загрузка не удалась; при повторах он же стоит
 * вместо заглушки, чтобы картинка не мигала между попытками.
 */
@Composable
fun RetryAsyncImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
    error: Painter? = null,
) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }

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

    // key(attempt) пересоздаёт загрузчик, то есть запрос уходит заново
    key(attempt) {
        AsyncImage(
            model = request,
            contentDescription = null,
            placeholder = if (attempt > 0) error else placeholder,
            error = error,
            fallback = error,
            contentScale = contentScale,
            onSuccess = { failed = false },
            onError = { failed = true },
            modifier = modifier,
        )
    }
}

/** Обложка трека. Лого показывается, только если обложки нет или она не загрузилась. */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val logo = painterResource(Res.drawable.logo)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        RetryAsyncImage(
            url = url,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = null,
            error = logo,
        )
    }
}
