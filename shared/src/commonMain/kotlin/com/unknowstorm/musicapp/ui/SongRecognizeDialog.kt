package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr

private const val CLIP_SECONDS = 10

private sealed interface RecState {
    data object Listening : RecState
    data object Searching : RecState
    class Done(val outcome: RecognizeOutcome) : RecState
    data object Denied : RecState
    data object Unsupported : RecState
    data object Error : RecState
}

/**
 * "What's playing?" dialog: records a short clip, sends it for recognition and shows the result.
 * The clip is deleted right after sending. A found song (title, artist, album) is added by SongRecognizer to the list
 * on this phone (RecognizedHistory); links to other services are not saved.
 */
@Composable
fun SongRecognizeDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    val recorder = rememberSongRecorder()
    val recognizer = remember { SongRecognizer() }
    val uriHandler = LocalUriHandler.current
    val spotifyRemote = com.unknowstorm.musicapp.spotify.LocalSpotifyRemote.current
    var attempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<RecState>(RecState.Listening) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        state = RecState.Listening
        started = false
        state = when (val rec = recorder.record(CLIP_SECONDS, onStarted = { started = true })) {
            is RecordResult.Ok -> {
                state = RecState.Searching
                RecState.Done(recognizer.recognize(rec.bytes, rec.mime))
            }
            RecordResult.PermissionDenied -> RecState.Denied
            RecordResult.Unsupported -> RecState.Unsupported
            RecordResult.Failed -> RecState.Error
        }
    }

    val st = state
    val found = (st as? RecState.Done)?.outcome as? RecognizeOutcome.Found
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("What's playing?", "Що за пісня?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (st) {
                    RecState.Listening, RecState.Searching -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text(
                            when {
                                st == RecState.Searching -> tr("Recognizing…", "Розпізнаю…")
                                started -> tr("Listening… hold your phone near the music ($CLIP_SECONDS s)", "Слухаю… піднесіть телефон до музики ($CLIP_SECONDS с)")
                                else -> tr("Starting the microphone…", "Вмикаю мікрофон…")
                            },
                        )
                    }
                    is RecState.Done -> when (val o = st.outcome) {
                        is RecognizeOutcome.Found -> {
                            Text(o.song.title, style = MaterialTheme.typography.titleLarge)
                            Text(o.song.artist, style = MaterialTheme.typography.titleMedium)
                            o.song.album?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(
                                tr("Recognition by AudD.", "Розпізнавання виконує AudD."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RecognizeOutcome.NotFound -> Text(tr("Could not recognize this song. Try again closer to the sound source.", "Не вдалося розпізнати пісню. Спробуйте ще раз ближче до джерела звуку."))
                        RecognizeOutcome.NeedSignIn -> Text(tr("Sign in to your Solara account to recognize songs.", "Увійдіть в обліковий запис Solara, щоб розпізнавати пісні."))
                        RecognizeOutcome.RateLimit -> Text(tr("Daily limit of recognitions reached. Try again tomorrow.", "Досягнуто денного ліміту розпізнавань. Спробуйте завтра."))
                        RecognizeOutcome.Unavailable -> Text(tr("Recognition is temporarily unavailable.", "Розпізнавання тимчасово недоступне."))
                        RecognizeOutcome.Failed -> Text(tr("Something went wrong. Check your connection and try again.", "Щось пішло не так. Перевірте з'єднання та спробуйте ще раз."))
                    }
                    RecState.Denied -> Text(tr("Microphone access is needed to listen to the music. You can allow it in the system settings.", "Для розпізнавання потрібен доступ до мікрофона. Дозволити його можна в налаштуваннях системи."))
                    RecState.Unsupported -> Text(tr("Song recognition is available on Android.", "Розпізнавання пісень доступне на Android."))
                    RecState.Error -> Text(tr("Could not record audio. Try again.", "Не вдалося записати звук. Спробуйте ще раз."))
                }
            }
        },
        confirmButton = {
            if (found != null) {
                TextButton(onClick = { onSearch("${found.song.artist} ${found.song.title}"); onDismiss() }) {
                    Text(tr("Find in Solara", "Знайти в Solara"))
                }
            } else if (st !is RecState.Listening && st !is RecState.Searching && st != RecState.Unsupported) {
                TextButton(onClick = { attempt++ }) { Text(tr("Try again", "Спробувати ще")) }
            }
        },
        dismissButton = {
            // The Spotify link is a separate text-only button, never next to another brand's mark
            if (found?.song?.spotifyUrl != null) {
                TextButton(onClick = {
                    // Inside Solara: the Spotify section plays the track (Spotify app and Premium needed); otherwise the link opens
                    val track = spotifyTrackUri(found.song.spotifyUrl)
                    if (track != null && spotifyRemote != null) {
                        PendingSpotify.uri = track
                        onDismiss()
                    } else {
                        runCatching { uriHandler.openUri(found.song.spotifyUrl) }
                    }
                }) {
                    Text("Open Spotify")
                }
            }
            TextButton(onClick = onDismiss) { Text(tr("Close", "Закрити")) }
        },
    )
}

/** spotify:track:ID from a link like https://open.spotify.com/track/ID?si=... (null for other kinds of links). */
private fun spotifyTrackUri(url: String): String? =
    Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?track/([A-Za-z0-9]+)""").find(url)?.groupValues?.get(1)?.let { "spotify:track:$it" }
