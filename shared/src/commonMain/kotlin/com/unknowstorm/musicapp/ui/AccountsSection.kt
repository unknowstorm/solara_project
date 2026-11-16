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
        SpotifyCard(onOpenSpotify)
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


/** Spotify card: always shown on Android, same layout as the others. Not connected: status line + "Connect". Neutral colors, no logo. */
@Composable
private fun SpotifyCard(onOpenSpotify: () -> Unit) {
    val remote = LocalSpotifyRemote.current ?: return
    val st by remote.state.collectAsState()
    val lib = st.library
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(remote) { remote.refreshProfile() }
    val working = st.connecting || lib.status == LibraryStatus.LOADING || lib.status == LibraryStatus.AUTHORIZING
    val connected = st.connected || lib.status == LibraryStatus.READY || lib.accountName != null
    val subtitle = when {
        working -> tr("Connecting…", "Підключення…")
        lib.accountName != null -> tr("Connected as ${lib.accountName}", "Підключено як ${lib.accountName}")
        st.connected -> tr("Spotify app connected", "Підключено застосунок Spotify")
        lib.status == LibraryStatus.READY -> tr("Connected", "Підключено")
        else -> S.notConnected
    }
    AccountCardFrame(
        container = spotifyContainer(colors),
        content = colors.onSurface,
        title = "Spotify",
        subtitle = subtitle,
        logo = {
            androidx.compose.material3.Icon(
                GenericNote, contentDescription = null,
                modifier = Modifier.size(28.dp), tint = colors.onSurface,
            )
        },
        onClick = if (connected) ({ expanded = !expanded }) else null,
    ) {
        AnimatedVisibility(expanded || !connected) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected) {
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
                } else {
                    Text(
                        tr(
                            "Music plays in the Spotify app (Premium is required). Your Spotify library is shown only in memory.",
                            "Музику відтворює застосунок Spotify (потрібен Premium). Ваша бібліотека Spotify показується лише в пам’яті.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (st.appInstalled) {
                        Button(onClick = { remote.connect() }, enabled = !working) { Text(S.connect) }
                    } else {
                        Button(onClick = { remote.getSpotifyApp() }) { Text("Get Spotify Free") }
                    }
                }
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                (st.error ?: lib.error)?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/**
 * Spotify card: a neutral surface with a hint of the theme accent. If the accent is greenish
 * (so the card could look like Spotify's own brand green), the plain neutral surface is used.
 */
private fun spotifyContainer(c: androidx.compose.material3.ColorScheme): Color {
    if (isGreenish(c.primary) || isGreenish(c.primaryContainer)) return c.surfaceVariant
    return androidx.compose.ui.graphics.lerp(c.surfaceVariant, c.primary, 0.16f)
}

private fun isGreenish(c: Color): Boolean {
    val r = c.red; val g = c.green; val b = c.blue
    val max = maxOf(r, g, b); val d = max - minOf(r, g, b)
    if (d < 0.05f) return false // almost gray: no real hue
    var h = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
    if (h < 0f) h += 360f
    return h in 75f..175f
}

/** A generic "music" note (Material Icons): not Spotify's logo, so no circle or waves on purpose. */
private val GenericNote: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "GenericNote",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.addPathNodes(
            "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
        ),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    ).build()
}
