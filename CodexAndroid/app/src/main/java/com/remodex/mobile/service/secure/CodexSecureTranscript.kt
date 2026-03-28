package com.remodex.mobile.service.secure

import com.remodex.mobile.model.CODEX_SECURE_HANDSHAKE_TAG
import com.remodex.mobile.model.SecureTranscriptInput
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

object CodexSecureTranscript {
    fun buildTranscriptBytes(input: SecureTranscriptInput): ByteArray {
        return concat(
            encodeLengthPrefixedUtf8(CODEX_SECURE_HANDSHAKE_TAG),
            encodeLengthPrefixedUtf8(input.sessionId),
            encodeLengthPrefixedUtf8(input.protocolVersion.toString()),
            encodeLengthPrefixedUtf8(input.handshakeMode),
            encodeLengthPrefixedUtf8(input.keyEpoch.toString()),
            encodeLengthPrefixedUtf8(input.macDeviceId),
            encodeLengthPrefixedUtf8(input.phoneDeviceId),
            encodeLengthPrefixedBytes(base64Decode(input.macIdentityPublicKeyBase64)),
            encodeLengthPrefixedBytes(base64Decode(input.phoneIdentityPublicKeyBase64)),
            encodeLengthPrefixedBytes(base64Decode(input.macEphemeralPublicKeyBase64)),
            encodeLengthPrefixedBytes(base64Decode(input.phoneEphemeralPublicKeyBase64)),
            encodeLengthPrefixedBytes(base64Decode(input.clientNonceBase64)),
            encodeLengthPrefixedBytes(base64Decode(input.serverNonceBase64)),
            encodeLengthPrefixedUtf8(input.expiresAtForTranscript.toString())
        )
    }

    fun nonceForSender(sender: String, counter: Long): ByteArray {
        require(counter >= 0) { "Counter must be non-negative." }
        val nonce = ByteArray(12)
        nonce[0] = if (sender == "mac") 1 else 2
        var value = counter
        for (index in 11 downTo 1) {
            nonce[index] = (value and 0xff).toByte()
            value = value ushr 8
        }
        return nonce
    }

    private fun encodeLengthPrefixedUtf8(value: String): ByteArray {
        return encodeLengthPrefixedBytes(value.toByteArray(Charsets.UTF_8))
    }

    private fun encodeLengthPrefixedBytes(value: ByteArray): ByteArray {
        val lengthPrefix = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(value.size)
            .array()
        return concat(lengthPrefix, value)
    }

    private fun base64Decode(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }

    private fun concat(vararg chunks: ByteArray): ByteArray {
        val totalSize = chunks.sumOf { it.size }
        val output = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(output, offset)
            offset += chunk.size
        }
        return output
    }
}
