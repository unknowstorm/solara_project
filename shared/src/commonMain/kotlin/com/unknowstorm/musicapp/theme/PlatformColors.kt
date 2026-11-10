package com.unknowstorm.musicapp.theme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
/** Colors from the system (Material You), or null if the platform does not provide them. */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?
