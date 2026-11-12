package com.unknowstorm.musicapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.model.Service
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ключ в текстовом хранилище, под которым лежит порядок сервисов в островке. */
const val ISLAND_KEY = "island_v1"

/**
 * Какие сервисы показаны в главном островке (верхней панели) и в каком порядке.
 * Глобальное состояние, как PaletteSettings: панель читает его при каждой перерисовке.
 * Сервисы, которых нет в сохранённой строке (например, добавленные в новой версии), показываются в конце.
 */
object IslandSettings {
    private var savedVisible: List<String>? by mutableStateOf(null)
    private var savedHidden: Set<String> by mutableStateOf(emptySet())

    /** Сервисы, которые можно переставлять и скрывать. Библиотека, Spotify и «+» остаются на своих местах. */
    val editableServices: List<Service>
        get() = Service.entries.filter { it.ready && it != Service.SPOTIFY && it != Service.ALL }

    /** Видимые сервисы в сохранённом порядке. */
    fun visible(): List<Service> {
        val all = editableServices
        val order = savedVisible ?: return all.filter { it.name !in savedHidden }
        val known = order.mapNotNull { name -> all.firstOrNull { it.name == name } }
        val rest = all.filter { it.name !in order && it.name !in savedHidden }
        return known + rest
    }

    /** Вызывается один раз при старте с сохранённой строкой (или null). */
    fun restore(raw: String?) {
        if (raw.isNullOrBlank()) {
            savedVisible = null
            savedHidden = emptySet()
            return
        }
        var visible: List<String>? = null
        var hidden: Set<String> = emptySet()
        raw.split(';').forEach { part ->
            val pieces = part.split('=', limit = 2)
            val names = pieces.getOrElse(1) { "" }.split(',').filter { it.isNotBlank() }
            when (pieces[0]) {
                "v" -> visible = names
                "h" -> hidden = names.toSet()
            }
        }
        savedVisible = visible
        savedHidden = hidden
    }

    /** Применяет новый порядок сразу и сохраняет его через [persist]. */
    fun update(order: List<Service>, persist: (String) -> Unit) {
        val names = order.map { it.name }
        val hidden = editableServices.map { it.name }.filter { it !in names }.toSet()
        savedVisible = names
        savedHidden = hidden
        persist("v=" + names.joinToString(",") + ";h=" + hidden.joinToString(","))
    }
}

/**
 * Шторка редактора островка. В центре сам островок: сервисы можно зажать и перетащить.
 * Нажатие на сервис выделяет его и показывает «Скрыть»; «+ Добавить» возвращает скрытые.
 * Изменения применяются только по «Сохранить».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSheet(
    onSave: (List<Service>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editable = remember { IslandSettings.editableServices }
    var visible by remember { mutableStateOf(IslandSettings.visible()) }
    var selected by remember { mutableStateOf<Service?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val hidden = editable.filter { it !in visible }

    // Всё вернули в островок: панель добавления больше не нужна
    LaunchedEffect(hidden.isEmpty()) { if (hidden.isEmpty()) showAdd = false }

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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                tr("Island", "Острівець"),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            IslandPreview(
                visible = visible,
                hidden = hidden,
                selected = selected,
                onSelect = { selected = if (selected == it) null else it },
                onReorder = { from, to ->
                    visible = visible.toMutableList().apply { add(to, removeAt(from)) }
                },
            )

            Text(
                tr("Hold a service and drag to reorder", "Утримуйте сервіс і перетягуйте, щоб змінити порядок"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // «Скрыть» появляется, когда выбран сервис; «+ Добавить», когда есть скрытые
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = selected != null && visible.size > 1,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + scaleIn(spring(0.6f, Spring.StiffnessMedium)),
                    exit = fadeOut() + scaleOut(),
                ) {
                    OutlinedButton(onClick = {
                        selected?.let { visible = visible - it }
                        selected = null
                    }) { Text(tr("Hide", "Сховати")) }
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = hidden.isNotEmpty(),
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + scaleIn(spring(0.6f, Spring.StiffnessMedium)),
                    exit = fadeOut() + scaleOut(),
                ) {
                    FilledTonalButton(onClick = { showAdd = !showAdd }) {
                        Text("+ " + tr("Add", "Додати"))
                    }
                }
            }

            // Скрытые сервисы: нажатие возвращает сервис в конец островка
            AnimatedVisibility(
                visible = showAdd && hidden.isNotEmpty(),
                enter = fadeIn() + expandVertically(spring(0.8f, Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hidden.forEach { service ->
                        AssistChip(
                            onClick = {
                                visible = visible + service
                                selected = service
                            },
                            label = { Text(service.title) },
                            leadingIcon = {
                                ServiceLogo(service, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
                            },
                        )
                    }
                }
            }

            Button(
                onClick = { onSave(visible); close() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(tr("Save", "Зберегти")) }
        }
    }
}

/**
 * Островок в редакторе. Положение каждой иконки считается из её номера, поэтому остальные плавно
 * съезжают на освободившиеся места, пока одну иконку тащат. Скрытые сервисы не убираются из дерева,
 * а сжимаются и гаснут на краю: так «скрыть» и «добавить» анимируются.
 */
