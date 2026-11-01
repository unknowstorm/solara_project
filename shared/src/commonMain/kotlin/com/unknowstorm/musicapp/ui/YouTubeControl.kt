package com.unknowstorm.musicapp.ui

/**
 * Мост между нашими кнопками и единственным встроенным плеером YouTube.
 * Платформа (Android) подставляет send и вызывает onState на изменения состояния плеера.
 * Команды: "play" и "pause" (методы официального IFrame Player API).
 */
object YouTubeControl {
    var send: ((String) -> Unit)? = null
    var onState: ((Int) -> Unit)? = null
    /** Позиция и длительность в секундах: плеер сообщает их несколько раз в секунду. */
    var onTime: ((Double, Double) -> Unit)? = null
}
