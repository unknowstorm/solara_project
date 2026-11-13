package com.unknowstorm.musicapp.spotify

import com.unknowstorm.musicapp.i18n.tr

/** User-facing text for a failed library import (tr() is internal to this module, so it lives here). */
fun spotifyLibraryErrorText(e: Exception): String = when ((e as? SpotifyHttpError)?.code) {
    401 -> tr("Spotify session expired. Tap “Import” again.", "Сесія Spotify закінчилась. Натисніть «Імпортувати» ще раз.")
    403 -> tr(
        "Spotify did not allow access. In development mode the Web API works only for the app owner and users added in the Spotify dashboard (up to 5), and the owner needs Premium.",
        "Spotify не дав доступ. У режимі розробки Web API працює лише для власника застосунку та користувачів, доданих у панелі Spotify (до 5), і власнику потрібен Premium.",
    )
    429 -> tr("Spotify asks to wait a bit. Try again in a minute.", "Spotify просить трохи зачекати. Спробуйте за хвилину.")
    else -> tr("Could not read the library from Spotify.", "Не вдалося прочитати бібліотеку зі Spotify.") +
        " (" + (e.message ?: e::class.simpleName) + ")"
}

fun spotifyAccessRevokedText(): String = tr(
    "Access to Spotify was revoked or expired. Tap “Import” to sign in again.",
    "Доступ до Spotify відкликано або він закінчився. Натисніть «Імпортувати», щоб увійти знову.",
)
