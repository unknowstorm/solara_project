package com.unknowstorm.musicapp.auth

import android.content.Intent
import io.github.jan.supabase.auth.handleDeeplinks

/** Called from MainActivity: passes the return from the browser (Google, email, password reset) to Supabase. */
fun handleAuthDeepLink(intent: Intent?) {
    if (intent == null) return
    supabase.handleDeeplinks(intent)
}
