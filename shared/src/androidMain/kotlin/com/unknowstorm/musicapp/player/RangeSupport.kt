package com.unknowstorm.musicapp.player

import java.util.concurrent.ConcurrentHashMap

/**
 * Треки, которые сервер отдаёт только целиком (на запрос части файла отвечает 200).
 * Сервис воспроизведения и плеер живут в одном процессе, поэтому хватает общего объекта.
 */
object RangeSupport {
    private val noRange = ConcurrentHashMap.newKeySet<String>()

    fun set(key: String, unsupported: Boolean) {
        if (unsupported) noRange.add(key) else noRange.remove(key)
    }

    fun isNoRange(key: String): Boolean = key in noRange
}
