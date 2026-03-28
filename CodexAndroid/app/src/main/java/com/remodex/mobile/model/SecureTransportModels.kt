package com.remodex.mobile.model

const val CODEX_SECURE_PROTOCOL_VERSION = 1
const val CODEX_SECURE_HANDSHAKE_TAG = "remodex-e2ee-v1"

enum class SecureHandshakeMode(val wireValue: String) {
    QR_BOOTSTRAP("qr_bootstrap"),
    TRUSTED_RECONNECT("trusted_reconnect")
}

data class SecureTranscriptInput(
    val sessionId: String,
    val protocolVersion: Int,
    val handshakeMode: String,
    val keyEpoch: Int,
    val macDeviceId: String,
    val phoneDeviceId: String,
    val macIdentityPublicKeyBase64: String,
    val phoneIdentityPublicKeyBase64: String,
    val macEphemeralPublicKeyBase64: String,
    val phoneEphemeralPublicKeyBase64: String,
    val clientNonceBase64: String,
    val serverNonceBase64: String,
    val expiresAtForTranscript: Long
)
