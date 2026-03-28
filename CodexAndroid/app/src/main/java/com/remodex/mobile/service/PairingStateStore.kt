package com.remodex.mobile.service

import android.content.Context

interface PairingStateStore {
    fun load(): PairingPayload?
    fun save(payload: PairingPayload)
    fun clear()
}

class SharedPreferencesPairingStateStore(
    context: Context
) : PairingStateStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): PairingPayload? {
        val sessionId = prefs.getString(KEY_SESSION_ID, null)?.trim().orEmpty()
        val relayUrl = prefs.getString(KEY_RELAY_URL, null)?.trim().orEmpty()
        val macDeviceId = prefs.getString(KEY_MAC_DEVICE_ID, null)?.trim().orEmpty()
        val macIdentityPublicKey = prefs.getString(KEY_MAC_IDENTITY_PUBLIC_KEY, null)?.trim().orEmpty()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (
            sessionId.isEmpty() ||
            relayUrl.isEmpty() ||
            macDeviceId.isEmpty() ||
            macIdentityPublicKey.isEmpty() ||
            expiresAt <= System.currentTimeMillis()
        ) {
            clear()
            return null
        }

        return PairingPayload(
            sessionId = sessionId,
            relayUrl = relayUrl,
            macDeviceId = macDeviceId,
            macIdentityPublicKey = macIdentityPublicKey,
            expiresAt = expiresAt
        )
    }

    override fun save(payload: PairingPayload) {
        prefs.edit()
            .putString(KEY_SESSION_ID, payload.sessionId)
            .putString(KEY_RELAY_URL, payload.relayUrl)
            .putString(KEY_MAC_DEVICE_ID, payload.macDeviceId)
            .putString(KEY_MAC_IDENTITY_PUBLIC_KEY, payload.macIdentityPublicKey)
            .putLong(KEY_EXPIRES_AT, payload.expiresAt)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "remodex_pairing_store"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_RELAY_URL = "relay_url"
        const val KEY_MAC_DEVICE_ID = "mac_device_id"
        const val KEY_MAC_IDENTITY_PUBLIC_KEY = "mac_identity_public_key"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
