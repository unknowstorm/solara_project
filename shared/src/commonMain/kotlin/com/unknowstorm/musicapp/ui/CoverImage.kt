package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.logo
import org.jetbrains.compose.resources.painterResource

/** Обложка трека. Лого показываем, только если обложки нет или она не загрузилась; на время загрузки фон нейтральный. */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val fallback = painterResource(Res.drawable.logo)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = url?.takeIf { it.isNotBlank() },
            contentDescription = null,
            error = fallback,
            fallback = fallback,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
