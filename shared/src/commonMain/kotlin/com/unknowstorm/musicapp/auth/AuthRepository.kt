package com.unknowstorm.musicapp.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface AuthState {
    data object Loading : AuthState
    data object Guest : AuthState
    data class SignedIn(val userId: String, val email: String?, val username: String?) : AuthState
    data class Error(val message: String) : AuthState
}

enum class SignUpResult { SignedIn, ConfirmEmail }

@Serializable
private data class AccountRow(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
)

class AuthRepository(private val client: SupabaseClient = supabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Initializing -> _state.value = AuthState.Loading
                    is SessionStatus.Authenticated -> publishUser()
                    is SessionStatus.NotAuthenticated -> _state.value = AuthState.Guest
                    // нет сети при обновлении токена: сессия сохранена, пользователь остаётся «вошедшим»
                    is SessionStatus.RefreshFailure -> {
                        if (client.auth.currentUserOrNull() != null) publishUser()
                        else _state.value = AuthState.Guest
                    }
                }
            }
        }
    }

    private suspend fun publishUser() {
        val user = client.auth.currentUserOrNull() ?: run {
            _state.value = AuthState.Guest
            return
        }
        _state.value = AuthState.SignedIn(user.id, user.email, null)
        val name = runCatching {
            client.from("account")
                .select { filter { eq("user_id", user.id) } }
                .decodeSingleOrNull<AccountRow>()?.username
        }.getOrNull()
        (_state.value as? AuthState.SignedIn)?.takeIf { it.userId == user.id }?.let {
            _state.value = it.copy(username = name)
        }
    }

    suspend fun signUp(email: String, password: String): Result<SignUpResult> = safe {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        if (client.auth.currentSessionOrNull() != null) SignUpResult.SignedIn else SignUpResult.ConfirmEmail
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = safe {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Открывает браузер; результат вернётся через диплинк (Android) или локальный сервер (Desktop). */
    suspend fun signInWithGoogle(): Result<Unit> = safe { client.auth.signInWith(Google) }

    suspend fun resetPassword(email: String): Result<Unit> = safe {
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_REDIRECT)
    }

    suspend fun signOut(): Result<Unit> = safe { client.auth.signOut() }

    private suspend inline fun <T> safe(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e), e))
        }

    private fun friendly(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "Invalid login credentials" in m -> "Неверная почта или пароль"
            "already registered" in m || "already been registered" in m -> "Эта почта уже зарегистрирована"
            "Password should be" in m -> "Пароль слишком простой (минимум 6 символов)"
            "Email not confirmed" in m -> "Сначала подтверди почту по ссылке из письма"
            "rate limit" in m.lowercase() -> "Слишком много попыток, подожди немного"
            "Unable to resolve" in m || "timeout" in m.lowercase() ||
                    "UnknownHost" in e::class.simpleName.orEmpty() -> "Нет сети"
            else -> "Не получилось: ${m.take(120)}"
        }
    }
}