package com.unknowstorm.musicapp.i18n

/**
 * Все тексты интерфейса: tr(English, Українська).
 * Записи добавляет tools/i18n_apply.py; над каждой стоит комментарий с исходным русским текстом
 * (по нему ищутся повторы). Править переводы можно прямо здесь.
 */
object S {
    // ru: "Главная"
    val home: String get() = tr("Home", "Головна")
    // ru: "Поиск"
    val search: String get() = tr("Search", "Пошук")
    // ru: "Очередь"
    val queue: String get() = tr("Queue", "Черга")
    // ru: "Настройки"
    val settings: String get() = tr("Settings", "Налаштування")
    // ru: "Язык"
    val language: String get() = tr("Language", "Мова")
    // ru: "Сменить пароль"
    val changePassword: String get() = tr("Change password", "Змінити пароль")
    // ru: "Источники музыки"
    val musicSources: String get() = tr("Music sources", "Джерела музики")
    // ru: "Работает. {0}"
    fun available(p0: Any?): String = tr("Available. ${p0}", "Працює. ${p0}")
    // ru: "Скоро. {0}"
    fun comingSoon(p0: Any?): String = tr("Coming soon. ${p0}", "Незабаром. ${p0}")
    // ru: "Все"
    val all: String get() = tr("All", "Усі")
    // ru: "Все доступные источники"
    val allAvailableSources: String get() = tr("All available sources", "Усі доступні джерела")
    // ru: "Полные треки внутри приложения"
    val fullTracksInsideTheApp: String get() = tr("Full tracks inside the app", "Повні треки всередині застосунку")
    // ru: "Пульт к приложению Spotify, нужен Premium"
    val remoteControlForTheSpotify: String get() = tr("Remote control for the Spotify app, requires Premium", "Пульт до застосунку Spotify, потрібен Premium")
    // ru: "Видео во встроенном плеере: Главная → YouTube. Без фонового воспроизведения"
    val videoInTheBuiltIn: String get() = tr("Video in the built-in player: Home → YouTube. No background playback", "Відео у вбудованому плеєрі: Головна → YouTube. Без фонового відтворення")
    // ru: "Каталог и 30-секундные превью"
    val catalogAnd30SecondPreviews: String get() = tr("Catalog and 30-second previews", "Каталог і 30-секундні прев’ю")
    // <<< ENTRIES >>>
}
