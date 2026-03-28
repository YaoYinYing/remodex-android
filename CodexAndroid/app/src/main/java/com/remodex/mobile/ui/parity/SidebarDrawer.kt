package com.remodex.mobile.ui.parity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode

@Composable
fun SidebarDrawerContent(
    todos: List<WebsiteTodo>,
    todoStates: Map<String, TodoState>,
    rateLimitInfo: String,
    ciStatus: String,
    notificationsEnabled: Boolean,
    fontStyle: AppFontStyle,
    toneMode: AppToneMode,
    onFontStyleChanged: (AppFontStyle) -> Unit,
    onToneModeChanged: (AppToneMode) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    autoRefreshEnabled: Boolean,
    onAutoRefreshChanged: (Boolean) -> Unit,
    onAdvanceTodo: () -> Unit,
    onRefreshWorkspace: () -> Unit,
    loggerLevel: LoggerLevel,
    loggerMaxLines: Int,
    onLoggerLevelChanged: (LoggerLevel) -> Unit,
    onLoggerMaxLinesChanged: (Int) -> Unit,
    onGitPull: () -> Unit,
    onGitPush: () -> Unit,
    onDisconnect: () -> Unit
) {
    var loggerLinesInput by remember { mutableStateOf(loggerMaxLines.toString()) }
    LaunchedEffect(loggerMaxLines) {
        loggerLinesInput = loggerMaxLines.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Sidebar",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        SectionCard(
            title = "Parity Checklist",
            subtitle = "Pinned iOS website feature mapping."
        ) {
            todos.forEach { todo ->
                TodoRow(todo = todo, state = todoStates[todo.id] ?: TodoState.TODO)
            }
            Button(
                onClick = onAdvanceTodo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Advance Next TODO")
            }
        }

        SectionCard(
            title = "iOS Component Map",
            subtitle = "One-to-one module parity tracker."
        ) {
            ComponentParityChecklist.forEach { item ->
                Text(
                    text = "${item.component}: ${item.status.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = item.iosReference,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(
            title = "Settings",
            subtitle = "Match iOS typography and tone options."
        ) {
            Text("Font style: ${fontStyle.title}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.SYSTEM) }, modifier = Modifier.weight(1f)) {
                    Text("System")
                }
                OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.GEIST) }, modifier = Modifier.weight(1f)) {
                    Text("Geist")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.GEIST_MONO) }, modifier = Modifier.weight(1f)) {
                    Text("Geist Mono")
                }
                OutlinedButton(onClick = { onFontStyleChanged(AppFontStyle.JETBRAINS_MONO) }, modifier = Modifier.weight(1f)) {
                    Text("JetBrains Mono")
                }
            }
            Text("Tone mode: ${toneMode.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAutoRefreshChanged(!autoRefreshEnabled) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (autoRefreshEnabled) "Auto Refresh On" else "Auto Refresh Off")
                }
                OutlinedButton(
                    onClick = onRefreshWorkspace,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Force Refresh")
                }
            }
            Text("Logger level: ${loggerLevel.name}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onLoggerLevelChanged(LoggerLevel.DEBUG) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Debug")
                }
                OutlinedButton(
                    onClick = { onLoggerLevelChanged(LoggerLevel.INFO) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Info")
                }
                OutlinedButton(
                    onClick = { onLoggerLevelChanged(LoggerLevel.WARN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Warn")
                }
                OutlinedButton(
                    onClick = { onLoggerLevelChanged(LoggerLevel.ERROR) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Error")
                }
            }
            OutlinedTextField(
                value = loggerLinesInput,
                onValueChange = { loggerLinesInput = it.filter { ch -> ch.isDigit() }.take(6) },
                label = { Text("Logger max lines") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val current = loggerLinesInput.toIntOrNull() ?: loggerMaxLines
                        val updated = (current - 500).coerceAtLeast(200)
                        loggerLinesInput = updated.toString()
                        onLoggerMaxLinesChanged(updated)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("-500")
                }
                OutlinedButton(
                    onClick = {
                        val current = loggerLinesInput.toIntOrNull() ?: loggerMaxLines
                        val updated = (current + 500).coerceAtMost(20000)
                        loggerLinesInput = updated.toString()
                        onLoggerMaxLinesChanged(updated)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+500")
                }
                Button(
                    onClick = {
                        val parsed = loggerLinesInput.toIntOrNull()
                        if (parsed != null) {
                            onLoggerMaxLinesChanged(parsed)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
            if (!notificationsEnabled) {
                Button(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Notifications")
                }
            }
            Text(
                text = rateLimitInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ciStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(
            title = "Git Actions",
            subtitle = "Quick project sync commands."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onGitPull,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pull")
                }
                OutlinedButton(
                    onClick = onGitPush,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Push")
                }
            }
        }

        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect")
        }
    }
}
