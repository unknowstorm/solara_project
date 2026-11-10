package com.unknowstorm.musicapp.auth

import com.unknowstorm.musicapp.i18n.S
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Profile data from the `account` table.
 * [avatar] is null (we show the first letter of the nickname) or a version tag of the uploaded photo like "u1a2b3c":
 * the file itself lies in the `avatars` bucket at the path `<user_id>/avatar.jpg`.
 */
data class Profile(
    val username: String? = null,
    val avatar: String? = null,
    val syncPalette: Boolean = false,
    val services: List<String> = emptyList(),
    val onboarded: Boolean = false,
)

/** What to do with the avatar when saving the profile. */
sealed interface AvatarUpdate {
    /** Leave as is. */
    data object Keep : AvatarUpdate

    /** Remove the photo: the nickname letter returns. */
    data object Remove : AvatarUpdate

    /** Upload a new photo (JPEG, already cropped to a square). */
    class Upload(val jpeg: ByteArray) : AvatarUpdate
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Guest : AuthState
    data class SignedIn(
        val userId: String,
        val email: String?,
        val username: String?,
        /** null: the profile has not loaded yet (or failed to load). */
        val profile: Profile? = null,
    ) : AuthState
    data class Error(val message: String) : AuthState
}

enum class SignUpResult { SignedIn, ConfirmEmail }

/** Registration was sent, waiting for email confirmation. The password lives only in memory. */
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

