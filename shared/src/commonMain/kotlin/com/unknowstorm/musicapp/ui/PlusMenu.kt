package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.model.Service

/** Скрытые сервисы островка в порядке editableServices. */
fun IslandSettings.hiddenServices(): List<Service> {
    val shown = visible()
    return editableServices.filter { it !in shown }
}

/** Возвращает скрытый сервис в конец островка навсегда и сохраняет через [persist]. */
fun IslandSettings.reveal(service: Service, persist: (String) -> Unit) {
    if (service in visible()) return
    update(visible() + service, persist)
}

/**
 * Меню «+» в стиле Material 3 You: скруглённое, surfaceContainer, заголовок,
 * у каждого пункта круглая плашка с логотипом, название и короткая заметка.
 *
 * Ставьте внутрь того же Box, что и кнопка «+», вместо старого DropdownMenu.
 */
@Composable
fun PlusMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSpotify: () -> Unit,
    onReveal: (Service) -> Unit,
    // Services with ready = false (empty now, kept for the future)
    soon: List<Service> = emptyList(),
    onSoon: (Service) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = cs.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Text(
            tr("Add source", "Додати джерело"),
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Spotify: без логотипа (brand rules), плашка той же геометрии для выравнивания
        PlusMenuItem(
            title = Service.SPOTIFY.title,
            note = Service.SPOTIFY.note,
            onClick = { onDismiss(); onSpotify() },
            plate = {
                Text(
                    Service.SPOTIFY.title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                )
            },
        )

        IslandSettings.hiddenServices().forEach { service ->
            PlusMenuItem(
                title = service.title,
                note = service.note,
                onClick = { onDismiss(); onReveal(service) },
                plate = { ServiceLogo(service, Modifier.size(20.dp), cs.onSurface) },
            )
        }

        soon.forEach { service ->
            PlusMenuItem(
                title = service.title,
                note = service.note,
                onClick = { onDismiss(); onSoon(service) },
                plate = { ServiceLogo(service, Modifier.size(20.dp), cs.onSurfaceVariant) },
            )
        }
    }
}

@Composable
private fun PlusMenuItem(
    title: String,
    note: String?,
    onClick: () -> Unit,
    plate: @Composable () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) { plate() }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!note.isNullOrBlank()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}