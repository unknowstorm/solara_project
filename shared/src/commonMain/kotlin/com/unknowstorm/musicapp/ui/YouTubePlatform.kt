package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Простое хранилище флагов (например, «принял условия YouTube»). */
interface FlagStore {
    fun get(key: String): Boolean
    fun set(key: String, value: Boolean)
}

@Composable
expect fun rememberFlagStore(): FlagStore

/** Открывает ссылку во внешнем приложении или браузере. */
@Composable
expect fun rememberExternalLinkOpener(): (String) -> Unit

/**
 * Встроенный плеер YouTube. Видео играет только пока этот компонент на экране
 * (правила YouTube запрещают фоновое воспроизведение).
 * onError получает код ошибки IFrame API: 100, 101, 150, 153 и т. д.
 */
@Composable
expect fun YouTubePlayerView(videoId: String, modifier: Modifier, onError: (Int) -> Unit)
