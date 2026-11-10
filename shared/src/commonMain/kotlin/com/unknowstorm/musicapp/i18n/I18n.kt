package com.unknowstorm.musicapp.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Interface languages. The app's primary language is English. */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    UK("uk", "Українська");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** Default language: Ukrainian if it is the system language, otherwise English. */
        fun system(): AppLanguage = if (systemLanguageCode().startsWith("uk")) UK else EN
    }
}

/** Key in the persistent storage under which the selected language is kept. */
const val LANGUAGE_KEY = "app_language"

object I18n {
    /** Current language. This is Compose state: everything that reads S.xxx is redrawn when the language changes. */
    var language: AppLanguage by mutableStateOf(AppLanguage.EN)
}

/** Picks the right text version for the current language. */
internal fun tr(en: String, uk: String): String = if (I18n.language == AppLanguage.UK) uk else en
