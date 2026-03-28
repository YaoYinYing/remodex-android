package com.remodex.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ServiceEventKind
import com.remodex.mobile.service.SharedPreferencesPairingStateStore
import com.remodex.mobile.ui.RemodexApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val APP_CHANNEL_ID = "remodex-workflow-events"
    }

    private lateinit var service: CodexService
    private var notificationsEnabled by mutableStateOf(false)
    private var nextNotificationId by mutableIntStateOf(1)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = CodexService(
            pairingStore = SharedPreferencesPairingStateStore(applicationContext)
        )
        notificationsEnabled = hasNotificationPermission()
        createNotificationChannel()
        observeServiceEvents()
        setContent {
            RemodexApp(
                service = service,
                notificationsEnabled = notificationsEnabled,
                onRequestNotificationPermission = { requestNotificationPermission() }
            )
        }
    }

    private fun observeServiceEvents() {
        lifecycleScope.launch {
            service.events.collect { event ->
                if (!hasNotificationPermission()) {
                    return@collect
                }
                val builder = NotificationCompat.Builder(this@MainActivity, APP_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(
                        when (event.kind) {
                            ServiceEventKind.WORK_STATUS_CHANGED -> "Working Status"
                            ServiceEventKind.PERMISSION_REQUIRED -> "Permission Required"
                            ServiceEventKind.RATE_LIMIT_HIT -> "Rate Limit Hit"
                            ServiceEventKind.GIT_ACTION -> "Git Action"
                            ServiceEventKind.CI_CD_DONE -> "CI/CD Updated"
                        }
                    )
                    .setContentText(event.message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                NotificationManagerCompat.from(this@MainActivity)
                    .notify(nextNotificationId++, builder.build())
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationsEnabled = true
            return
        }
        if (hasNotificationPermission()) {
            notificationsEnabled = true
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            APP_CHANNEL_ID,
            "Remodex Workflow Events",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Status notifications for workspace, permissions, git, and CI events."
        }
        manager.createNotificationChannel(channel)
    }
}
