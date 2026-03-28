package com.remodex.mobile.service.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveLogRedactorTest {
    @Test
    fun redactHidesRelaySessionAndTokenFields() {
        val raw = "connect relay=wss://192.168.31.138/relay/fa8c830d sessionId=cd7f9d17 token=abc123xyz mac=67237f47-3a9b-42b6-9884-799cde38cee5"

        val redacted = SensitiveLogRedactor.redact(raw)

        assertTrue(redacted.contains("/relay/<redacted>"))
        assertTrue(redacted.contains("sessionId=<redacted>"))
        assertTrue(redacted.contains("token=<redacted>"))
        assertTrue(redacted.contains("mac=<redacted>"))
        assertFalse(redacted.contains("fa8c830d"))
        assertFalse(redacted.contains("abc123xyz"))
        assertFalse(redacted.contains("67237f47-3a9b-42b6-9884-799cde38cee5"))
    }

    @Test
    fun redactHidesLongMaterialLikeKeysAndDigests() {
        val raw = "phoneKey=8d32b78b0cf8abcdef1234567890abcd transcript=3c9815e949a01c9affffffffffffffff"

        val redacted = SensitiveLogRedactor.redact(raw)

        assertTrue(redacted.contains("phoneKey=<redacted>"))
        assertTrue(redacted.contains("transcript=<redacted>"))
        assertFalse(redacted.contains("8d32b78b0cf8abcdef1234567890abcd"))
    }
}
