package com.unknowstorm.musicapp.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Языки интерфейса. Основной язык приложения английский. */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    UK("uk", "Українська");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** Язык по умолчанию: украинский, если он язык системы, иначе английский. */
        fun system(): AppLanguage = if (systemLanguageCode().startsWith("uk")) UK else EN
    }
}

/** Ключ в постоянном хранилище, под которым лежит выбранный язык. */
const val LANGUAGE_KEY = "app_language"

object I18n {
    /** Текущий язык. Это состояние Compose: всё, что читает S.xxx, перерисовывается при смене языка. */
    var language: AppLanguage by mutableStateOf(AppLanguage.EN)
}

/** Выбор нужной версии текста по текущему языку. */
internal fun tr(en: String, uk: String): String = if (I18n.language == AppLanguage.UK) uk else en
