package com.remodex.mobile.service.secure

import com.remodex.mobile.model.SecureTranscriptInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class CodexSecureTranscriptTest {
    @Test
    fun transcriptMatchesGoldenVector() {
        val input = SecureTranscriptInput(
            sessionId = "session-golden-1",
            protocolVersion = 1,
            handshakeMode = "trusted_reconnect",
            keyEpoch = 7,
            macDeviceId = "mac-golden-1",
            phoneDeviceId = "phone-golden-1",
            macIdentityPublicKeyBase64 = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXktMQ==",
            phoneIdentityPublicKeyBase64 = "cGhvbmUtaWRlbnRpdHktcHVibGljLWtleS0x",
            macEphemeralPublicKeyBase64 = "bWFjLWVwaGVtZXJhbC1wdWJsaWMta2V5LTE=",
            phoneEphemeralPublicKeyBase64 = "cGhvbmUtZXBoZW1lcmFsLXB1YmxpYy1rZXktMQ==",
            clientNonceBase64 = "Y2xpZW50LW5vbmNlLTE=",
            serverNonceBase64 = "c2VydmVyLW5vbmNlLTE=",
            expiresAtForTranscript = 0
        )

        val transcript = CodexSecureTranscript.buildTranscriptBytes(input)
        assertEquals(
            "0000000f72656d6f6465782d653265652d76310000001073657373696f6e2d676f6c64656e2d31000000013100000011747275737465645f7265636f6e6e65637400000001370000000c6d61632d676f6c64656e2d310000000e70686f6e652d676f6c64656e2d31000000196d61632d6964656e746974792d7075626c69632d6b65792d310000001b70686f6e652d6964656e746974792d7075626c69632d6b65792d310000001a6d61632d657068656d6572616c2d7075626c69632d6b65792d310000001c70686f6e652d657068656d6572616c2d7075626c69632d6b65792d310000000e636c69656e742d6e6f6e63652d310000000e7365727665722d6e6f6e63652d310000000130",
            transcript.toHex()
        )
        assertEquals(
            "4bd63e61e7824d1f092f22bbe64493fa304ae848f8c0b2893d6bd1ca38b4ecfe",
            sha256Hex(transcript)
        )
    }

    @Test
    fun noncesMatchGoldenVectorForMacAndMobileSenders() {
        assertEquals("02000000000000000000002a", CodexSecureTranscript.nonceForSender("iphone", 42).toHex())
        assertEquals("02000000000000000000002a", CodexSecureTranscript.nonceForSender("android", 42).toHex())
        assertEquals("01000000000000000000002a", CodexSecureTranscript.nonceForSender("mac", 42).toHex())
    }

    private fun sha256Hex(value: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(value).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
}
