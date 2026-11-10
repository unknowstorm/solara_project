package com.unknowstorm.musicapp.model

import com.unknowstorm.musicapp.i18n.S

/** Как источник играет музыку. */
enum class PlaybackKind {
    IN_APP,     // звук идёт в наш плеер
    REMOTE,     // пульт к чужому приложению (Spotify)
    EMBEDDED,   // встроенный видимый плеер (YouTube)
    PREVIEW,    // только 30-секундные превью (Deezer)
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

    /** Название и описание берутся при каждом чтении, поэтому меняются вместе с языком. */
    val title: String get() = titleFn()
    val note: String get() = noteFn()
}

/** Источник трека как значок/чип интерфейса. */
fun ProviderId.toService(): Service? = Service.entries.firstOrNull { it.name == name }
