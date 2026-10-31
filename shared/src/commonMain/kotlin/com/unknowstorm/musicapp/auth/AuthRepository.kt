package com.unknowstorm.musicapp.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

/**
 * Данные профиля из таблицы `account`.
 * [avatar] — null (показываем первую букву ника) или метка версии загруженного фото вида "u1a2b3c":
 * сам файл лежит в бакете `avatars` по пути `<user_id>/avatar.jpg`.
 */
data class Profile(
    val username: String? = null,
    val avatar: String? = null,
    val syncPalette: Boolean = false,
    val services: List<String> = emptyList(),
    val onboarded: Boolean = false,
)

/** Что сделать с аватаркой при сохранении профиля. */
sealed interface AvatarUpdate {
    /** Оставить как есть. */
    data object Keep : AvatarUpdate

    /** Убрать фото: вернётся буква ника. */
    data object Remove : AvatarUpdate

    /** Загрузить новое фото (JPEG, уже обрезанный под квадрат). */
    class Upload(val jpeg: ByteArray) : AvatarUpdate
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Guest : AuthState
    data class SignedIn(
        val userId: String,
        val email: String?,
        val username: String?,
        /** null — профиль ещё не загрузился (или не удалось загрузить). */
        val profile: Profile? = null,
    ) : AuthState
    data class Error(val message: String) : AuthState
}

enum class SignUpResult { SignedIn, ConfirmEmail }

/** Регистрация отправлена, ждём подтверждения письма. Пароль живёт только в памяти. */
data class PendingConfirmation(val email: String, val password: String)

@Serializable
private data class AccountRow(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    val avatar: String? = null,
    @SerialName("sync_palette") val syncPalette: Boolean = false,
    val services: List<String> = emptyList(),
    val onboarded: Boolean = false,
)

/** Параметры серверной функции `complete_onboarding`. */
@Serializable
private data class OnboardingParams(
    @SerialName("p_username") val username: String,
    @SerialName("p_avatar") val avatar: String?,
    @SerialName("p_sync_palette") val syncPalette: Boolean,
    @SerialName("p_services") val services: List<String>,
)

private const val AVATAR_BUCKET = "avatars"

