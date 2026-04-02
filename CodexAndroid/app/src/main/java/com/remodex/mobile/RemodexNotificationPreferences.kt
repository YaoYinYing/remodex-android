package com.remodex.mobile

import android.content.Context

data class RemodexNotificationPreferences(
    val pinnedStatusEnabled: Boolean = true,
    val permissionAlertsEnabled: Boolean = true,
    val rateLimitAlertsEnabled: Boolean = true,
    val gitAlertsEnabled: Boolean = true,
    val ciAlertsEnabled: Boolean = true
)

class RemodexNotificationPreferencesStore(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): RemodexNotificationPreferences {
        return RemodexNotificationPreferences(
            pinnedStatusEnabled = prefs.getBoolean(KEY_PINNED_STATUS, true),
            permissionAlertsEnabled = prefs.getBoolean(KEY_PERMISSION_ALERTS, true),
            rateLimitAlertsEnabled = prefs.getBoolean(KEY_RATE_LIMIT_ALERTS, true),
            gitAlertsEnabled = prefs.getBoolean(KEY_GIT_ALERTS, true),
            ciAlertsEnabled = prefs.getBoolean(KEY_CI_ALERTS, true)
        )
    }

    fun save(value: RemodexNotificationPreferences) {
        prefs.edit()
            .putBoolean(KEY_PINNED_STATUS, value.pinnedStatusEnabled)
            .putBoolean(KEY_PERMISSION_ALERTS, value.permissionAlertsEnabled)
            .putBoolean(KEY_RATE_LIMIT_ALERTS, value.rateLimitAlertsEnabled)
            .putBoolean(KEY_GIT_ALERTS, value.gitAlertsEnabled)
            .putBoolean(KEY_CI_ALERTS, value.ciAlertsEnabled)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "remodex.notification_prefs"
        const val KEY_PINNED_STATUS = "pinned_status"
        const val KEY_PERMISSION_ALERTS = "permission_alerts"
        const val KEY_RATE_LIMIT_ALERTS = "rate_limit_alerts"
        const val KEY_GIT_ALERTS = "git_alerts"
        const val KEY_CI_ALERTS = "ci_alerts"
    }
}
