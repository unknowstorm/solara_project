package com.unknowstorm.musicapp.model

/** Как источник играет музыку. */
enum class PlaybackKind {
    IN_APP,     // звук идёт в наш плеер
    REMOTE,     // пульт к чужому приложению (Spotify)
    EMBEDDED,   // встроенный видимый плеер (YouTube)
    PREVIEW,    // только 30-секундные превью (Deezer)
}

enum class Service(
    val title: String,
    val kind: PlaybackKind?,
    val ready: Boolean,
    val note: String,
) {
    ALL("Все", null, true, "Все доступные источники"),
    JAMENDO("Jamendo", PlaybackKind.IN_APP, true, "Полные треки внутри приложения"),
    AUDIUS("Audius", PlaybackKind.IN_APP, true, "Полные треки внутри приложения"),
    SPOTIFY("Spotify", PlaybackKind.REMOTE, false, "Пульт к приложению Spotify, нужен Premium"),
    YOUTUBE("YouTube", PlaybackKind.EMBEDDED, false, "Режим видео, без фонового воспроизведения"),
    DEEZER("Deezer", PlaybackKind.PREVIEW, false, "Каталог и 30-секундные превью"),
}

/** Источник трека как значок/чип интерфейса. */
fun ProviderId.toService(): Service? = Service.entries.firstOrNull { it.name == name }