/** Parameters of the server function `complete_onboarding`. */
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

    /** Downloaded avatars: the key is "<user_id>/<version tag>". */
    private val avatarCache = mutableMapOf<String, ByteArray>()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Initializing -> _state.value = AuthState.Loading
                    is SessionStatus.Authenticated -> publishUser()
                    is SessionStatus.NotAuthenticated -> _state.value = AuthState.Guest
                    // no network while refreshing the token: the session is saved, the user stays "signed in"
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
        // If this is the same user (e.g. the token was refreshed), we do not reset the already loaded profile,
        // otherwise the onboarding screen would blink and lose what was entered
        val prev = (_state.value as? AuthState.SignedIn)?.takeIf { it.userId == user.id }
        // A copy of the nickname lives in the session on the device, so it is visible without a network
        val cached = user.userMetadata?.get("username")?.jsonPrimitive?.contentOrNull
        _state.value = prev ?: AuthState.SignedIn(user.id, user.email, cached)
        val profile = runCatching { loadProfile(user.id) }.getOrNull()
        (_state.value as? AuthState.SignedIn)?.takeIf { it.userId == user.id }?.let {
            _state.value = it.copy(username = profile?.username ?: it.username, profile = profile ?: it.profile)
        }
        val fresh = profile?.username?.takeIf { it.isNotBlank() }
        if (fresh != null && fresh != cached) cacheUsername(fresh)
    }

    /** Writes the nickname to the session metadata (only for display offline; the source of truth stays the database). */
    private suspend fun cacheUsername(name: String) {
        runCatching { client.auth.updateUser { data = buildJsonObject { put("username", name) } } }
    }

    private suspend fun loadProfile(userId: String): Profile {
        val row = client.from("account")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<AccountRow>()
        // The row in `account` appears only after "Done" in onboarding.
        // No row: the email is confirmed but the profile is not filled in yet, so we show onboarding
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
            // Supabase does not say directly that the email is taken: it returns "success" without a letter and without identities.
            // Without this check a person would wait for a letter that will never come.
            error("already registered")
        } else {
            // App will see this and show the "Waiting for email confirmation" screen
            _pending.value = PendingConfirmation(email.trim(), password)
            SignUpResult.ConfirmEmail
        }
    }

    /** Sign-in by email or by login (nickname): the login is first turned into an email via the RPC `email_by_username`. */
    suspend fun signIn(login: String, password: String): Result<Unit> = safe {
        val mail = resolveEmailOrNull(login) ?: error("Invalid login credentials")
        client.auth.signInWith(Email) {
            this.email = mail
            this.password = password
        }
    }

    /** If [login] contains '@', it is an email. Otherwise we look up the email by nickname on the server; null if there is no such nickname. */
    private suspend fun resolveEmailOrNull(login: String): String? {
        val v = login.trim()
        if ('@' in v) return v
        if (v.isEmpty()) return null
        val res = client.postgrest.rpc(
            "email_by_username",
            buildJsonObject { put("p_username", v) },
        )
        return Json.parseToJsonElement(res.data).jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /**
     * Password reset by email or login: finds the email, sends a code to it and returns this email.
     * It must be passed on to [resetPasswordWithCode] and [sendResetCode].
     */
    suspend fun startPasswordReset(login: String): Result<String> = safe {
        val mail = resolveEmailOrNull(login) ?: error("login not found")
        client.auth.resetPasswordForEmail(mail)
        mail
    }

    /** Close the waiting-for-email screen (e.g. "Change email"). */
    fun cancelPending() {
        _pending.value = null
    }

    suspend fun resendConfirmation(email: String): Result<Unit> = safe {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    /**
     * Registration confirmation with the 8-digit code from the email.
     * On success Supabase creates a session, [state] becomes SignedIn, and [pending] is reset in publishUser().
     */
    suspend fun verifyEmailCode(email: String, code: String): Result<Unit> = safe {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.SIGNUP,
            email = email.trim(),
            token = code.filter { it.isDigit() },
        )
    }

    /** The current user's photo by the version tag from [Profile.avatar]; null if it could not be downloaded. */
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

    /** Saves onboarding; on success the profile is marked as filled and App closes the screen. */
    suspend fun saveOnboarding(
        username: String,
        avatar: AvatarUpdate,
        syncPalette: Boolean,
        services: Set<String>,
    ): Result<Unit> = safe {
        val user = client.auth.currentUserOrNull() ?: error(S.noActiveSession)
        val current = (_state.value as? AuthState.SignedIn)?.profile?.avatar
        val path = "${user.id}/avatar.jpg"

        // A new version tag is needed so that devices do not show the old photo from the cache
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
        // The profile in `account` is created only here, on the server, when "Done" is pressed
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
        cacheUsername(username)
    }

    /** Opens the browser; the result comes back via a deep link (Android) or a local server (Desktop). */
    suspend fun signInWithGoogle(): Result<Unit> = safe { client.auth.signInWith(Google) }

    /** Step 1 of the code password reset: sends an email with a code to the address (without a redirect link). */
    suspend fun sendResetCode(email: String): Result<Unit> = safe {
        client.auth.resetPasswordForEmail(email.trim())
    }

    /**
     * Step 2: checks the code from the email and sets the new password right away.
     * After the code is verified Supabase creates a session, so the password is changed in the same call,
     * and after success the user ends up signed in.
     */
    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): Result<Unit> = safe {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = email.trim(),
            token = code.filter { it.isDigit() },
        )
        client.auth.updateUser { password = newPassword }
    }

    /** Password change from settings for an already signed-in user. */
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
            "Invalid login credentials" in m -> S.wrongLoginOrPassword
            "login not found" in m -> S.noAccountWithThisLogin
            "already registered" in m || "already been registered" in m -> S.thisEmailIsAlreadyRegistered
            "Password should be" in m -> S.passwordIsTooSimpleAt
            "Email not confirmed" in m -> S.confirmYourEmailWithThe
            "Token has expired" in m || "otp_expired" in m || "invalid" in m.lowercase() && "token" in m.lowercase() ->
                S.wrongOrExpiredCode
            "duplicate key" in m || "23505" in m -> S.thisNicknameIsAlreadyTaken
            "exceeded the maximum allowed size" in m || "too large" in m.lowercase() -> S.thePhotoIsTooLarge
            "rate limit" in m.lowercase() -> S.tooManyAttemptsPleaseWait
            "Unable to resolve" in m || "timeout" in m.lowercase() ||
                    "UnknownHost" in e::class.simpleName.orEmpty() -> S.noNetwork
            else -> S.failed(m.take(120))
        }
    }
}
