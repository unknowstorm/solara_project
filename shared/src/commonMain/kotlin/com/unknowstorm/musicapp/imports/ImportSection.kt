package com.unknowstorm.musicapp.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import kotlinx.coroutines.launch

/** "Import favorites and playlists" block: drop it into the Settings screen. */
@Composable
fun ImportSection(repo: ImportRepository, modifier: Modifier = Modifier) {
    val signedIn by repo.signedIn.collectAsState()
    val linked by repo.linked.collectAsState()
    val busy by repo.busy.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(S.importFavoritesTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            S.importPublicOnly,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!signedIn) {
            Text(S.signInToImport, style = MaterialTheme.typography.bodyMedium)
        } else {
            IMPORT_PROVIDERS.forEach { p ->
                ProviderRow(repo, p, linked[p], busy == p, busy != null) { message = it }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ProviderRow(
    repo: ImportRepository,
    provider: ProviderId,
    account: LinkedAccount?,
    working: Boolean,
    anyBusy: Boolean,
    onMessage: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var deleteToo by remember { mutableStateOf(false) }
    val title = provider.name.lowercase().replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title + (account?.let { " · ${it.displayName ?: it.externalId}" } ?: ""))
        if (account == null) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(S.importAccountHint) },
                singleLine = true,
                enabled = !anyBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !anyBusy && input.isNotBlank(),
                onClick = {
                    onMessage(null)
                    scope.launch {
                        repo.import(provider, input).fold(
                            onSuccess = { onMessage("$title: " + S.importedSummary(it.likesAdded, it.playlistsAdded)) },
                            onFailure = { onMessage("$title: ${it.message}") },
                        )
                    }
                },
            ) { Text(if (working) S.importing else S.importAction) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !anyBusy,
                    onClick = {
                        onMessage(null)
                        scope.launch {
                            repo.import(provider, null).fold(
                                onSuccess = { onMessage("$title: " + S.importedSummary(it.likesAdded, it.playlistsAdded)) },
                                onFailure = { onMessage("$title: ${it.message}") },
                            )
                        }
                    },
                ) { Text(if (working) S.importing else S.importAgain) }
                TextButton(
                    enabled = !anyBusy,
                    onClick = {
                        scope.launch {
                            repo.disconnect(provider, deleteToo).onFailure { onMessage("$title: ${it.message}") }
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(S.disconnectAccount) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteToo, onCheckedChange = { deleteToo = it })
                Text(S.alsoDeleteImported, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
