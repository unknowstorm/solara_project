package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.provider.youtube.YouTubeLinkRepository
import com.unknowstorm.musicapp.provider.youtube.YtLinkException
import com.unknowstorm.musicapp.provider.youtube.YtLinkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun ytLinkMessage(e: Throwable): String = when ((e as? YtLinkException)?.code) {
    "unauthorized" -> tr("Sign in to your Solara account first.", "Спочатку увійдіть в акаунт Solara.")
    "revoked" -> tr("Access was revoked. Connect YouTube again.", "Доступ відкликано. Підключіть YouTube знову.")
    "quota" -> tr("The daily YouTube request limit has been reached. Please try again tomorrow.", "Денний ліміт запитів YouTube вичерпано. Спробуйте завтра.")
    else -> tr("Could not reach YouTube. Try again.", "Не вдалося звернутися до YouTube. Спробуйте ще раз.")
}

/** What the link row shows: the state and two actions. The look is up to the caller (a plain row or a card). */
class YtLinkUi(
    val linked: Boolean?,
    /** The title of the connected channel, if the server returned it. Kept in memory only. */
    val account: String?,
    val waiting: Boolean,
    val busy: Boolean,
    val message: String?,
    val connect: () -> Unit,
    val disconnect: () -> Unit,
)

internal fun ytLinkSubtitle(ui: YtLinkUi): String = when {
    ui.linked == true -> ui.account?.let { tr("Connected as $it", "Підключено як $it") }
        ?: tr("Connected, read-only", "Підключено, лише читання")
    ui.waiting -> tr("Waiting for confirmation in the browser…", "Чекаємо підтвердження в браузері…")
    else -> tr("Your playlists and liked videos, read-only", "Ваші плейлисти та вподобані відео, лише читання")
}

/**
 * Connect / disconnect your YouTube account (read-only). Sign-in happens on Google's page in the browser;
 * the token never reaches the app. The user must accept the YouTube terms and the privacy policy first.
 * [content] draws the row; by default it is the plain row used on the YouTube screen.
 */
@Composable
fun YouTubeLinkRow(
    modifier: Modifier = Modifier,
    onLinked: (Boolean) -> Unit = {},
    content: @Composable (YtLinkUi) -> Unit = { DefaultYouTubeLinkRow(it) },
) {
    val repo = remember { YouTubeLinkRepository() }
    val scope = rememberCoroutineScope()
    val openLink = rememberExternalLinkOpener()
    var linked by remember { mutableStateOf<Boolean?>(null) }
    var account by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var askConsent by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val report by rememberUpdatedState(onLinked)

    suspend fun status(): YtLinkStatus? = try {
        repo.status()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    LaunchedEffect(Unit) {
        val st = status()
        linked = st?.linked ?: false
        account = if (st?.linked == true) st.account else null
    }
    LaunchedEffect(linked) { linked?.let { report(it) } }

    // Google returns the user to the app; in case the link back does not work, ask the server every few seconds
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        repeat(40) {
            delay(3000)
            val st = status()
            if (st?.linked == true) {
                linked = true
                account = st.account
                waiting = false
                return@LaunchedEffect
            }
        }
        waiting = false
    }

    suspend fun connect() {
        try {
            openLink(repo.start())
            waiting = true
            message = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = ytLinkMessage(e)
        }
    }

    val ui = YtLinkUi(
        linked = linked,
        account = account,
        waiting = waiting,
        busy = busy,
        message = message,
        connect = { askConsent = true },
        disconnect = { confirm = true },
    )
    Box(modifier.fillMaxWidth()) { content(ui) }

    // The consent gate shows the YouTube terms and the privacy policy if they were not accepted yet
    if (askConsent) {
        Dialog(
            onDismissRequest = { askConsent = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                YouTubeConsentGate(onLeave = { askConsent = false }) {
                    LaunchedEffect(Unit) {
                        askConsent = false
                        // The dialog closes right now, so the request runs in the outer scope
                        scope.launch { connect() }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("Disconnect YouTube?", "Відключити YouTube?")) },
            text = {
                Text(
                    tr(
                        "Access will be revoked at Google and the saved token deleted from our server. This does not affect your data on YouTube itself.",
                        "Доступ буде відкликано в Google, а збережений токен видалено з нашого сервера. Це не зачіпає ваші дані на самому YouTube.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        busy = true
                        try {
                            repo.disconnect()
                            linked = false
                            account = null
                            message = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = ytLinkMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(tr("Disconnect and delete my data", "Відключити й видалити мої дані")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(S.cancel) } },
        )
    }
}

@Composable
private fun DefaultYouTubeLinkRow(ui: YtLinkUi) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YouTube", style = MaterialTheme.typography.titleMedium)
                Text(
                    ytLinkSubtitle(ui),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.linked == true) {
                TextButton(onClick = ui.disconnect, enabled = !ui.busy) { Text(tr("Disconnect", "Відключити")) }
            } else {
                Button(onClick = ui.connect, enabled = ui.linked != null && !ui.waiting) {
                    Text(tr("Connect", "Підключити"))
                }
            }
        }
        if (ui.waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
