package com.securechat.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the local (device-side) halves of a user's X3DH key material.
 *
 * Public keys are uploaded to Supabase during registration (see [ChatApiService.register]
 * and [ChatApiService.uploadPreKeys] in :core:network); the WRAPPED private keys returned
 * by [X3DHKeyManager.generateRegistrationBundle] must stay on-device only. This class stores
 * them inside Android's [EncryptedSharedPreferences], itself backed by a Keystore-protected
 * master key (separate from the AES-GCM master key used by [KeystoreManager]).
 *
 * Values are stored Base64-encoded since [EncryptedPayload] holds raw byte arrays.
 */
class IdentityKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_identity_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Persists the full registration bundle's wrapped private keys for [userId]. */
    fun saveRegistrationBundle(userId: String, bundle: RegistrationBundle) {
        prefs.edit()
            .putString("$userId:identity", bundle.wrappedIdentityPrivate.toEncoded())
            .putString("$userId:signed", bundle.wrappedSignedPrePrivate.toEncoded())
            .putString(
                "$userId:onetime",
                bundle.wrappedOneTimePrivates.joinToString("|") { it.toEncoded() },
            )
            .apply()
    }

    /** True if this device already holds identity keys for [userId] (i.e. registered locally). */
    fun hasLocalIdentity(userId: String): Boolean =
        prefs.contains("$userId:identity")

    fun loadWrappedIdentityPrivate(userId: String): EncryptedPayload? =
        prefs.getString("$userId:identity", null)?.toEncryptedPayload()

    fun loadWrappedSignedPrePrivate(userId: String): EncryptedPayload? =
        prefs.getString("$userId:signed", null)?.toEncryptedPayload()

    fun loadWrappedOneTimePrivates(userId: String): List<EncryptedPayload> =
        prefs.getString("$userId:onetime", null)
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.map { it.toEncryptedPayload() }
            ?: emptyList()

    /** Wipes locally stored key material for [userId] — call on logout / account deletion. */
    fun clear(userId: String) {
        prefs.edit()
            .remove("$userId:identity")
            .remove("$userId:signed")
            .remove("$userId:onetime")
            .apply()
    }

    // ── Encoding helpers ──────────────────────────────────────────────────────

    private fun EncryptedPayload.toEncoded(): String =
        Base64.encodeToString(cipherText, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(iv, Base64.NO_WRAP)

    private fun String.toEncryptedPayload(): EncryptedPayload {
        val (cipherB64, ivB64) = split(":", limit = 2)
        return EncryptedPayload(
            cipherText = Base64.decode(cipherB64, Base64.NO_WRAP),
            iv         = Base64.decode(ivB64, Base64.NO_WRAP),
        )
    }
}
