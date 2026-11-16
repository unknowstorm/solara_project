package com.unknowstorm.musicapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.imports.IMPORT_PROVIDERS
import com.unknowstorm.musicapp.imports.ImportRepository
import com.unknowstorm.musicapp.imports.LinkedAccount
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.spotify.LibraryStatus
import com.unknowstorm.musicapp.spotify.LocalSpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** "Accounts and import": one rounded card per service, one column, in the style of the Library tiles. */
@Composable
fun AccountsSection(repo: ImportRepository?, onOpenSpotify: () -> Unit = {}, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            S.importPublicOnly,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (repo != null) {
            val signedIn by repo.signedIn.collectAsState()
            val linked by repo.linked.collectAsState()
            val busy by repo.busy.collectAsState()
            if (!signedIn) {
                Text(S.signInToImport, style = MaterialTheme.typography.bodyMedium)
            } else {
                IMPORT_PROVIDERS.forEach { p ->
                    ImportCard(repo, p, linked[p], busy == p, busy != null)
                }
            }
        }
        YouTubeCard()
        SpotifyOrAddButton(onOpenSpotify)
    }
}

@Composable
private fun AccountCardFrame(
    container: Color,
    content: Color,
    title: String,
    subtitle: String,
    logo: @Composable () -> Unit,
    onClick: (() -> Unit)?,
    body: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(content.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { logo() }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f))
                }
            }
            body()
        }
    }
}

@Composable
private fun ImportCard(
    repo: ImportRepository,
    provider: ProviderId,
    account: LinkedAccount?,
    working: Boolean,
    anyBusy: Boolean,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val service = Service.entries.firstOrNull { it.name == provider.name }
    val container = when (provider) {
        ProviderId.JAMENDO -> colors.primaryContainer
        ProviderId.AUDIUS -> colors.secondaryContainer
        else -> colors.tertiaryContainer
    }
    val content = when (provider) {
        ProviderId.JAMENDO -> colors.onPrimaryContainer
        ProviderId.AUDIUS -> colors.onSecondaryContainer
        else -> colors.onTertiaryContainer
    }
    val title = service?.title ?: provider.name.lowercase().replaceFirstChar { it.uppercase() }
    var input by remember { mutableStateOf("") }
    var deleteToo by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(account == null) }

    fun runImport(account: String?) {
        message = null
        scope.launch {
            repo.import(provider, account).fold(
                onSuccess = { message = S.importedSummary(it.likesAdded, it.playlistsAdded) },
                onFailure = { message = it.message },
            )
        }
    }

    AccountCardFrame(
        container = container,
        content = content,
        title = title,
        subtitle = account?.let { it.displayName ?: it.externalId } ?: S.notConnected,
        logo = { if (service != null) ServiceLogo(service, Modifier.size(28.dp), content) },
        onClick = if (account != null) ({ expanded = !expanded }) else null,
    ) {
        AnimatedVisibility(expanded || account == null) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = { runImport(input) },
                    ) { Text(if (working) S.importing else S.importAction) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = !anyBusy, onClick = { runImport(null) }) {
                            Text(if (working) S.importing else S.importAgain)
                        }
                        TextButton(
                            enabled = !anyBusy,
                            onClick = {
                                scope.launch {
                                    repo.disconnect(provider, deleteToo).onFailure { message = it.message }
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
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun YouTubeCard() {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    YouTubeLinkRow { ui ->
        AccountCardFrame(
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            title = Service.YOUTUBE.title,
            subtitle = if (ui.linked == true || ui.waiting) ytLinkSubtitle(ui) else S.notConnected,
            logo = { ServiceLogo(Service.YOUTUBE, Modifier.size(28.dp), colors.onTertiaryContainer) },
            onClick = if (ui.linked == true) ({ expanded = !expanded }) else null,
        ) {
            // While the status is still unknown (linked == null) nothing is shown: otherwise the card opens,
            // and when the status arrives the closing animation briefly shows the "connected" content
            AnimatedVisibility(expanded || ui.linked == false) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.linked == true) {
                        TextButton(onClick = ui.disconnect, enabled = !ui.busy) { Text(S.disconnectAccount) }
                        Text(
                            tr(
                                "Disconnecting revokes Solara's access at Google and deletes the token we hold. Your data on YouTube is not affected.",
                                "Відключення відкликає доступ Solara в Google і видаляє токен, який ми зберігаємо. Ваші дані на YouTube не зачіпаються.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(S.youtubeAccountNote, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = ui.connect, enabled = ui.linked != null && !ui.waiting) { Text(S.connect) }
                    }
                    if (ui.waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    ui.message?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** "+" like on Home and Search: Spotify is entered only through it, as text without a logo (Spotify design rules). */
@Composable
private fun AddServiceButton(onOpenSpotify: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                AddIcon(Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Spotify") },
                onClick = { open = false; onOpenSpotify() },
            )
        }
    }
}


/** Spotify card: shown only after the user connected Spotify; otherwise the "+" entry stays. Neutral colors, no logo. */
@Composable
private fun SpotifyOrAddButton(onOpenSpotify: () -> Unit) {
    val remote = LocalSpotifyRemote.current
    val flow = remember(remote) { remote?.state ?: MutableStateFlow(SpotifyState()) }
    val st by flow.collectAsState()
    val lib = st.library
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    val active = remote != null && (st.connected || st.connecting || lib.status != LibraryStatus.IDLE)
    if (!active || remote == null) {
        AddServiceButton(onOpenSpotify)
        return
    }
    val subtitle = when {
        lib.status == LibraryStatus.READY ->
            lib.accountName?.let { tr("Connected as $it", "Підключено як $it") } ?: tr("Connected", "Підключено")
        lib.status == LibraryStatus.LOADING || lib.status == LibraryStatus.AUTHORIZING || st.connecting ->
            tr("Connecting…", "Підключення…")
        st.connected -> tr("Spotify app connected", "Підключено застосунок Spotify")
        else -> S.notConnected
    }
    AccountCardFrame(
        container = colors.surfaceVariant,
        content = colors.onSurfaceVariant,
        title = "Spotify",
        subtitle = subtitle,
        logo = { Text("♪", style = MaterialTheme.typography.headlineSmall) },
        onClick = { expanded = !expanded },
    ) {
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenSpotify) { Text(tr("Spotify section", "Розділ Spotify")) }
                    TextButton(onClick = { if (st.appInstalled) remote.openSpotifyApp() else remote.getSpotifyApp() }) {
                        Text(if (st.appInstalled) "Open Spotify" else "Get Spotify Free")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { remote.forgetLibrary(); remote.disconnect() }) { Text(S.disconnectSpotify) }
                    TextButton(onClick = { uriHandler.openUri(LegalTexts.SPOTIFY_APPS_URL) }) {
                        Text(tr("Revoke access", "Відкликати доступ"))
                    }
                }
                Text(
                    tr(
                        "Spotify data is kept only in memory and is erased when you disconnect or close the app.",
                        "Дані Spotify зберігаються лише в пам’яті й стираються під час відключення або закриття застосунку.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                lib.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
