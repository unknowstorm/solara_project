package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.theme.PALETTE_SEEDS
import com.unknowstorm.musicapp.theme.PaletteChoice
import com.unknowstorm.musicapp.theme.ThemeMode
import com.unknowstorm.musicapp.theme.dynamicColorSchemeOrNull
import com.unknowstorm.musicapp.theme.recommendedSeeds
import com.unknowstorm.musicapp.theme.schemeFromSeed
import com.unknowstorm.musicapp.theme.seedColor
import com.unknowstorm.musicapp.theme.seedHue
import com.unknowstorm.musicapp.theme.seedSaturation
import kotlinx.coroutines.launch

/**
 * Шторка палитры, как системный выбор Android: сначала палитра по умолчанию (цвета системы),
 * затем рекомендуемые палитры рядом по цвету, свой цвет и кнопка «+». Ниже переключатель тёмной темы.
 * Выбор применяется только по кнопке «Применить», пока её не нажали, тема не меняется.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteSheet(
    current: PaletteChoice,
    currentMode: ThemeMode,
    onApply: (PaletteChoice, ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var choice by remember { mutableStateOf(current) }
    var mode by remember { mutableStateOf(currentMode) }
    val dark = mode.isDark(isSystemInDarkTheme())

    // Палитра по умолчанию: цвета системы, а где их нет (Desktop, Android до 12), стандартная схема
    val defaultScheme = dynamicColorSchemeOrNull(dark) ?: if (dark) darkColorScheme() else lightColorScheme()
    // Основа для рекомендаций берётся из светлой схемы, чтобы набор не менялся при переключении темы
    val base = dynamicColorSchemeOrNull(false)?.primary ?: PALETTE_SEEDS.first()
    val recommended = remember(base) { recommendedSeeds(base) }

    // Ранее выбранный свой цвет, которого нет среди рекомендуемых, показываем отдельным образцом
    var customArgb by remember {
        mutableStateOf((current as? PaletteChoice.Seed)?.argb?.takeIf { a -> recommended.none { it.toArgb() == a } })
    }
    var showPicker by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("Palette", "Палітра"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                tr(
                    "The colors of the app will match the selected palette.",
                    "Кольори застосунку поєднуватимуться з обраною палітрою.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaletteSwatch(defaultScheme, selected = choice is PaletteChoice.Dynamic) {
                    choice = PaletteChoice.Dynamic
                }
                recommended.forEach { color ->
                    val argb = color.toArgb()
                    PaletteSwatch(schemeFromSeed(color, dark), selected = (choice as? PaletteChoice.Seed)?.argb == argb) {
                        choice = PaletteChoice.Seed(argb)
                    }
                }
                customArgb?.let { argb ->
                    PaletteSwatch(schemeFromSeed(Color(argb), dark), selected = (choice as? PaletteChoice.Seed)?.argb == argb) {
                        choice = PaletteChoice.Seed(argb)
                    }
                }
                AddSwatch { showPicker = true }
            }
            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Dark theme", "Темна тема"), style = MaterialTheme.typography.titleMedium)
                        if (mode == ThemeMode.System) {
                            Text(
                                tr("Follows the system", "Як у системі"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = dark,
                        onCheckedChange = { mode = if (it) ThemeMode.Dark else ThemeMode.Light },
                    )
                }
            }

            TextButton(
                onClick = {
                    choice = PaletteChoice.Dynamic
                    mode = ThemeMode.System
                    customArgb = null
                },
                modifier = Modifier.padding(vertical = 4.dp),
            ) { Text(tr("Reset", "Скинути")) }

            Button(
                onClick = { onApply(choice, mode); close() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(tr("Apply", "Застосувати")) }
        }
    }

    if (showPicker) {
        SeedPickerDialog(
            initial = (choice as? PaletteChoice.Seed)?.let { Color(it.argb) } ?: base,
            dark = dark,
            onConfirm = { color ->
                val argb = color.toArgb()
                // Если цвет совпал с рекомендуемым, отдельный образец не нужен
                customArgb = if (recommended.any { it.toArgb() == argb }) customArgb else argb
                choice = PaletteChoice.Seed(argb)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Диалог своей палитры: оттенок и насыщенность, превью обновляется сразу. */
@Composable
private fun SeedPickerDialog(initial: Color, dark: Boolean, onConfirm: (Color) -> Unit, onDismiss: () -> Unit) {
    var hue by remember { mutableFloatStateOf(seedHue(initial)) }
    var saturation by remember { mutableFloatStateOf(seedSaturation(initial).coerceIn(0.3f, 1f)) }
    val color = seedColor(hue, saturation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Custom palette", "Власна палітра")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PalettePie(
                    schemeFromSeed(color, dark),
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    Modifier.size(96.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("Hue", "Відтінок"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start),
                )
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(
                    tr("Saturation", "Насиченість"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start),
                )
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0.3f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text(tr("Done", "Готово")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Скасувати")) } },
    )
}

/**
 * Круг из четырёх секторов в цветах схемы. Им же рисуется иконка строки «Палітра» в настройках.
 * [gapColor] — цвет тонких разделителей между секторами, обычно цвет фона под кругом.
 */
@Composable
internal fun PalettePie(scheme: ColorScheme, gapColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val d = size.minDimension
        val box = Size(d, d)
        listOf(
            180f to scheme.primary,
            270f to scheme.tertiary,
            0f to scheme.secondary,
            90f to scheme.primaryContainer,
        ).forEach { (start, color) ->
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset.Zero,
                size = box,
            )
        }
        val gap = 2.dp.toPx()
        drawLine(gapColor, Offset(d / 2f, 0f), Offset(d / 2f, d), strokeWidth = gap)
        drawLine(gapColor, Offset(0f, d / 2f), Offset(d, d / 2f), strokeWidth = gap)
    }
}

@Composable
private fun PaletteSwatch(scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(if (selected) 7.dp else 3.dp),
    ) {
        PalettePie(scheme, MaterialTheme.colorScheme.surfaceContainerLow, Modifier.fillMaxWidth().height(46.dp))
    }
}

/** Кнопка «+»: круг с контуром, открывает создание своей палитры. */
@Composable
private fun AddSwatch(onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val c = size.minDimension / 2f
            val w = 2.dp.toPx()
            drawLine(tint, Offset(0f, c), Offset(size.width, c), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(tint, Offset(c, 0f), Offset(c, size.height), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}