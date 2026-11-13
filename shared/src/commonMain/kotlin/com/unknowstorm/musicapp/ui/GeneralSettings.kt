package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.theme.PALETTE_KEY
import com.unknowstorm.musicapp.theme.PaletteSettings

/**
 * Содержимое раздела General, как на эскизе: палитра, язык, документация.
 * Строки «Сменить пароль» и «Выйти» приходят снаружи в [extraEntries] и стоят после основных.
 */
@Composable
internal fun GeneralSettings(extraEntries: List<@Composable (Shape) -> Unit> = emptyList()) {
    val store = rememberTextStore()
    var showPalette by remember { mutableStateOf(false) }
    var showIsland by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showDocs by remember { mutableStateOf(false) }

    val entries = buildList<@Composable (Shape) -> Unit> {
        add { shape ->
            SettingsRow(shape, tr("Palette", "Палітра"), tile = { PaletteIcon() }, onClick = { showPalette = true })
        }
        add { shape ->
            SettingsRow(shape, tr("Island", "Острівець"), tile = { IslandTileIcon() }, onClick = { showIsland = true })
        }
        add { shape ->
            SettingsRow(
                shape,
                tr("Language", "Мова"),
                tile = { Text("Aи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                onClick = { showLanguage = true },
            )
        }
        add { shape ->
            SettingsRow(shape, tr("Documentation", "Документація"), onClick = { showDocs = true })
        }
        addAll(extraEntries)
    }
    SettingsGroup(entries)

    if (showPalette) {
        PaletteSheet(
            current = PaletteSettings.choice,
            onApply = { choice ->
                // Тема меняется сразу, выбор запоминается на устройстве
                PaletteSettings.update(choice) { saved -> store.set(PALETTE_KEY, saved) }
            },
            onDismiss = { showPalette = false },
        )
    }
    if (showIsland) {
        IslandSheet(
            onSave = { order ->
                IslandSettings.update(order) { saved -> store.set(ISLAND_KEY, saved) }
            },
            onDismiss = { showIsland = false },
        )
    }
    if (showLanguage) {
        SettingsSheet(tr("Language", "Мова"), onDismiss = { showLanguage = false }) {
            LanguageSetting()
        }
    }
    if (showDocs) {
        DocumentationSheet(onDismiss = { showDocs = false })
    }
}

/** Документация: условия использования и политика конфиденциальности (тексты из [LegalTexts]). */
@Composable
private fun DocumentationSheet(onDismiss: () -> Unit) {
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    SettingsSheet(tr("Documentation", "Документація"), onDismiss = onDismiss) {
        SettingsGroup(
            listOf(
                { shape -> SettingsRow(shape, S.termsOfUse, onClick = { showTerms = true }) },
                { shape -> SettingsRow(shape, S.privacyPolicy, onClick = { showPrivacy = true }) },
            )
        )
    }
    if (showTerms) LegalDialog(S.termsOfUse, LegalTexts.TERMS) { showTerms = false }
    if (showPrivacy) LegalDialog(S.privacyPolicy, LegalTexts.PRIVACY) { showPrivacy = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            content()
        }
    }
}

/** Иконка островка для плитки: библиотека и плюс, как на панели. */
@Composable
private fun IslandTileIcon() {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        LibraryIcon(Modifier.size(20.dp), tint)
        AddIcon(Modifier.size(14.dp), tint)
    }
}

/** Иконка палитры для плитки: круг с тремя точками. */
@Composable
private fun PaletteIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        drawCircle(color, radius = w * 0.45f, style = Stroke(w * 0.08f))
        listOf(0.34f to 0.40f, 0.62f to 0.34f, 0.68f to 0.60f).forEach { (x, y) ->
            drawCircle(color, radius = w * 0.07f, center = Offset(x * w, y * w))
        }
    }
}
