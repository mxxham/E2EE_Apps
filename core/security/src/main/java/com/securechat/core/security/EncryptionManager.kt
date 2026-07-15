package com.securechat.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * **Double Ratchet** session cipher.
 *
 * After X3DH establishes a shared master secret, the Double Ratchet derives
 * fresh symmetric sending/receiving chain keys for every message, providing:
 * - **Perfect Forward Secrecy**: compromising the current key doesn't expose past messages.
 * - **Break-in Recovery**: new keys are derived from fresh DH ratchet steps.
 *
 * This implementation uses:
 * - AES-GCM (256-bit) for message encryption.
 * - HKDF (HMAC-SHA256) for all key derivation steps.
 *
 * State machine: [SessionState] is mutable and should be serialized (encrypted
 * with [KeystoreManager.wrapSessionKey]) and stored per-conversation.
 */
class SessionCipherManager(private val x3dh: X3DHKeyManager) {

    companion object {
        private const val AES_ALGO       = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LEN_BIT    = 128
        private val MESSAGE_KEY_CONSTANT = byteArrayOf(0x01)
        private val CHAIN_KEY_CONSTANT   = byteArrayOf(0x02)
    }

    private val random = SecureRandom()

    // ── Symmetric Ratchet ─────────────────────────────────────────────────────

    /**
     * Derives the next message key and advances the sending chain key.
     * Called once per outgoing message.
     */
    fun ratchetSendKey(state: SessionState): ByteArray {
        val messageKey = x3dh.hkdf(
            input  = state.sendChainKey,
            salt   = ByteArray(32),
            info   = MESSAGE_KEY_CONSTANT,
            length = 32,
        )
        // Advance chain key
        state.sendChainKey = x3dh.hkdf(
            input  = state.sendChainKey,
            salt   = ByteArray(32),
            info   = CHAIN_KEY_CONSTANT,
            length = 32,
        )
        state.sendMessageIndex++
        return messageKey
    }

    /**
     * Derives the receive message key for incoming message at [messageIndex].
     * Skipped-message keys are stored in [state.skippedKeys] to handle
     * out-of-order delivery.
     */
    fun ratchetReceiveKey(state: SessionState, messageIndex: Int): ByteArray {
        // If we already cached this key (out-of-order message), return it.
        state.skippedKeys[messageIndex]?.let { return it }

        // Advance chain until we reach the requested index.
        while (state.receiveMessageIndex <= messageIndex) {
            val mk = x3dh.hkdf(
                input = state.receiveChainKey,
                salt  = ByteArray(32),
                info  = MESSAGE_KEY_CONSTANT,
                length = 32,
            )
            if (state.receiveMessageIndex == messageIndex) {
                state.receiveChainKey = x3dh.hkdf(
                    input = state.receiveChainKey,
                    salt  = ByteArray(32),
                    info  = CHAIN_KEY_CONSTANT,
                    length = 32,
                )
                state.receiveMessageIndex++
                return mk
            }
            // Cache skipped key for potential out-of-order delivery.
            state.skippedKeys[state.receiveMessageIndex] = mk
            state.receiveChainKey = x3dh.hkdf(
                input = state.receiveChainKey,
                salt  = ByteArray(32),
                info  = CHAIN_KEY_CONSTANT,
                length = 32,
            )
            state.receiveMessageIndex++
        }
        error("Cannot derive key for index $messageIndex; current index ${state.receiveMessageIndex}")
    }

    // ── Message Encryption / Decryption ───────────────────────────────────────

    /**
     * Encrypts [plaintext] for a session, advancing the send ratchet.
     * Returns an [EncryptedMessage] wire frame ready to be JSON-serialized.
     */
    fun encrypt(state: SessionState, plaintext: ByteArray): EncryptedMessage {
        val messageKey  = ratchetSendKey(state)
        val iv          = ByteArray(12).also { random.nextBytes(it) }
        val secretKey   = SecretKeySpec(messageKey, AES_ALGO)
        val cipher      = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BIT, iv))
        val cipherText  = cipher.doFinal(plaintext)

        return EncryptedMessage(
            messageIndex = state.sendMessageIndex - 1,
            iv           = iv,
            cipherText   = cipherText,
        )
    }

    /**
     * Decrypts an incoming [frame], advancing (or looking up) the receive ratchet.
     * Handles out-of-order messages by caching skipped keys.
     */
    fun decrypt(state: SessionState, frame: EncryptedMessage): ByteArray {
        val messageKey = ratchetReceiveKey(state, frame.messageIndex)
        val secretKey  = SecretKeySpec(messageKey, AES_ALGO)
        val cipher     = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BIT, frame.iv))
        return cipher.doFinal(frame.cipherText)
    }

    /** Initializes a brand-new session from a shared master secret (output of X3DH). */
    fun initSession(masterSecret: ByteArray): SessionState {
        // Derive initial send and receive chain keys from the master secret.
        val sendChain = x3dh.hkdf(masterSecret, ByteArray(32), "send-chain".toByteArray(), 32)
        val recvChain = x3dh.hkdf(masterSecret, ByteArray(32), "recv-chain".toByteArray(), 32)
        return SessionState(sendChainKey = sendChain, receiveChainKey = recvChain)
    }
}

// ── Session State ─────────────────────────────────────────────────────────────

/**
 * Mutable ratchet state for one conversation session.
 * Must be encrypted and persisted (e.g., via [KeystoreManager.wrapSessionKey])
 * between app launches.
 */
data class SessionState(
    var sendChainKey: ByteArray,
    var receiveChainKey: ByteArray,
    var sendMessageIndex: Int = 0,
    var receiveMessageIndex: Int = 0,
    /** Cached message keys for out-of-order messages; index → messageKey. */
    val skippedKeys: MutableMap<Int, ByteArray> = mutableMapOf(),
)

/** Wire format for a single encrypted message frame. */
data class EncryptedMessage(
    val messageIndex: Int,
    val iv: ByteArray,
    val cipherText: ByteArray,
)
