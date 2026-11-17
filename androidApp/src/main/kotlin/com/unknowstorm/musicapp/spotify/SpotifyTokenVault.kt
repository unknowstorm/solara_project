package com.unknowstorm.musicapp.spotify

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps ONE value on this device: the user's Spotify refresh token, encrypted with a key that never leaves the Android Keystore.
 * It is never sent to our server. If it cannot be decrypted (for example after a backup restore), it is simply dropped.
 */
internal class SpotifyTokenVault(
    private val prefs: SharedPreferences,
    private val prefKey: String = "lib_refresh",
    private val alias: String = "solara_spotify_refresh_key",
) {
    private val flags = Base64.NO_WRAP

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun has(): Boolean = prefs.contains(prefKey)

    fun save(token: String) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(prefKey, Base64.encodeToString(cipher.iv, flags) + ":" + Base64.encodeToString(data, flags))
                .apply()
        }.onFailure { clear() }
    }

    fun load(): String? {
        val stored = prefs.getString(prefKey, null) ?: return null
        return runCatching {
            val (iv, data) = stored.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, flags)))
            String(cipher.doFinal(Base64.decode(data, flags)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(prefKey).apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }
}
