package com.unknowstorm.musicapp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SolaraTheme(
    darkTheme: Boolean = PaletteSettings.mode.isDark(isSystemInDarkTheme()),
    palette: PaletteChoice = PaletteSettings.choice,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (palette) {
        // Цвета системы; там, где их нет (Desktop, Android до 12), остаётся стандартная схема
        PaletteChoice.Dynamic ->
            dynamicColorSchemeOrNull(darkTheme) ?: if (darkTheme) darkColorScheme() else lightColorScheme()
        is PaletteChoice.Seed ->
            remember(palette, darkTheme) { schemeFromSeed(Color(palette.argb), darkTheme) }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}