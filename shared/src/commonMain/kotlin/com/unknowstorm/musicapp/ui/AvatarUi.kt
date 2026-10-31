package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.unknowstorm.musicapp.auth.AuthRepository

/**
 * Аватарка: своё фото, а если его нет — первая буква ника на фоне из цветовой палитры приложения.
 */
@Composable
fun UserAvatar(
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
) {
    val shaped = modifier.size(size).clip(CircleShape)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = shaped,
        )
    } else {
        val letter = name?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        Box(
            shaped.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

/** Фото текущего пользователя из облака по метке версии из профиля (null — фото нет). */
@Composable
fun rememberAvatarImage(repo: AuthRepository, version: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, version) {
        value = if (version == null) null else repo.loadAvatar(version)?.let { decodeImageBitmap(it) }
    }
    return image
}
