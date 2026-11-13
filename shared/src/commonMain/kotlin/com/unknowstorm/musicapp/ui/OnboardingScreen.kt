package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AvatarUpdate
import com.unknowstorm.musicapp.auth.Profile
import com.unknowstorm.musicapp.imports.ImportHolder
import com.unknowstorm.musicapp.imports.ImportSection
import kotlinx.coroutines.launch

private val NickRegex = Regex("^[a-zA-Z0-9_]{3,20}$")

private val ServiceChoices = listOf(
    "soundcloud" to "SoundCloud",
    "spotify" to "Spotify",
    "youtube" to "YouTube",
)

/** The "chain" icon (Material Symbols, link). Drawn here so as not to pull in an icon package. */
private val LinkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Link",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1z" +
                "M8,13h8v-2H8v2z" +
                "M17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
fun OnboardingScreen(
    repo: AuthRepository,
    profile: Profile,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var nick by remember { mutableStateOf(profile.username.orEmpty()) }
    var syncPalette by remember { mutableStateOf(profile.syncPalette) }
    var services by remember { mutableStateOf(profile.services.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Avatar: an already saved photo, a just picked one, or "remove photo"
    val savedImage = rememberAvatarImage(repo, profile.avatar)
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    val pickedImage = remember(pickedBytes) { pickedBytes?.let { decodeImageBitmap(it) } }
    val shownImage = if (removePhoto) null else pickedImage ?: savedImage
    val pickPhoto = rememberAvatarPicker { bytes ->
        pickedBytes = bytes
        removePhoto = false
        error = null
    }

    val nickValid = NickRegex.matches(nick.trim())
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val transparentItem = ListItemDefaults.colors(containerColor = Color.Transparent)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (onDone != null) {
                    TextButton(onClick = onDone, enabled = !busy) { Text(S.back) }
                    Text(S.profile, style = MaterialTheme.typography.headlineLarge)
                } else {
                    Text(S.emailConfirmed, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(S.tellUsAboutYourself, style = MaterialTheme.typography.headlineLarge)
                }

                // Avatar: a photo or the first letter of the nickname on a palette color
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UserAvatar(
                        name = nick,
                        size = 96.dp,
                        image = shownImage,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = !busy) { pickPhoto() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = pickPhoto, enabled = !busy) {
                            Text(if (shownImage == null) S.uploadPhoto else S.anotherPhoto)
                        }
                        if (shownImage != null) {
                            TextButton(
                                onClick = { pickedBytes = null; removePhoto = true },
                                enabled = !busy,
                            ) { Text(S.remove) }
                        }
                    }
                }

                // Nickname
                OutlinedTextField(
                    value = nick,
                    onValueChange = { nick = it.take(20); error = null },
                    label = { Text(S.nickname) },
                    singleLine = true,
                    isError = nick.isNotEmpty() && !nickValid,
                    supportingText = { Text(S.s320CharactersLatinLetters) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Palette sync
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = cardColors) {
                    ListItem(
                        headlineContent = { Text(S.syncTheColorPalette) },
                        supportingContent = { Text(S.acrossAllYourDevicesVia) },
                        trailingContent = { Switch(checked = syncPalette, onCheckedChange = { syncPalette = it }) },
                        colors = transparentItem,
                    )
                }

                // Services: square buttons with rounded corners
                Text(S.services, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ServiceChoices.forEach { (key, title) ->
                        ServiceTile(
                            title = title,
                            selected = key in services,
                            onClick = { services = if (key in services) services - key else services + key },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Импорт из Jamendo и Audius и подключение YouTube (вместо старой заглушки)
                ImportHolder.repo?.let { ImportSection(it) }
                YouTubeLinkRow()

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            val picked = pickedBytes
                            val avatarUpdate = when {
                                picked != null -> AvatarUpdate.Upload(picked)
                                removePhoto -> AvatarUpdate.Remove
                                else -> AvatarUpdate.Keep
                            }
                            // On success the profile is marked as filled and App will close this screen itself
                            repo.saveOnboarding(nick.trim(), avatarUpdate, syncPalette, services)
                                .onSuccess { onDone?.invoke() }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = nickValid && !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(S.done)
                    }
                }

                if (onDone == null) {
                    TextButton(
                        onClick = { scope.launch { repo.signOut() } },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(S.signOutOfAccount) }
                }
            }
        }
    }
}

@Composable
private fun ServiceTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.aspectRatio(1f).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = container,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(LinkIcon, contentDescription = null, tint = content, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                if (selected) S.selected else S.connect,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
