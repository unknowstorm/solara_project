package com.unknowstorm.musicapp.auth

import com.unknowstorm.musicapp.Secrets
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

const val AUTH_SCHEME = "solara"
const val AUTH_HOST = "auth"
const val AUTH_REDIRECT = "$AUTH_SCHEME://$AUTH_HOST"

val supabase by lazy {
    createSupabaseClient(Secrets.SUPABASE_URL, Secrets.SUPABASE_ANON_KEY) {
        install(Auth) {
            scheme = AUTH_SCHEME
            host = AUTH_HOST
        }
        install(Postgrest)
    }
}