class AuthRepository(private val client: SupabaseClient = supabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _pending = MutableStateFlow<PendingConfirmation?>(null)
    val pending: StateFlow<PendingConfirmation?> = _pending.asStateFlow()

    /** Скачанные аватарки: ключ "<user_id>/<метка версии>". */
    private val avatarCache = mutableMapOf<String, ByteArray>()

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
        _pending.value = null
        // Если это тот же пользователь (например, обновился токен), не сбрасываем уже загруженный профиль,
        // иначе экран онбординга мигнёт и потеряет введённое
        val prev = (_state.value as? AuthState.SignedIn)?.takeIf { it.userId == user.id }
        _state.value = prev ?: AuthState.SignedIn(user.id, user.email, null)
        val profile = runCatching { loadProfile(user.id) }.getOrNull()
        (_state.value as? AuthState.SignedIn)?.takeIf { it.userId == user.id }?.let {
            _state.value = it.copy(username = profile?.username ?: it.username, profile = profile ?: it.profile)
        }
    }

    private suspend fun loadProfile(userId: String): Profile {
        val row = client.from("account")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<AccountRow>()
        // Строка в `account` появляется только после «Готово» в онбординге.
        // Нет строки: почта подтверждена, но профиль ещё не заполнен, поэтому покажем онбординг
        return row?.let {
            Profile(it.username, it.avatar, it.syncPalette, it.services, it.onboarded)
        } ?: Profile()
    }

    suspend fun signUp(email: String, password: String): Result<SignUpResult> = safe {
        val created = client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        if (client.auth.currentSessionOrNull() != null) {
            SignUpResult.SignedIn
        } else if (created != null && created.identities.isNullOrEmpty()) {
            // Supabase не говорит прямо, что почта занята: возвращает «успех» без письма и без identities.
            // Без этой проверки человек ждал бы письмо, которого не будет.
            error("already registered")
        } else {
            // App увидит это и покажет экран «Ожидаем подтверждение письма»
            _pending.value = PendingConfirmation(email.trim(), password)
            SignUpResult.ConfirmEmail
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = safe {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Закрыть экран ожидания письма (например, «Изменить почту»). */
    fun cancelPending() {
        _pending.value = null
    }

    suspend fun resendConfirmation(email: String): Result<Unit> = safe {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    /**
     * Подтверждение регистрации 8-значным кодом из письма.
     * При успехе Supabase создаёт сессию, [state] становится SignedIn, а [pending] сбрасывается в publishUser().
     */
    suspend fun verifyEmailCode(email: String, code: String): Result<Unit> = safe {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.SIGNUP,
            email = email.trim(),
            token = code.filter { it.isDigit() },
        )
    }

    /** Фото текущего пользователя по метке версии из [Profile.avatar]; null, если не удалось скачать. */
    suspend fun loadAvatar(version: String): ByteArray? {
        val uid = client.auth.currentUserOrNull()?.id ?: return null
        val key = "$uid/$version"
        avatarCache[key]?.let { return it }
        val bytes = runCatching {
            client.storage.from(AVATAR_BUCKET).downloadPublic("$uid/avatar.jpg")
        }.getOrNull() ?: return null
        avatarCache[key] = bytes
        return bytes
    }

    /** Сохраняет онбординг; при успехе профиль помечается заполненным и App закрывает экран. */
    suspend fun saveOnboarding(
        username: String,
        avatar: AvatarUpdate,
        syncPalette: Boolean,
        services: Set<String>,
    ): Result<Unit> = safe {
        val user = client.auth.currentUserOrNull() ?: error("Нет активной сессии")
        val current = (_state.value as? AuthState.SignedIn)?.profile?.avatar
        val path = "${user.id}/avatar.jpg"

        // Новая метка версии нужна, чтобы устройства не показывали старое фото из кэша
        val marker: String? = when (avatar) {
            is AvatarUpdate.Keep -> current
            is AvatarUpdate.Remove -> null
            is AvatarUpdate.Upload -> "u" + Random.nextInt(0, Int.MAX_VALUE).toString(36)
        }

        if (avatar is AvatarUpdate.Upload) {
            client.storage.from(AVATAR_BUCKET).upload(path, avatar.jpeg) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            avatarCache["${user.id}/$marker"] = avatar.jpeg
        } else if (avatar is AvatarUpdate.Remove && current != null) {
            runCatching { client.storage.from(AVATAR_BUCKET).delete(path) }
        }

        val list = services.toList()
        // Профиль в `account` создаётся только здесь, на сервере, по нажатию «Готово»
        client.postgrest.rpc(
            "complete_onboarding",
            Json.encodeToJsonElement(
                OnboardingParams.serializer(),
                OnboardingParams(username, marker, syncPalette, list),
            ).jsonObject,
        )

        val profile = Profile(username, marker, syncPalette, list, onboarded = true)
        (_state.value as? AuthState.SignedIn)?.takeIf { it.userId == user.id }?.let {
            _state.value = it.copy(username = username, profile = profile)
        }
    }

    /** Открывает браузер; результат вернётся через диплинк (Android) или локальный сервер (Desktop). */
    suspend fun signInWithGoogle(): Result<Unit> = safe { client.auth.signInWith(Google) }

    /** Шаг 1 сброса пароля по коду: отправляет на почту письмо с кодом (без ссылки-редиректа). */
    suspend fun sendResetCode(email: String): Result<Unit> = safe {
        client.auth.resetPasswordForEmail(email.trim())
    }

    /**
     * Шаг 2: проверяет код из письма и сразу задаёт новый пароль.
     * После проверки кода Supabase создаёт сессию, поэтому пароль меняется в том же вызове,
     * а пользователь после успеха оказывается вошедшим.
     */
    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): Result<Unit> = safe {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = email.trim(),
            token = code.filter { it.isDigit() },
        )
        client.auth.updateUser { password = newPassword }
    }

    /** Смена пароля из настроек для уже вошедшего пользователя. */
    suspend fun changePassword(newPassword: String): Result<Unit> = safe {
        client.auth.updateUser { password = newPassword }
    }

    suspend fun resetPassword(email: String): Result<Unit> = safe {
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_REDIRECT)
    }

    suspend fun signOut(): Result<Unit> = safe {
        _pending.value = null
        client.auth.signOut()
    }

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
            "Email not confirmed" in m -> "Сначала подтверди почту кодом из письма"
            "Token has expired" in m || "otp_expired" in m || "invalid" in m.lowercase() && "token" in m.lowercase() ->
                "Неверный или просроченный код"
            "duplicate key" in m || "23505" in m -> "Этот ник уже занят"
            "exceeded the maximum allowed size" in m || "too large" in m.lowercase() -> "Фото слишком большое"
            "rate limit" in m.lowercase() -> "Слишком много попыток, подожди немного"
            "Unable to resolve" in m || "timeout" in m.lowercase() ||
                    "UnknownHost" in e::class.simpleName.orEmpty() -> "Нет сети"
            else -> "Не получилось: ${m.take(120)}"
        }
    }
}