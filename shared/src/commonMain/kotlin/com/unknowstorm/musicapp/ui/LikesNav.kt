package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Переход на «Любимые» главной. Экраны, которые рисуют панель сервисов сами (YouTube),
 * берут обработчик отсюда, чтобы сердечко стояло на одном месте на всех чипах.
 */
val LocalOpenLikes = staticCompositionLocalOf<(() -> Unit)?> { null }
