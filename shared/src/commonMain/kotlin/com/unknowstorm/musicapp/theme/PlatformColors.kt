package com.unknowstorm.musicapp.theme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
/** Цвета из системы (Material You), либо null, если платформа их не даёт. */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?
