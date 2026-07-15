package com.securechat.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the device's master AES-GCM secret key inside the Android Keystore.
 *
 * The key NEVER leaves the hardware security module (TEE / StrongBox).
 * All encryption/decryption happens in-place within the secure enclave.
 *
 * Usage:
 * ```
 * val payload = keystoreManager.encryptData("hello".toByteArray())
 * val plain   = keystoreManager.decryptData(payload.cipherText, payload.iv)
 * ```
 */
class KeystoreManager {

    companion object {
        private const val KEY_PROVIDER    = "AndroidKeyStore"
        private const val KEY_ALIAS       = "E2EE_MasterSecretKey_v2"
        private const val TRANSFORMATION  = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT  = 128
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }
    }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) generateMasterKey()
    }

    // ── Key Generation ────────────────────────────────────────────────────────

    private fun generateMasterKey() {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Require biometric or device credential to use the key.
            // Set to true in production builds with biometric prompt flow.
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            // Invalidate on new biometric enrollment for extra security.
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private fun getSecretKey(): SecretKey =
        (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

    // ── Crypto Operations ─────────────────────────────────────────────────────

    /**
     * Encrypts [plainText] with a newly generated random IV each call.
     * Returns the ciphertext + IV bundle.
     */
    fun encryptData(plainText: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        return EncryptedPayload(cipher.doFinal(plainText), cipher.iv)
    }

    /**
     * Decrypts [cipherText] using the provided [iv].
     */
    fun decryptData(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return cipher.doFinal(cipherText)
    }

    // ── Key Rotation ──────────────────────────────────────────────────────────

    /**
     * Rotates the master key. Any data encrypted under the old key must be
     * re-encrypted before rotation. In practice, call this periodically or when
     * a biometric enrollment change is detected.
     *
     * Caller is responsible for re-encrypting any cached session data.
     */
    fun rotateKey() {
        keyStore.deleteEntry(KEY_ALIAS)
        generateMasterKey()
    }

    /**
     * Convenience: wraps a raw ECDH shared secret so it can be stored safely.
     */
    fun wrapSessionKey(sharedSecret: ByteArray): EncryptedPayload =
        encryptData(sharedSecret)

    fun unwrapSessionKey(wrapped: EncryptedPayload): ByteArray =
        decryptData(wrapped.cipherText, wrapped.iv)
}

data class EncryptedPayload(
    val cipherText: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return cipherText.contentEquals(other.cipherText) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int = 31 * cipherText.contentHashCode() + iv.contentHashCode()
}
