package com.unknowstorm.musicapp.imports

import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.auth.supabase
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.put

/** Sources from which favorites and playlists can be imported. */
val IMPORT_PROVIDERS = listOf(ProviderId.JAMENDO, ProviderId.AUDIUS)

data class LinkedAccount(val provider: ProviderId, val externalId: String, val displayName: String?)
data class ImportSummary(val likesAdded: Int, val playlistsAdded: Int, val account: String)

@Serializable
private data class LinkedRow(
    val provider: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
private data class ImportReply(
    val likesAdded: Int = 0,
    val playlistsAdded: Int = 0,
    val account: String = "",
    val error: String? = null,
)

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * One-way import from Jamendo / Audius: the Edge Function `import-account` reads the PUBLIC profile
 * and writes into our tables. No service tokens are requested or stored.
 */
class ImportRepository(
    auth: AuthRepository,
    private val likes: LikesRepository,
    private val playlists: PlaylistsRepository,
    private val client: SupabaseClient = supabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var userId: String? = null

    private val _linked = MutableStateFlow<Map<ProviderId, LinkedAccount>>(emptyMap())
    val linked: StateFlow<Map<ProviderId, LinkedAccount>> = _linked.asStateFlow()

    private val _busy = MutableStateFlow<ProviderId?>(null)
    val busy: StateFlow<ProviderId?> = _busy.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init {
        scope.launch {
            auth.state.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> if (userId != s.userId) {
                        userId = s.userId
                        _linked.value = emptyMap()
                        _signedIn.value = true
                        refresh()
                    }
                    is AuthState.Guest -> {
                        userId = null
                        _signedIn.value = false
                        _linked.value = emptyMap()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        val owner = userId ?: return
        scope.launch {
            try {
                val rows = client.from("linked_accounts").select().decodeList<LinkedRow>()
                if (userId == owner) {
                    _linked.value = rows.mapNotNull { r ->
                        ProviderId.entries.firstOrNull { it.name == r.provider }
                            ?.let { it to LinkedAccount(it, r.externalId, r.displayName) }
                    }.toMap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Solara: linked accounts refresh failed: ${e.message}")
            }
        }
    }

    /** [account]: nickname / link / id; null = take the saved one (re-import). */
    suspend fun import(provider: ProviderId, account: String?): Result<ImportSummary> {
        if (provider !in IMPORT_PROVIDERS) return Result.failure(Exception(S.importFailed))
        if (_busy.value != null) return Result.failure(Exception(S.importing))
        _busy.value = provider
        return try {
            val resp = client.functions.invoke(
                function = "import-account",
                body = buildJsonObject {
                    put("provider", provider.name)
                    if (!account.isNullOrBlank()) put("account", account.trim())
                },
            )
            val reply = lenientJson.decodeFromString<ImportReply>(resp.bodyAsText())
            if (reply.error != null) {
                Result.failure(Exception(if (reply.error == "not_found") S.accountNotFound else S.importFailed))
            } else {
                likes.refresh()
                playlists.refresh()
                refresh()
                Result.success(ImportSummary(reply.likesAdded, reply.playlistsAdded, reply.account))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: import failed: ${e.message}")
            // A non-2xx reply may arrive as an exception: look for the code in its text
            val notFound = e.message?.contains("not_found") == true
            Result.failure(Exception(if (notFound) S.accountNotFound else S.importFailed))
        } finally {
            _busy.value = null
        }
    }

    /** Unlinks the account; optionally removes everything that was imported from it. */
    suspend fun disconnect(provider: ProviderId, deleteImported: Boolean): Result<Unit> = try {
        if (deleteImported) {
            client.from("likes").delete { filter { eq("imported_from", provider.name) } }
            client.from("playlists").delete { filter { eq("imported_from", provider.name) } }
        }
        client.from("linked_accounts").delete { filter { eq("provider", provider.name) } }
        _linked.value = _linked.value - provider
        if (deleteImported) {
            likes.refresh()
            playlists.refresh()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: disconnect failed: ${e.message}")
        Result.failure(Exception(S.importFailed))
    }
}
