package com.remodex.mobile

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.PairingPayload
import com.remodex.mobile.service.ServiceEventKind
import com.remodex.mobile.service.SharedPreferencesPairingStateStore
import com.remodex.mobile.service.logging.AppLogger
import com.remodex.mobile.ui.RemodexApp
import com.remodex.mobile.ui.parity.QrScannerPairingValidationResult
import com.remodex.mobile.ui.parity.validatePairingQrCode
import kotlinx.coroutines.launch
import java.util.Base64

class MainActivity : ComponentActivity() {
    companion object {
        private const val APP_GROUP_KEY = "remodex.workflow.events"
        private const val STATUS_CHANNEL_ID = "remodex-status"
        private const val PERMISSION_CHANNEL_ID = "remodex-permissions"
        private const val RATE_LIMIT_CHANNEL_ID = "remodex-rate-limits"
        private const val GIT_CHANNEL_ID = "remodex-git"
        private const val CI_CHANNEL_ID = "remodex-ci"
        private const val THROTTLE_WINDOW_MS = 2_500L
        private const val NOTIFICATION_ID_PINNED_STATUS = 1000
        private const val NOTIFICATION_ID_PERMISSION_REQUIRED = 1002
        private const val NOTIFICATION_ID_RATE_LIMIT = 1003
        private const val NOTIFICATION_ID_GIT_ACTION = 1004
        private const val NOTIFICATION_ID_CI_CD = 1005
        private const val LOG_TAG = "MainActivity"
        private const val ACTION_DEBUG_IMPORT_PAIRING = "com.remodex.mobile.DEBUG_IMPORT_PAIRING"
        private const val EXTRA_PAYLOAD_B64 = "payload_b64"
        private const val EXTRA_PAYLOAD_JSON = "payload_json"
        private const val EXTRA_CONNECT_LIVE = "connect_live"
    }

