package com.remodex.mobile.ui.parity

import com.remodex.mobile.model.CODEX_PAIRING_QR_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScannerPairingValidatorTest {
    @Test
    fun versionMismatchRequiresBridgeUpdate() {
        val result = validatePairingQrCode(
            code = pairingQrCode(
                version = CODEX_PAIRING_QR_VERSION + 1,
                expiresAt = 1_900_000_000_000L
            )
        )

        val update = result as? QrScannerPairingValidationResult.BridgeUpdateRequired
            ?: error("Expected bridge update result")

        assertEquals("Update Remodex on your Mac before scanning", update.prompt.title)
        assertEquals("npm install -g remodex@latest", update.prompt.command)
        assertTrue(update.prompt.message.contains("different Remodex npm version"))
    }

    @Test
    fun legacyPayloadRequiresBridgeUpdate() {
        val result = validatePairingQrCode(
            code = """{"relay":"wss://relay.example","sessionId":"session-123"}"""
        )

        val update = result as? QrScannerPairingValidationResult.BridgeUpdateRequired
            ?: error("Expected bridge update result")

        assertEquals("npm install -g remodex@latest", update.prompt.command)
        assertTrue(update.prompt.message.contains("older Remodex bridge"))
    }

    @Test
    fun validPayloadReturnsSuccess() {
        val result = validatePairingQrCode(
            code = pairingQrCode(
                version = CODEX_PAIRING_QR_VERSION,
                expiresAt = 1_900_000_000_000L
            ),
            nowMillis = 1_800_000_000_000L
        )

        val success = result as? QrScannerPairingValidationResult.Success
            ?: error("Expected success")

        assertEquals("session-123", success.payload.sessionId)
        assertEquals("wss://relay.example", success.payload.relayUrl)
    }

    @Test
    fun validPayloadWithWhitespaceStillSucceeds() {
        val result = validatePairingQrCode(
            code = " \n\t${pairingQrCode(version = CODEX_PAIRING_QR_VERSION, expiresAt = 1_900_000_000_000L)}\n ",
            nowMillis = 1_800_000_000_000L
        )

        val success = result as? QrScannerPairingValidationResult.Success
            ?: error("Expected success")

        assertEquals("session-123", success.payload.sessionId)
    }

    @Test
    fun expiredPayloadReturnsScanError() {
        val result = validatePairingQrCode(
            code = pairingQrCode(
                version = CODEX_PAIRING_QR_VERSION,
                expiresAt = 1_700_000_000_000L
            ),
            nowMillis = 1_800_000_000_000L
        )

        val error = result as? QrScannerPairingValidationResult.ScanError
            ?: error("Expected scan error")

        assertEquals(
            "This pairing QR code has expired. Generate a new one from the Mac bridge.",
            error.message
        )
    }

    private fun pairingQrCode(version: Int, expiresAt: Long): String {
        return """
            {"v":$version,"relay":"wss://relay.example","sessionId":"session-123","macDeviceId":"mac-123","macIdentityPublicKey":"pub-key","expiresAt":$expiresAt}
        """.trimIndent()
    }
}