@Composable
private fun IslandPreview(
    visible: List<Service>,
    hidden: List<Service>,
    selected: Service?,
    onSelect: (Service) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val itemSize = 48.dp
    val gap = 14.dp
    val stepPx = with(LocalDensity.current) { (itemSize + gap).toPx() }
    val count = visible.size
    val contentWidth = itemSize * count + gap * (count - 1).coerceAtLeast(0)

    var dragging by remember { mutableStateOf<Service?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val onReorderNow by rememberUpdatedState(onReorder)

    // Те же цвета, что у настоящей панели: чисто белые или чёрные логотипы по правилам брендов
    val darkTheme = cs.background.luminance() < 0.179f
    val idle = if (darkTheme) Color.White else Color.Black
    val onPrimary = if (cs.primary.luminance() > 0.179f) Color.Black else Color.White

    // Отпустили: иконка плавно садится на своё место, и только потом «перетаскивание» заканчивается
    fun release() {
        scope.launch {
            animate(dragOffset, 0f, animationSpec = spring(0.7f, Spring.StiffnessMediumLow)) { value, _ ->
                dragOffset = value
            }
            dragging = null
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .animateContentSize(spring(0.8f, Spring.StiffnessMediumLow))
                .clip(RoundedCornerShape(32.dp))
                .background(cs.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(Modifier.width(contentWidth).height(itemSize)) {
                (visible + hidden).forEach { service ->
                    key(service) {
                        val index = visible.indexOf(service)
                        val isVisible = index >= 0
                        val indexNow by rememberUpdatedState(index)
                        val countNow by rememberUpdatedState(count)
                        // Скрытые стоят на последнем месте и гаснут
                        val slot = if (isVisible) index else (count - 1).coerceAtLeast(0)
                        val isDragging = dragging == service
                        val isSelected = isVisible && selected == service

                        val animX by animateFloatAsState(
                            slot * stepPx,
                            spring(0.75f, Spring.StiffnessMedium),
                            label = "islandX",
                        )
                        val x = if (isDragging) {
                            (slot * stepPx + dragOffset).coerceIn(0f, (count - 1).coerceAtLeast(0) * stepPx)
                        } else animX
                        val scaleValue by animateFloatAsState(
                            when {
                                isDragging -> 1.18f
                                !isVisible -> 0.3f
                                else -> 1f
                            },
                            spring(0.55f, Spring.StiffnessMedium),
                            label = "islandScale",
                        )
                        val alphaValue by animateFloatAsState(
                            if (isVisible) 1f else 0f,
                            spring(stiffness = Spring.StiffnessMedium),
                            label = "islandAlpha",
                        )
                        val background by animateColorAsState(
                            if (isSelected) cs.primary else Color.Transparent,
                            label = "islandBg",
                        )
                        val tint by animateColorAsState(if (isSelected) onPrimary else idle, label = "islandTint")

                        Box(
                            Modifier
                                .offset { IntOffset(x.roundToInt(), 0) }
                                .zIndex(if (isDragging) 1f else 0f)
                                .size(itemSize)
                                .graphicsLayer {
                                    scaleX = scaleValue
                                    scaleY = scaleValue
                                    alpha = alphaValue
                                    shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                                    shape = CircleShape
                                    clip = false
                                }
                                .background(background, CircleShape)
                                .clip(CircleShape)
                                .pointerInput(service) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            if (dragging == null && indexNow >= 0) {
                                                dragging = service
                                                dragOffset = 0f
                                                onSelect(service)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (dragging == service) {
                                                dragOffset += amount.x
                                                val current = indexNow
                                                val target = ((current * stepPx + dragOffset) / stepPx)
                                                    .roundToInt()
                                                    .coerceIn(0, (countNow - 1).coerceAtLeast(0))
                                                if (target != current) {
                                                    onReorderNow(current, target)
                                                    // Иконка продолжает идти за пальцем, а не прыгает на новое место
                                                    dragOffset -= (target - current) * stepPx
                                                }
                                            }
                                        },
                                        onDragEnd = { if (dragging == service) release() },
                                        onDragCancel = { if (dragging == service) release() },
                                    )
                                }
                                .clickable(enabled = isVisible) { onSelect(service) },
                            contentAlignment = Alignment.Center,
                        ) {
                            ServiceLogo(service, Modifier.size(24.dp), tint)
                        }
                    }
                }
            }
        }
    }
}