    private lateinit var service: CodexService
    private lateinit var notificationPreferencesStore: RemodexNotificationPreferencesStore
    private var notificationsEnabled by mutableStateOf(false)
    private var notificationPreferences by mutableStateOf(RemodexNotificationPreferences())
    private var isAppForeground: Boolean = false
    private val lastEventAtByKind = mutableMapOf<ServiceEventKind, Long>()
    private val lastEventMessageByKind = mutableMapOf<ServiceEventKind, String>()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
    }
    private val debugPairingImportReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_DEBUG_IMPORT_PAIRING) {
                return
            }
            handleDebugPairingImport(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLogger.initialize(applicationContext)
        service = CodexService(
            pairingStore = SharedPreferencesPairingStateStore(applicationContext)
        )
        notificationPreferencesStore = RemodexNotificationPreferencesStore(applicationContext)
        notificationPreferences = notificationPreferencesStore.load()
        notificationsEnabled = hasNotificationPermission()
        createNotificationChannels()
        observeServiceEvents()
        observePinnedStatusNotification()
        registerDebugPairingImportReceiver()
        handleDebugPairingImportFromIntent(intent)
        attemptStartupReconnectIfPaired()
        setContent {
            RemodexApp(
                service = service,
                notificationsEnabled = notificationsEnabled,
                notificationPreferences = notificationPreferences,
                onRequestNotificationPermission = { requestNotificationPermission() },
                onNotificationPreferencesChanged = { nextPreferences ->
                    notificationPreferences = nextPreferences
                    notificationPreferencesStore.save(nextPreferences)
                    updatePinnedStatusNotification(
                        connectionState = service.connectionState.value,
                        status = service.status.value
                    )
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugPairingImportFromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        isAppForeground = true
        service.setForegroundState(true)
        clearTransientNotifications()
        updatePinnedStatusNotification(
            connectionState = service.connectionState.value,
            status = service.status.value
        )
    }

    override fun onStop() {
        super.onStop()
        isAppForeground = false
        service.setForegroundState(false)
        updatePinnedStatusNotification(
            connectionState = service.connectionState.value,
            status = service.status.value
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(debugPairingImportReceiver)
        if (isFinishing) {
            clearTransientNotifications()
        }
    }

    private fun observeServiceEvents() {
        lifecycleScope.launch {
            service.events.collect { event ->
                if (!hasNotificationPermission()) {
                    return@collect
                }

                if (event.kind == ServiceEventKind.WORK_STATUS_CHANGED) {
                    updatePinnedStatusNotification(
                        connectionState = service.connectionState.value,
                        status = event.message
                    )
                    return@collect
                }

                if (shouldSuppressEventNotification(event)) {
                    return@collect
                }

                val notificationId = notificationIdFor(event.kind)
                val builder = NotificationCompat.Builder(this@MainActivity, channelIdFor(event.kind))
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(eventTitleFor(event.kind))
                    .setContentText(event.message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOnlyAlertOnce(true)
                    .setGroup(APP_GROUP_KEY)
                    .setAutoCancel(true)
                    .setContentIntent(mainActivityPendingIntent())

                NotificationManagerCompat.from(this@MainActivity)
                    .notify(notificationId, builder.build())
            }
        }
    }

    private fun observePinnedStatusNotification() {
        lifecycleScope.launch {
            service.connectionState.collect { connectionState ->
                updatePinnedStatusNotification(
                    connectionState = connectionState,
                    status = service.status.value
                )
            }
        }
        lifecycleScope.launch {
            service.status.collect { status ->
                updatePinnedStatusNotification(
                    connectionState = service.connectionState.value,
                    status = status
                )
            }
        }
    }

    private fun shouldSuppressEventNotification(event: com.remodex.mobile.service.ServiceEvent): Boolean {
        if (isAppForeground) {
            return true
        }

        if (!isCategoryEnabled(event.kind)) {
            return true
        }

        val now = System.currentTimeMillis()
        val lastAt = lastEventAtByKind[event.kind] ?: 0L
        val lastMessage = lastEventMessageByKind[event.kind]
        val sameMessageRecently = lastMessage == event.message && (now - lastAt) < THROTTLE_WINDOW_MS
        if (sameMessageRecently) {
            return true
        }

        lastEventAtByKind[event.kind] = now
        lastEventMessageByKind[event.kind] = event.message
        return false
    }

    private fun notificationIdFor(kind: ServiceEventKind): Int {
        return when (kind) {
            ServiceEventKind.WORK_STATUS_CHANGED -> NOTIFICATION_ID_PINNED_STATUS
            ServiceEventKind.PERMISSION_REQUIRED -> NOTIFICATION_ID_PERMISSION_REQUIRED
            ServiceEventKind.RATE_LIMIT_HIT -> NOTIFICATION_ID_RATE_LIMIT
            ServiceEventKind.GIT_ACTION -> NOTIFICATION_ID_GIT_ACTION
            ServiceEventKind.CI_CD_DONE -> NOTIFICATION_ID_CI_CD
        }
    }

    private fun channelIdFor(kind: ServiceEventKind): String {
        return when (kind) {
            ServiceEventKind.WORK_STATUS_CHANGED -> STATUS_CHANNEL_ID
            ServiceEventKind.PERMISSION_REQUIRED -> PERMISSION_CHANNEL_ID
            ServiceEventKind.RATE_LIMIT_HIT -> RATE_LIMIT_CHANNEL_ID
            ServiceEventKind.GIT_ACTION -> GIT_CHANNEL_ID
            ServiceEventKind.CI_CD_DONE -> CI_CHANNEL_ID
        }
    }

    private fun eventTitleFor(kind: ServiceEventKind): String {
        return when (kind) {
            ServiceEventKind.WORK_STATUS_CHANGED -> "Working Status"
            ServiceEventKind.PERMISSION_REQUIRED -> "Permission Required"
            ServiceEventKind.RATE_LIMIT_HIT -> "Rate Limit Hit"
            ServiceEventKind.GIT_ACTION -> "Git Action"
            ServiceEventKind.CI_CD_DONE -> "CI/CD Updated"
        }
    }

    private fun isCategoryEnabled(kind: ServiceEventKind): Boolean {
        return when (kind) {
            ServiceEventKind.WORK_STATUS_CHANGED -> notificationPreferences.pinnedStatusEnabled
            ServiceEventKind.PERMISSION_REQUIRED -> notificationPreferences.permissionAlertsEnabled
            ServiceEventKind.RATE_LIMIT_HIT -> notificationPreferences.rateLimitAlertsEnabled
            ServiceEventKind.GIT_ACTION -> notificationPreferences.gitAlertsEnabled
            ServiceEventKind.CI_CD_DONE -> notificationPreferences.ciAlertsEnabled
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationsEnabled = true
            updatePinnedStatusNotification(service.connectionState.value, service.status.value)
            return
        }
        if (hasNotificationPermission()) {
            notificationsEnabled = true
            updatePinnedStatusNotification(service.connectionState.value, service.status.value)
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                STATUS_CHANNEL_ID,
                "Remodex Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pinned connection and working status."
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PERMISSION_CHANNEL_ID,
                "Remodex Approvals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Approval and permission requests."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RATE_LIMIT_CHANNEL_ID,
                "Remodex Rate Limits",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rate limit warnings and resets."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                GIT_CHANNEL_ID,
                "Remodex Git",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Git actions and repository updates."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CI_CHANNEL_ID,
                "Remodex CI",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "CI/CD completion and failures."
            }
        )
    }

    private fun updatePinnedStatusNotification(
        connectionState: com.remodex.mobile.service.ConnectionState,
        status: String
    ) {
        val manager = NotificationManagerCompat.from(this)
        if (!notificationsEnabled || !notificationPreferences.pinnedStatusEnabled || isAppForeground) {
            manager.cancel(NOTIFICATION_ID_PINNED_STATUS)
            return
        }

        val hasPairing = service.currentPairing() != null
        if (!hasPairing && connectionState == com.remodex.mobile.service.ConnectionState.Disconnected) {
            manager.cancel(NOTIFICATION_ID_PINNED_STATUS)
            return
        }

        val connectionLabel = when (connectionState) {
            com.remodex.mobile.service.ConnectionState.Connected -> "Connected"
            com.remodex.mobile.service.ConnectionState.Connecting -> "Connecting"
            com.remodex.mobile.service.ConnectionState.Paired -> "Paired"
            com.remodex.mobile.service.ConnectionState.Disconnected -> "Offline"
            is com.remodex.mobile.service.ConnectionState.Failed -> "Failed"
        }
        val title = "Remodex · $connectionLabel"
        val builder = NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(mainActivityPendingIntent())

        manager.notify(NOTIFICATION_ID_PINNED_STATUS, builder.build())
    }

    private fun clearTransientNotifications() {
        NotificationManagerCompat.from(this).apply {
            cancel(NOTIFICATION_ID_PERMISSION_REQUIRED)
            cancel(NOTIFICATION_ID_RATE_LIMIT)
            cancel(NOTIFICATION_ID_GIT_ACTION)
            cancel(NOTIFICATION_ID_CI_CD)
        }
    }

    private fun mainActivityPendingIntent() =
        PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun registerDebugPairingImportReceiver() {
        ContextCompat.registerReceiver(
            this,
            debugPairingImportReceiver,
            IntentFilter(ACTION_DEBUG_IMPORT_PAIRING),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun handleDebugPairingImport(intent: Intent) {
        val payload = resolveDebugPairingPayload(intent) ?: run {
            AppLogger.warn(LOG_TAG, "DEBUG_IMPORT_PAIRING ignored because payload was missing.")
            return
        }
        val connectLive = intent.getBooleanExtra(EXTRA_CONNECT_LIVE, true)
        when (val result = validatePairingQrCode(payload)) {
            is QrScannerPairingValidationResult.Success -> {
                applyDebugPairing(result.payload, connectLive)
            }
            is QrScannerPairingValidationResult.BridgeUpdateRequired -> {
                AppLogger.warn(LOG_TAG, "DEBUG_IMPORT_PAIRING rejected: bridge update required.")
            }
            is QrScannerPairingValidationResult.ScanError -> {
                AppLogger.warn(LOG_TAG, "DEBUG_IMPORT_PAIRING rejected: ${result.message}")
            }
        }
    }

    private fun handleDebugPairingImportFromIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        if (!intent.hasExtra(EXTRA_PAYLOAD_B64) && !intent.hasExtra(EXTRA_PAYLOAD_JSON)) {
            return
        }
        handleDebugPairingImport(intent)
    }

    private fun resolveDebugPairingPayload(intent: Intent): String? {
        intent.getStringExtra(EXTRA_PAYLOAD_JSON)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val payloadB64 = intent.getStringExtra(EXTRA_PAYLOAD_B64)?.trim().orEmpty()
        if (payloadB64.isBlank()) {
            return null
        }
        return runCatching {
            String(Base64.getDecoder().decode(payloadB64), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun applyDebugPairing(payload: PairingPayload, connectLive: Boolean) {
        AppLogger.info(
            LOG_TAG,
            "DEBUG_IMPORT_PAIRING accepted for mac=${payload.macDeviceId} relay=${payload.relayUrl} connectLive=$connectLive."
        )
        service.rememberPairing(payload)
        if (connectLive) {
            lifecycleScope.launch {
                runCatching { service.connectLive() }
                    .onFailure { error ->
                        AppLogger.error(LOG_TAG, "DEBUG_IMPORT_PAIRING connectLive failed.", error)
                    }
            }
        }
    }

    private fun attemptStartupReconnectIfPaired() {
        if (service.connectionState.value != com.remodex.mobile.service.ConnectionState.Paired) {
            return
        }
        lifecycleScope.launch {
            runCatching { service.connectLive() }
                .onFailure { error ->
                    AppLogger.warn(LOG_TAG, "Startup reconnect failed; keeping saved pairing.", error)
                }
        }
    }
}
