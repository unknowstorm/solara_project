package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.AppLanguage
import com.unknowstorm.musicapp.i18n.I18n
import com.unknowstorm.musicapp.i18n.LANGUAGE_KEY
import com.unknowstorm.musicapp.i18n.S

/** Interface language picker. The choice is saved and applied immediately, without a restart. */
@Composable
fun LanguageSetting() {
    val texts = rememberTextStore()
    val current = I18n.language
    Text(S.language, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            FilterChip(
                selected = current == lang,
                onClick = {
                    I18n.language = lang
                    texts.set(LANGUAGE_KEY, lang.code)
                },
                label = { Text(lang.nativeName) },
            )
        }
    }
}
