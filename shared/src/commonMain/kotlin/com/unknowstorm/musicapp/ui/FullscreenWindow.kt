package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable

/**
 * Полноэкранный режим окна, пока [active] = true.
 * Android: прячет системные панели и поворачивает экран в альбом; при выходе возвращает всё как было.
 * Desktop: ничего не делает (видео и так растягивается на всё окно приложения).
 */
@Composable
expect fun FullscreenWindowEffect(active: Boolean)
