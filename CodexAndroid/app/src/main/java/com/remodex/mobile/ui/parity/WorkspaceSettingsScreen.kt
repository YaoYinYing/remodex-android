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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.mobile.RemodexNotificationPreferences
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.BridgeManagedAccountStatus
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import com.remodex.mobile.ui.theme.remodexFontSet

@Composable
fun WorkspaceSettingsScreen(
    connectionState: ConnectionState,
    status: String,
    selectedModel: String,
    availableModels: List<String>,
    selectedReasoningEffort: String,
    availableReasoningEfforts: List<String>,
    currentProjectPath: String?,
    archivedThreadCount: Int,
    hasProAccess: Boolean,
    trustedPairLabel: String?,
    notificationsEnabled: Boolean,
    notificationPreferences: RemodexNotificationPreferences,
    rateLimitInfo: String,
    ciStatus: String,
    bridgeInstalledVersion: String?,
    latestBridgePackageVersion: String?,
    gptAccountStatus: BridgeManagedAccountStatus,
    gptAccountEmail: String?,
    fontStyle: AppFontStyle,
    toneMode: AppToneMode,
    loggerLevel: LoggerLevel,
    loggerMaxLines: Int,
    dockCollapsedSide: String,
    onClose: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onNotificationPreferencesChanged: (RemodexNotificationPreferences) -> Unit,
    onFontStyleChanged: (AppFontStyle) -> Unit,
    onToneModeChanged: (AppToneMode) -> Unit,
    onLoggerLevelChanged: (LoggerLevel) -> Unit,
    onLoggerMaxLinesChanged: (Int) -> Unit,
    onDockCollapsedSideChanged: (String) -> Unit,
    onSwitchModel: (String) -> Unit,
    onSwitchReasoningEffort: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshBridgeManagedState: () -> Unit,
    onForgetPair: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
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
                    FontPreviewButton(style = AppFontStyle.SYSTEM, selectedStyle = fontStyle, onFontStyleChanged = onFontStyleChanged, modifier = Modifier.weight(1f))
                    FontPreviewButton(style = AppFontStyle.GEIST, selectedStyle = fontStyle, onFontStyleChanged = onFontStyleChanged, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontPreviewButton(style = AppFontStyle.GEIST_MONO, selectedStyle = fontStyle, onFontStyleChanged = onFontStyleChanged, modifier = Modifier.weight(1f))
                    FontPreviewButton(style = AppFontStyle.JETBRAINS_MONO, selectedStyle = fontStyle, onFontStyleChanged = onFontStyleChanged, modifier = Modifier.weight(1f))
                }
                SettingsRow(
                    title = "Tone",
                    value = toneMode.name.lowercase()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsChoiceButton(
                        label = "System",
                        selected = toneMode == AppToneMode.SYSTEM,
                        onClick = { onToneModeChanged(AppToneMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsChoiceButton(
                        label = "Light",
                        selected = toneMode == AppToneMode.FORCE_LIGHT,
                        onClick = { onToneModeChanged(AppToneMode.FORCE_LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsChoiceButton(
                        label = "Dark",
                        selected = toneMode == AppToneMode.FORCE_DARK,
                        onClick = { onToneModeChanged(AppToneMode.FORCE_DARK) },
                        modifier = Modifier.weight(1f)
                    )
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
                    text = "Pinned connection status stays available in background. Approval, rate-limit, git, and CI alerts can be toggled separately.",
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
                NotificationToggleRow(
                    title = "Pinned status",
                    enabled = notificationPreferences.pinnedStatusEnabled,
                    onToggle = {
                        onNotificationPreferencesChanged(
                            notificationPreferences.copy(pinnedStatusEnabled = !notificationPreferences.pinnedStatusEnabled)
                        )
                    }
                )
                NotificationToggleRow(
                    title = "Approval alerts",
                    enabled = notificationPreferences.permissionAlertsEnabled,
                    onToggle = {
                        onNotificationPreferencesChanged(
                            notificationPreferences.copy(permissionAlertsEnabled = !notificationPreferences.permissionAlertsEnabled)
                        )
                    }
                )
                NotificationToggleRow(
                    title = "Rate limit alerts",
                    enabled = notificationPreferences.rateLimitAlertsEnabled,
                    onToggle = {
                        onNotificationPreferencesChanged(
                            notificationPreferences.copy(rateLimitAlertsEnabled = !notificationPreferences.rateLimitAlertsEnabled)
                        )
                    }
                )
                NotificationToggleRow(
                    title = "Git alerts",
                    enabled = notificationPreferences.gitAlertsEnabled,
                    onToggle = {
                        onNotificationPreferencesChanged(
                            notificationPreferences.copy(gitAlertsEnabled = !notificationPreferences.gitAlertsEnabled)
                        )
                    }
                )
                NotificationToggleRow(
                    title = "CI/CD alerts",
                    enabled = notificationPreferences.ciAlertsEnabled,
                    onToggle = {
                        onNotificationPreferencesChanged(
                            notificationPreferences.copy(ciAlertsEnabled = !notificationPreferences.ciAlertsEnabled)
                        )
                    }
                )
            }
        }
        item {
            SettingsPanel(title = "ChatGPT") {
                SettingsRow(
                    title = "Status",
                    value = when (gptAccountStatus) {
                        BridgeManagedAccountStatus.AUTHENTICATED -> "Connected"
                        BridgeManagedAccountStatus.NOT_LOGGED_IN -> "Not logged in"
                        BridgeManagedAccountStatus.LOGIN_IN_PROGRESS -> "Syncing"
                        BridgeManagedAccountStatus.REAUTH_REQUIRED -> "Needs reauth"
                        BridgeManagedAccountStatus.UNKNOWN -> "Unknown"
                    }
                )
                if (!gptAccountEmail.isNullOrBlank()) {
                    SettingsRow(title = "Account", value = gptAccountEmail)
                }
                Text(
                    text = "ChatGPT voice/account state is managed on the paired Mac bridge and mirrored here for recovery guidance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRefreshBridgeManagedState, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Status")
                }
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
                SettingsRow(title = "Installed on Mac", value = bridgeInstalledVersion ?: "Unknown")
                SettingsRow(title = "Latest available", value = latestBridgePackageVersion ?: "Unknown")
                Text(
                    text = "Foreground reconnect refreshes bridge package status independently from ChatGPT account state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRefreshBridgeManagedState, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Bridge Version")
                }
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
                            SettingsChoiceButton(
                                label = model,
                                selected = selectedModel == model,
                                onClick = { onSwitchModel(model) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                SettingsRow(title = "Reasoning", value = selectedReasoningEffort)
                if (availableReasoningEfforts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableReasoningEfforts.take(6).forEach { effort ->
                            SettingsChoiceButton(
                                label = effort,
                                selected = selectedReasoningEffort == effort,
                                onClick = { onSwitchReasoningEffort(effort) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                SettingsRow(title = "Speed", value = "Normal")
                SettingsRow(title = "Access", value = "Workspace write")
                SettingsRow(
                    title = "Collapsed dock side",
                    value = dockCollapsedSide.replaceFirstChar { it.uppercase() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsChoiceButton(
                        label = "Left",
                        selected = dockCollapsedSide == "left",
                        onClick = { onDockCollapsedSideChanged("left") },
                        modifier = Modifier.weight(1f)
                    )
                    SettingsChoiceButton(
                        label = "Right",
                        selected = dockCollapsedSide == "right",
                        onClick = { onDockCollapsedSideChanged("right") },
                        modifier = Modifier.weight(1f)
                    )
                }
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
                if (ciStatus.isNotBlank()) {
                    SettingsRow(title = "CI/CD", value = ciStatus)
                }
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
                    LoggerLevelButton(icon = "·", label = "Debug", selected = loggerLevel == LoggerLevel.DEBUG, onClick = { onLoggerLevelChanged(LoggerLevel.DEBUG) }, modifier = Modifier.weight(1f))
                    LoggerLevelButton(icon = "i", label = "Info", selected = loggerLevel == LoggerLevel.INFO, onClick = { onLoggerLevelChanged(LoggerLevel.INFO) }, modifier = Modifier.weight(1f))
                    LoggerLevelButton(icon = "!", label = "Warn", selected = loggerLevel == LoggerLevel.WARN, onClick = { onLoggerLevelChanged(LoggerLevel.WARN) }, modifier = Modifier.weight(1f))
                    LoggerLevelButton(icon = "x", label = "Error", selected = loggerLevel == LoggerLevel.ERROR, onClick = { onLoggerLevelChanged(LoggerLevel.ERROR) }, modifier = Modifier.weight(1f))
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
private fun NotificationToggleRow(
    title: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        SettingsChoiceButton(
            label = if (enabled) "On" else "Off",
            selected = enabled,
            onClick = onToggle
        )
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
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.9f),
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
private fun FontPreviewButton(
    style: AppFontStyle,
    selectedStyle: AppFontStyle,
    onFontStyleChanged: (AppFontStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val fontSet = remodexFontSet(style)
    SettingsChoiceButton(
        label = style.title,
        selected = selectedStyle == style,
        onClick = { onFontStyleChanged(style) },
        modifier = modifier,
        supporting = {
            Text(
                text = if (selectedStyle == style) "Selected" else "Preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = style.title,
                fontFamily = fontSet.prose,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LoggerLevelButton(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsChoiceButton(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SettingsChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (content != null) {
                content()
            } else {
                Text(text = label)
            }
            if (supporting != null) {
                supporting()
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
