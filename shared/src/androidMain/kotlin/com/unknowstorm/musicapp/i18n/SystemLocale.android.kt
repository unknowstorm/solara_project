package com.unknowstorm.musicapp.i18n

actual fun systemLanguageCode(): String = java.util.Locale.getDefault().language
