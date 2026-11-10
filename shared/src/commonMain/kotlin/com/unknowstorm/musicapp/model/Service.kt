package com.unknowstorm.musicapp.model

import com.unknowstorm.musicapp.i18n.S

/** How a source plays music. */
enum class PlaybackKind {
    IN_APP,     // sound goes into our player
    REMOTE,     // remote control for another app (Spotify)
    EMBEDDED,   // embedded visible player (YouTube)
    PREVIEW,    // 30-second previews only (Deezer)
}

enum class Service(
    private val titleFn: () -> String,
    val kind: PlaybackKind?,
    val ready: Boolean,
    private val noteFn: () -> String,
) {
    ALL({ S.all }, null, true, { S.allAvailableSources }),
    JAMENDO({ "Jamendo" }, PlaybackKind.IN_APP, true, { S.fullTracksInsideTheApp }),
    AUDIUS({ "Audius" }, PlaybackKind.IN_APP, true, { S.fullTracksInsideTheApp }),
    SPOTIFY({ "Spotify" }, PlaybackKind.REMOTE, true, { S.remoteControlForTheSpotify }),
    YOUTUBE({ "YouTube" }, PlaybackKind.EMBEDDED, true, { S.videoInTheBuiltIn }),
    DEEZER({ "Deezer" }, PlaybackKind.PREVIEW, true, { S.catalogAnd30SecondPreviews });

    /** Name and description are read on every access, so they change together with the language. */
    val title: String get() = titleFn()
    val note: String get() = noteFn()
}

/** The source of a track as an icon/chip of the UI. */
fun ProviderId.toService(): Service? = Service.entries.firstOrNull { it.name == name }
