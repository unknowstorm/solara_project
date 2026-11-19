package com.unknowstorm.musicapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import com.unknowstorm.musicapp.i18n.AppLanguage
import com.unknowstorm.musicapp.i18n.I18n
import com.unknowstorm.musicapp.i18n.LANGUAGE_KEY

/** Short label for the tile: UA, not UK (UK reads as United Kingdom). The internal code stays "uk". */
internal fun AppLanguage.tileLabel(): String = if (this == AppLanguage.UK) "UA" else "EN"

/**
 * Interface language picker in the same style as the other settings: a row per language,
 * each with its own name and a UA/EN tile. The choice is saved and applied immediately, without a restart.
 */
@Composable
fun LanguageSetting(onSelected: () -> Unit = {}) {
    val texts = rememberTextStore()
    val current = I18n.language
    val entries = buildList<@Composable (Shape) -> Unit> {
        AppLanguage.entries.forEach { lang ->
            add { shape ->
                SettingsRow(
                    shape = shape,
                    title = lang.nativeName,
                    tile = {
                        Text(lang.tileLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    },
                    onClick = {
                        I18n.language = lang
                        texts.set(LANGUAGE_KEY, lang.code)
                        onSelected()
                    },
                    selected = current == lang,
                )
            }
        }
    }
    SettingsGroup(entries)
}
