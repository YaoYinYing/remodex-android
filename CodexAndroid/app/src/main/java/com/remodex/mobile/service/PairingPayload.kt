package com.remodex.mobile.service

data class PairingPayload(
    val sessionId: String,
    val relayUrl: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val expiresAt: Long
)
