package com.securechat.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model for a registered user.
 */
@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /** The user's ECDH public key (Base64-encoded), used for session establishment. */
    val publicKey: String = "",
    /** Whether the current session user is verified (key fingerprint compared). */
    val isVerified: Boolean = false,
    val registeredAt: Long = 0L,
)
