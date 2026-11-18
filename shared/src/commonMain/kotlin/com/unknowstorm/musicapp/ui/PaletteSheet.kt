package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.theme.PALETTE_CUSTOM_KEY
import com.unknowstorm.musicapp.theme.PALETTE_SEEDS
import com.unknowstorm.musicapp.theme.PaletteChoice
import com.unknowstorm.musicapp.theme.ThemeMode
import com.unknowstorm.musicapp.theme.decodeCustomSeeds
import com.unknowstorm.musicapp.theme.dynamicColorSchemeOrNull
import com.unknowstorm.musicapp.theme.encodeCustomSeeds
import com.unknowstorm.musicapp.theme.recommendedSeeds
import com.unknowstorm.musicapp.theme.schemeFromSeed
import com.unknowstorm.musicapp.theme.seedColor
import com.unknowstorm.musicapp.theme.seedHue
import com.unknowstorm.musicapp.theme.seedLightness
import com.unknowstorm.musicapp.theme.seedSaturation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val store = rememberTextStore()
    val recommendedArgb = remember(recommended) { recommended.map { it.toArgb() } }

    // Сохранённые свои палитры лежат в хранилище; несохранённые живут, пока открыта шторка
    var saved by remember { mutableStateOf(decodeCustomSeeds(store.get(PALETTE_CUSTOM_KEY))) }
    val customs = remember {
        mutableStateListOf<Int>().apply {
            addAll(saved)
            val argb = (current as? PaletteChoice.Seed)?.argb
            if (argb != null && argb !in recommendedArgb && argb !in this) add(argb)
        }
    }
    var showPicker by remember { mutableStateOf(false) }
    // Цвет, который сейчас редактируется; null, если создаётся новая палитра
    var editArgb by remember { mutableStateOf<Int?>(null) }

    fun persistSaved(list: List<Int>) {
        saved = list
        store.set(PALETTE_CUSTOM_KEY, encodeCustomSeeds(list))
    }

    fun commitCustom(argb: Int, replacing: Int?) {
        if (replacing != null && replacing != argb) {
            val i = customs.indexOf(replacing)
            if (i >= 0) {
                if (argb in customs || argb in recommendedArgb) customs.removeAt(i) else customs[i] = argb
            }
            if (replacing in saved) {
                val rest = saved.filter { it != replacing }
                persistSaved(if (argb in customs && argb !in rest) rest + argb else rest)
            }
        } else if (argb !in customs && argb !in recommendedArgb) {
            customs.add(argb)
        }
        choice = PaletteChoice.Seed(argb)
    }

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
                customs.toList().forEach { argb ->
                    CustomSwatch(
                        scheme = schemeFromSeed(Color(argb), dark),
                        selected = (choice as? PaletteChoice.Seed)?.argb == argb,
                        saved = argb in saved,
                        onSelect = { choice = PaletteChoice.Seed(argb) },
                        onSave = { persistSaved(saved + argb) },
                        onEdit = { editArgb = argb; showPicker = true },
                        onDelete = {
                            customs.remove(argb)
                            if (argb in saved) persistSaved(saved.filter { it != argb })
                            if ((choice as? PaletteChoice.Seed)?.argb == argb) choice = PaletteChoice.Dynamic
                        },
                    )
                }
                AddSwatch { editArgb = null; showPicker = true }
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
                    customs.removeAll { it !in saved }
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
        val editing = editArgb
        SeedPickerDialog(
            initial = editing?.let { Color(it) } ?: (choice as? PaletteChoice.Seed)?.let { Color(it.argb) } ?: base,
            dark = dark,
            editing = editing != null,
            onConfirm = { color ->
                commitCustom(color.toArgb(), editing)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Диалог своей палитры: оттенок и насыщенность, превью и «капля» над ползунком обновляются сразу. */
@Composable
private fun SeedPickerDialog(
    initial: Color,
    dark: Boolean,
    editing: Boolean,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    var hue by remember { mutableFloatStateOf(seedHue(initial)) }
    var saturation by remember { mutableFloatStateOf(seedSaturation(initial).coerceIn(0.1f, 1f)) }
    // Яркость не меняется ползунками, но у редактируемого тёмного цвета сохраняется
    val lightness = remember { seedLightness(initial).coerceIn(0.25f, 0.6f) }
    val color = seedColor(hue, saturation, lightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) tr("Edit palette", "Редагувати палітру") else tr("Custom palette", "Власна палітра")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PalettePie(
                    schemeFromSeed(color, dark),
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    Modifier.size(96.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Hue", "Відтінок"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start),
                )
                ColorSlider(hue, { hue = it }, 0f..360f, color)
                Text(
                    tr("Saturation", "Насиченість"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start),
                )
                ColorSlider(saturation, { saturation = it }, 0.1f..1f, color)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text(tr("Done", "Готово")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Скасувати")) } },
    )
}

/**
 * Обычный ползунок Material 3. Пока его тянут, над бегунком всплывает капля цвета [bubbleColor],
 * чтобы было видно, какой цвет выбирается сейчас.
 */
@Composable
private fun ColorSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    bubbleColor: Color,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val bubbleScale by animateFloatAsState(if (pressed || dragged) 1f else 0f, label = "bubble")
    var thumbWidthPx by remember { mutableFloatStateOf(0f) }
    val bubbleSize = 36.dp
    val bubblePx = with(LocalDensity.current) { bubbleSize.toPx() }
    val bubbleTopPx = with(LocalDensity.current) { -30.dp.roundToPx() }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        // Центр бегунка: Slider двигает его в пределах ширины минус ширина самого бегунка
        val thumbCenter = thumbWidthPx / 2f + fraction * (constraints.maxWidth - thumbWidthPx)

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            interactionSource = interaction,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interaction,
                    modifier = Modifier.onSizeChanged { thumbWidthPx = it.width.toFloat() },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Капля над бегунком: круг с острым углом вниз (квадрат с тремя скруглёнными углами, повёрнутый на 45°)
        if (bubbleScale > 0.01f) {
            val drop = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 0, bottomStartPercent = 50)
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset((thumbCenter - bubblePx / 2f).roundToInt(), bubbleTopPx) }
                    .size(bubbleSize)
                    .graphicsLayer {
                        rotationZ = 45f
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                        alpha = bubbleScale
                    }
                    .shadow(4.dp, drop)
                    .clip(drop)
                    .background(bubbleColor)
                    .border(2.dp, Color.White, drop),
            )
        }
    }
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
private fun PaletteSwatch(
    scheme: ColorScheme,
    selected: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentClick() },
                    onLongPress = { currentLongClick?.invoke() },
                )
            }
            .padding(if (selected) 7.dp else 3.dp),
    ) {
        PalettePie(scheme, MaterialTheme.colorScheme.surfaceContainerLow, Modifier.fillMaxSize())
    }
}

/** Своя палитра: по долгому нажатию открывается меню «Сохранить / Редактировать / Удалить». */
@Composable
private fun CustomSwatch(
    scheme: ColorScheme,
    selected: Boolean,
    saved: Boolean,
    onSelect: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        PaletteSwatch(
            scheme,
            selected,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            },
            onClick = onSelect,
        )
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            if (!saved) {
                DropdownMenuItem(
                    text = { Text(tr("Save", "Зберегти")) },
                    onClick = { menu = false; onSave() },
                )
            }
            DropdownMenuItem(
                text = { Text(tr("Edit", "Редагувати")) },
                onClick = { menu = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(tr("Delete", "Видалити")) },
                onClick = { menu = false; onDelete() },
            )
        }
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