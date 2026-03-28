package com.remodex.mobile.ui.parity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    notificationsEnabled: Boolean,
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
    onDisconnect: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            SectionCard(
                title = "Connection",
                subtitle = "Mirror iOS settings connection status and trusted-pair controls."
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
                if (!currentProjectPath.isNullOrBlank()) {
                    Text(
                        text = currentProjectPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SectionCard(
                title = "Runtime Defaults",
                subtitle = "Model and execution defaults."
            ) {
                Text(
                    text = "Current model: $selectedModel",
                    style = MaterialTheme.typography.bodySmall
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableModels.take(6).forEach { model ->
                        OutlinedButton(
                            onClick = { onSwitchModel(model) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(model)
                        }
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Appearance",
                subtitle = "Typography and tone."
            ) {
                Text("Font: ${fontStyle.title}", style = MaterialTheme.typography.bodySmall)
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
                Text("Tone: ${toneMode.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
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
            SectionCard(
                title = "Notifications + Logger",
                subtitle = "Device notifications and diagnostics."
            ) {
                if (!notificationsEnabled) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Notifications")
                    }
                }
                Text("Logger level: ${loggerLevel.name}", style = MaterialTheme.typography.bodySmall)
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
                Text(
                    text = "Max lines: $loggerMaxLines",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
