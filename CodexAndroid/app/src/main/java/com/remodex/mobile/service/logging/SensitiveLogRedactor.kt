package com.remodex.mobile.service.logging

import java.net.URI

object SensitiveLogRedactor {
    private val keyedSecretPattern = Regex(
        pattern = """(?i)\b(sessionId|session|token|secret|authorization|mac|phone|macDeviceId|phoneDeviceId|macIdentityPublicKey|phoneIdentityPublicKey|macKey|phoneKey|clientNonce|serverNonce|ciphertext|tag|signature|digest|transcript)\s*[:=]\s*([^\s,;|]+)"""
    )
    private val relayPathPattern = Regex("""(?i)(/relay/)([A-Za-z0-9._~-]{6,})""")
    private val longBase64Pattern = Regex("""\b[A-Za-z0-9+/]{32,}={0,2}\b""")
    private val longHexPattern = Regex("""\b[0-9a-fA-F]{16,}\b""")
    private val uuidPattern = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b""")
    private val urlPattern = Regex("""(?i)\b(wss?://[^\s]+)""")

    fun redact(raw: String): String {
        var sanitized = raw
        sanitized = keyedSecretPattern.replace(sanitized) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        sanitized = relayPathPattern.replace(sanitized) { match ->
            "${match.groupValues[1]}<redacted>"
        }
        sanitized = urlPattern.replace(sanitized) { match ->
            redactUrl(match.groupValues[1])
        }
        sanitized = uuidPattern.replace(sanitized, "<uuid:redacted>")
        sanitized = longBase64Pattern.replace(sanitized, "<base64:redacted>")
        sanitized = longHexPattern.replace(sanitized, "<hex:redacted>")
        return sanitized
    }

    private fun redactUrl(rawUrl: String): String {
        return runCatching {
            val uri = URI(rawUrl)
            val scheme = uri.scheme ?: return@runCatching rawUrl
            val host = uri.host ?: return@runCatching rawUrl
            val portSuffix = if (uri.port > 0) ":${uri.port}" else ""
            val normalizedPath = relayPathPattern.replace(uri.path.orEmpty()) { match ->
                "${match.groupValues[1]}<redacted>"
            }
            "$scheme://$host$portSuffix$normalizedPath"
        }.getOrDefault(rawUrl)
    }
}
