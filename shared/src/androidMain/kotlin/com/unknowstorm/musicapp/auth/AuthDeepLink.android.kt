package com.unknowstorm.musicapp.auth

import android.content.Intent
import io.github.jan.supabase.auth.handleDeeplinks

/** Вызывается из MainActivity: передаёт возврат из браузера (Google, письмо, сброс пароля) в Supabase. */
fun handleAuthDeepLink(intent: Intent?) {
    if (intent == null) return
    supabase.handleDeeplinks(intent)
}
