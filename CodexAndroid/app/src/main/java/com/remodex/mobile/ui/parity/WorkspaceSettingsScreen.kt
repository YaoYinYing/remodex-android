package com.remodex.mobile.ui.parity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode

@Composable
fun WorkspaceSettingsScreen(
    connectionState: ConnectionState,
    status: String,
    selectedModel: String,
    availableModels: List<String>,
    currentProjectPath: String?,
    archivedThreadCount: Int,
    hasProAccess: Boolean,
    trustedPairLabel: String?,
    notificationsEnabled: Boolean,
    rateLimitInfo: String,
    ciStatus: String,
    fontStyle: AppFontStyle,
    toneMode: AppToneMode,
    loggerLevel: LoggerLevel,
    loggerMaxLines: Int,
    onClose: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onFontStyleChanged: (AppFontStyle) -> Unit,
    onToneModeChanged: (AppToneMode) -> Unit,
    onLoggerLevelChanged: (LoggerLevel) -> Unit,
    onLoggerMaxLinesChanged: (Int) -> Unit,
    onSwitchModel: (String) -> Unit,
    onDisconnect: () -> Unit,
    onForgetPair: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect")
                }
            }
        }
        item {
            SettingsPanel(title = "Archived Chats") {
                SettingsRow(
                    title = "Archived chats",
                    value = archivedThreadCount.toString()
                )
                Text(
                    text = "Manage archived conversations from the sidebar chat list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsPanel(title = "Appearance") {
                SettingsRow(
                    title = "Font",
                    value = fontStyle.title
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.SYSTEM) }, modifier = Modifier.weight(1f)) {
                        Text("System")
                    }
                    OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.GEIST) }, modifier = Modifier.weight(1f)) {
                        Text("Geist")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.GEIST_MONO) }, modifier = Modifier.weight(1f)) {
                        Text("Geist Mono")
                    }
                    OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.JETBRAINS_MONO) }, modifier = Modifier.weight(1f)) {
                        Text("JetBrains Mono")
                    }
                }
                SettingsRow(
                    title = "Tone",
                    value = toneMode.name.lowercase()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onToneModeChanged(AppToneMode.SYSTEM) }, modifier = Modifier.weight(1f)) {
                        Text("System")
                    }
                    OutlinedButton(onClick = { onToneModeChanged(AppToneMode.FORCE_LIGHT) }, modifier = Modifier.weight(1f)) {
                        Text("Light")
                    }
                    OutlinedButton(onClick = { onToneModeChanged(AppToneMode.FORCE_DARK) }, modifier = Modifier.weight(1f)) {
                        Text("Dark")
                    }
                }
            }
        }
        item {
            SettingsPanel(title = "Notifications") {
                SettingsRow(
                    title = "Status",
                    value = if (notificationsEnabled) "Authorized" else "Not requested"
                )
                Text(
                    text = "Used for local alerts when a run completes while the app is in background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!notificationsEnabled) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow notifications")
                    }
                }
            }
        }
        item {
            SettingsPanel(title = "ChatGPT") {
                SettingsRow(
                    title = "Status",
                    value = "Unavailable"
                )
                Text(
                    text = "This mobile client keeps local bridge parity. GPT account state is managed on the paired desktop bridge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsPanel(title = "Remodex Pro") {
                SettingsRow(
                    title = "Status",
                    value = if (hasProAccess) "Active (dev gate)" else "Free (dev gate)"
                )
                Text(
                    text = "Public pricing is hidden in this dev build. Paywall remains present as a silent gate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsPanel(title = "Bridge Version") {
                val installedVersion = "0.1.0"
                SettingsRow(title = "Installed on device", value = installedVersion)
                SettingsRow(title = "Latest available", value = "Unknown")
                Text(
                    text = "Connect to the local bridge to compare package versions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsPanel(title = "Runtime Defaults") {
                SettingsRow(title = "Model", value = selectedModel)
                if (availableModels.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableModels.take(8).forEach { model ->
                            OutlinedButton(
                                onClick = { onSwitchModel(model) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(model)
                            }
                        }
                    }
                }
                SettingsRow(title = "Reasoning", value = "Auto")
                SettingsRow(title = "Speed", value = "Normal")
                SettingsRow(title = "Access", value = "Workspace write")
            }
        }
        item {
            SettingsPanel(title = "About") {
                Text(
                    text = "Chats are end-to-end encrypted between your phone and Mac. Relay only sees ciphertext after secure handshake.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Support and docs: phodex.app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            SettingsPanel(title = "Usage") {
                SettingsRow(title = "Rate limits", value = rateLimitInfo)
                SettingsRow(title = "CI/CD", value = ciStatus)
            }
        }
        item {
            SettingsPanel(
                title = "Connection"
            ) {
                StatusPill(
                    text = when (connectionState) {
                        ConnectionState.Connected -> "Connected"
                        ConnectionState.Connecting -> "Connecting"
                        ConnectionState.Paired -> "Paired"
                        ConnectionState.Disconnected -> "Offline"
                        is ConnectionState.Failed -> "Failed"
                    }
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!trustedPairLabel.isNullOrBlank()) {
                    Text(
                        text = trustedPairLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!currentProjectPath.isNullOrBlank()) {
                    Text(
                        text = currentProjectPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onForgetPair,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Forget Pair")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
        item {
            SettingsPanel(title = "Diagnostics") {
                SettingsRow(title = "Logger level", value = loggerLevel.name)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onLoggerLevelChanged(LoggerLevel.DEBUG) }, modifier = Modifier.weight(1f)) {
                        Text("Debug")
                    }
                    OutlinedButton(onClick = { onLoggerLevelChanged(LoggerLevel.INFO) }, modifier = Modifier.weight(1f)) {
                        Text("Info")
                    }
                    OutlinedButton(onClick = { onLoggerLevelChanged(LoggerLevel.WARN) }, modifier = Modifier.weight(1f)) {
                        Text("Warn")
                    }
                    OutlinedButton(onClick = { onLoggerLevelChanged(LoggerLevel.ERROR) }, modifier = Modifier.weight(1f)) {
                        Text("Error")
                    }
                }
                SettingsRow(title = "Logger max lines", value = loggerMaxLines.toString())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onLoggerMaxLinesChanged((loggerMaxLines - 250).coerceAtLeast(200)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-250")
                    }
                    OutlinedButton(
                        onClick = { onLoggerMaxLinesChanged((loggerMaxLines + 250).coerceAtMost(20_000)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+250")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
