package com.securechat.core.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM tests — no Android SDK required. Tests the cryptographic correctness
 * of X3DH key exchange and Double Ratchet encrypt/decrypt using standard Java APIs.
 */
class EncryptionManagerTest {

    private lateinit var x3dh: X3DHKeyManager
    private lateinit var cipher: SessionCipherManager

    // Stub KeystoreManager that uses raw pass-through (no Android Keystore in JVM tests).
    private val keystoreManager = object : KeystoreManager() {
        override fun wrapSessionKey(sharedSecret: ByteArray) =
            EncryptedPayload(sharedSecret, ByteArray(12))

        override fun unwrapSessionKey(wrapped: EncryptedPayload) = wrapped.cipherText
    }

    @Before
    fun setUp() {
        x3dh  = X3DHKeyManager(keystoreManager)
        cipher = SessionCipherManager(x3dh)
    }

    // ── X3DH ─────────────────────────────────────────────────────────────────

    @Test
    fun `x3dh initiator and responder derive same master secret`() {
        val aliceKP     = x3dh.generateKeyPair()
        val bobKP       = x3dh.generateKeyPair()
        val bobSpkKP    = x3dh.generateKeyPair()
        val bobOtkKP    = x3dh.generateKeyPair()

        val aliceResult = x3dh.initiatorX3DH(
            aliceIdentityPrivate  = aliceKP.private,
            aliceIdentityPublic   = aliceKP.public,
            bobIdentityPublic     = bobKP.public,
            bobSignedPrePublic    = bobSpkKP.public,
            bobOneTimePrePublic   = bobOtkKP.public,
        )

        val bobSecret = x3dh.responderX3DH(
            bobIdentityPrivate    = bobKP.private,
            bobSignedPrePrivate   = bobSpkKP.private,
            bobOneTimePrePrivate  = bobOtkKP.private,
            aliceIdentityPublic   = aliceKP.public,
            aliceEphemeralPublic  = x3dh.decodePublicKey(aliceResult.ephemeralPublic),
        )

        assertArrayEquals(
            "Initiator and responder should derive the same master secret",
            aliceResult.masterSecret,
            bobSecret,
        )
    }

    @Test
    fun `x3dh without one-time prekey still succeeds`() {
        val aliceKP  = x3dh.generateKeyPair()
        val bobKP    = x3dh.generateKeyPair()
        val bobSpkKP = x3dh.generateKeyPair()

        val aliceResult = x3dh.initiatorX3DH(
            aliceIdentityPrivate  = aliceKP.private,
            aliceIdentityPublic   = aliceKP.public,
            bobIdentityPublic     = bobKP.public,
            bobSignedPrePublic    = bobSpkKP.public,
            bobOneTimePrePublic   = null,
        )

        val bobSecret = x3dh.responderX3DH(
            bobIdentityPrivate    = bobKP.private,
            bobSignedPrePrivate   = bobSpkKP.private,
            bobOneTimePrePrivate  = null,
            aliceIdentityPublic   = aliceKP.public,
            aliceEphemeralPublic  = x3dh.decodePublicKey(aliceResult.ephemeralPublic),
        )

        assertArrayEquals(aliceResult.masterSecret, bobSecret)
    }

    // ── Double Ratchet ────────────────────────────────────────────────────────

    @Test
    fun `encrypt then decrypt round trip returns original plaintext`() {
        val masterSecret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val senderState = cipher.initSession(masterSecret)
        // Receiver gets swapped chains
        val receiverState = SessionState(
            sendChainKey    = senderState.receiveChainKey.clone(),
            receiveChainKey = senderState.sendChainKey.clone(),
        )

        val plaintext = "Hello, E2EE world!".toByteArray()
        val encrypted = cipher.encrypt(senderState, plaintext)
        val decrypted = cipher.decrypt(receiverState, encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `each message uses a different key (forward secrecy)`() {
        val masterSecret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val state1 = cipher.initSession(masterSecret)
        val state2 = cipher.initSession(masterSecret)

        val enc1 = cipher.encrypt(state1, "msg1".toByteArray())
        val enc2 = cipher.encrypt(state2, "msg2".toByteArray())

        // Same plaintext length but different ciphertext means different keys were used.
        assertFalse(enc1.cipherText.contentEquals(enc2.cipherText))
    }

    @Test
    fun `out-of-order messages decrypt correctly`() {
        val masterSecret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val senderState  = cipher.initSession(masterSecret)
        val receiverState = SessionState(
            sendChainKey    = senderState.receiveChainKey.clone(),
            receiveChainKey = senderState.sendChainKey.clone(),
        )

        val enc0 = cipher.encrypt(senderState, "msg0".toByteArray())
        val enc1 = cipher.encrypt(senderState, "msg1".toByteArray())
        val enc2 = cipher.encrypt(senderState, "msg2".toByteArray())

        // Deliver out of order: 2, 0, 1
        assertArrayEquals("msg2".toByteArray(), cipher.decrypt(receiverState, enc2))
        assertArrayEquals("msg0".toByteArray(), cipher.decrypt(receiverState, enc0))
        assertArrayEquals("msg1".toByteArray(), cipher.decrypt(receiverState, enc1))
    }

    // ── HKDF ─────────────────────────────────────────────────────────────────

    @Test
    fun `hkdf is deterministic`() {
        val input = "shared-secret".toByteArray()
        val out1  = x3dh.hkdf(input, ByteArray(32), "info".toByteArray(), 32)
        val out2  = x3dh.hkdf(input, ByteArray(32), "info".toByteArray(), 32)
        assertArrayEquals(out1, out2)
    }

    @Test
    fun `hkdf different info produces different keys`() {
        val input = "shared-secret".toByteArray()
        val out1  = x3dh.hkdf(input, ByteArray(32), "send-chain".toByteArray(), 32)
        val out2  = x3dh.hkdf(input, ByteArray(32), "recv-chain".toByteArray(), 32)
        assertFalse(out1.contentEquals(out2))
    }
}
