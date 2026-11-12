package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.theme.PALETTE_SEEDS
import com.unknowstorm.musicapp.theme.PaletteChoice
import com.unknowstorm.musicapp.theme.dynamicColorSchemeOrNull
import kotlinx.coroutines.launch

/**
 * Шторка выбора палитры: источник цвета (система или свой), основной цвет, «Сбросить» и «Применить».
 * Выбор применяется только по кнопке «Применить», пока её не нажали, тема не меняется.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteSheet(
    current: PaletteChoice,
    onApply: (PaletteChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Цвета системы есть не везде (Android 12+), поэтому на Desktop этот вариант не показываем
    val dynamicSupported = dynamicColorSchemeOrNull(isSystemInDarkTheme()) != null

    var useSystem by remember { mutableStateOf(dynamicSupported && current is PaletteChoice.Dynamic) }
    var seed by remember {
        mutableStateOf((current as? PaletteChoice.Seed)?.argb ?: PALETTE_SEEDS.first().toArgb())
    }
    val draft: PaletteChoice = if (useSystem) PaletteChoice.Dynamic else PaletteChoice.Seed(seed)

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dynamicSupported) {
                SectionTitle(tr("Color source", "Джерело кольору"))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    SegmentedButton(
                        selected = useSystem,
                        onClick = { useSystem = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(tr("System", "Система")) }
                    SegmentedButton(
                        selected = !useSystem,
                        onClick = { useSystem = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(tr("Custom", "Свій колір")) }
                }
                HorizontalDivider(Modifier.padding(top = 16.dp))
            }

            SectionTitle(tr("Color", "Колір"))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PALETTE_SEEDS.chunked(4).forEach { rowSeeds ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowSeeds.forEach { color ->
                            Swatch(
                                color = color,
                                selected = !useSystem && color.toArgb() == seed,
                                enabled = !useSystem,
                                onClick = { seed = color.toArgb() },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))

            TextButton(
                onClick = {
                    useSystem = dynamicSupported
                    seed = PALETTE_SEEDS.first().toArgb()
                },
                modifier = Modifier.padding(vertical = 4.dp),
            ) { Text(tr("Reset", "Скинути")) }

            Button(
                onClick = { onApply(draft); close() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(tr("Apply", "Застосувати")) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}

@Composable
private fun Swatch(color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                "\u2713",
                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
