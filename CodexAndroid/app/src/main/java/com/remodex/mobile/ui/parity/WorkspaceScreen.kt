package com.remodex.mobile.ui.parity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.PendingPermissionRequest
import com.remodex.mobile.service.ServiceEvent
import com.remodex.mobile.service.push.PushRegistrationPayload
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(
    service: CodexService,
    connectionState: ConnectionState,
    status: String,
    currentProjectPath: String,
    availableModels: List<String>,
    selectedModel: String,
    pendingPermissions: List<PendingPermissionRequest>,
    rateLimitInfo: String,
    ciStatus: String,
    gitStatusSummary: String,
    gitBranches: List<String>,
    checkoutBranch: String,
    onCheckoutBranchChange: (String) -> Unit,
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    pushToken: String,
    onPushTokenChange: (String) -> Unit,
    manualPermissionId: String,
    onManualPermissionIdChange: (String) -> Unit,
    threads: List<ThreadSummary>,
    selectedThreadId: String?,
    timeline: List<TimelineEntry>,
    composerInput: String,
    onComposerInputChange: (String) -> Unit,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    eventLog: List<ServiceEvent>,
    todos: List<WebsiteTodo>,
    todoStates: MutableMap<String, TodoState>,
    onAdvanceTodo: () -> Unit,
    fontStyle: AppFontStyle,
    toneMode: AppToneMode,
    onFontStyleChanged: (AppFontStyle) -> Unit,
    onToneModeChanged: (AppToneMode) -> Unit,
    loggerLevel: LoggerLevel,
    loggerMaxLines: Int,
    onLoggerLevelChanged: (LoggerLevel) -> Unit,
    onLoggerMaxLinesChanged: (Int) -> Unit,
    onHeaderTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isConnected = connectionState == ConnectionState.Connected
    var autoRefreshEnabled by rememberSaveable { mutableStateOf(true) }
    var refreshGestureDelta by remember { mutableStateOf(0f) }

    LaunchedEffect(isConnected, autoRefreshEnabled) {
        if (!isConnected || !autoRefreshEnabled) {
            return@LaunchedEffect
        }
        while (isActive) {
            delay(2_000L)
            runCatching {
                service.refreshThreads(silentStatus = true)
                service.refreshGitStatus(silentStatus = true)
                service.refreshGitBranches(silentStatus = true)
                service.refreshRateLimitInfo(silentStatus = true)
                service.refreshPendingPermissions(silentStatus = true)
                service.refreshModels(silentStatus = true)
                service.refreshCiStatus(silentStatus = true)
            }
        }
    }

    val pageGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.background
        )
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                SidebarDrawerContent(
                    todos = todos,
                    todoStates = todoStates,
                    rateLimitInfo = rateLimitInfo,
                    ciStatus = ciStatus,
                    notificationsEnabled = notificationsEnabled,
                    fontStyle = fontStyle,
                    toneMode = toneMode,
                    onFontStyleChanged = onFontStyleChanged,
                    onToneModeChanged = onToneModeChanged,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    autoRefreshEnabled = autoRefreshEnabled,
                    onAutoRefreshChanged = { autoRefreshEnabled = it },
                    onAdvanceTodo = onAdvanceTodo,
                    onRefreshWorkspace = {
                        scope.launch {
                            runCatching { service.forceRefreshWorkspace() }
                        }
                    },
                    loggerLevel = loggerLevel,
                    loggerMaxLines = loggerMaxLines,
                    onLoggerLevelChanged = onLoggerLevelChanged,
                    onLoggerMaxLinesChanged = onLoggerMaxLinesChanged,
                    onGitPull = {
                        scope.launch {
                            runCatching { service.gitPull() }
                        }
                    },
                    onGitPush = {
                        scope.launch {
                            runCatching { service.gitPush() }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            runCatching { service.disconnect() }
                        }
                    }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageGradient)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Text("Menu")
                        }
                        Text(
                            text = "Working Page",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { service.forceRefreshWorkspace() }
                                }
                            }
                        ) {
                            Text("Sync")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HeroCard(
                            stateLabel = connectionStateLabel(connectionState),
                            status = status,
                            indicatorColor = if (isConnected) Color(0xFF2DB17D) else Color(0xFFD9534F),
                            subtitle = "iOS-parity workspace shell with side-slide controls",
                            onTap = onHeaderTap
                        )
                    }

                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workspace_refresh_strip")
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dragAmount ->
                                            if (dragAmount > 0f) {
                                                refreshGestureDelta += dragAmount
                                            }
                                        },
                                        onDragEnd = {
                                            if (refreshGestureDelta > 180f) {
                                                scope.launch {
                                                    runCatching { service.forceRefreshWorkspace() }
                                                }
                                            }
                                            refreshGestureDelta = 0f
                                        },
                                        onDragCancel = {
                                            refreshGestureDelta = 0f
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Gesture strip: press and slide down here to force workspace refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    item {
                        SectionCard(
                            title = "Current Project",
                            subtitle = "Project path, model switching, and runtime sync status."
                        ) {
                            Text(
                                text = currentProjectPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Selected model: $selectedModel",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableModels.forEach { model ->
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { service.switchModel(model) }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = model,
                                            color = if (model == selectedModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "Permission Granting",
                            subtitle = "Approve or deny pending runtime actions."
                        ) {
                            if (pendingPermissions.isEmpty()) {
                                Text(
                                    text = "No pending permissions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                pendingPermissions.forEach { request ->
                                    PermissionRow(
                                        request = request,
                                        onAllow = {
                                            scope.launch { runCatching { service.grantPermission(request.id, allow = true) } }
                                        },
                                        onDeny = {
                                            scope.launch { runCatching { service.grantPermission(request.id, allow = false) } }
                                        }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = manualPermissionId,
                                onValueChange = onManualPermissionIdChange,
                                label = { Text("Manual permission ID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching { service.grantPermission(manualPermissionId, allow = true) }
                                            .onSuccess { onManualPermissionIdChange("") }
                                    }
                                },
                                enabled = manualPermissionId.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant By ID")
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "Code Changes",
                            subtitle = "Branch selection, commit, and remote sync actions."
                        ) {
                            Text(
                                text = gitStatusSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                gitBranches.forEach { branch ->
                                    OutlinedButton(onClick = { onCheckoutBranchChange(branch) }) {
                                        Text(branch)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = checkoutBranch,
                                onValueChange = onCheckoutBranchChange,
                                label = { Text("Branch") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = commitMessage,
                                onValueChange = onCommitMessageChange,
                                label = { Text("Commit message") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { scope.launch { runCatching { service.checkoutGitBranch(checkoutBranch) } } },
                                    enabled = checkoutBranch.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Checkout") }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { service.gitCommit(commitMessage) }
                                                .onSuccess { onCommitMessageChange("") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Commit") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { scope.launch { runCatching { service.gitPull() } } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Pull") }
                                OutlinedButton(
                                    onClick = { scope.launch { runCatching { service.gitPush() } } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Push") }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "Chat + Threads",
                            subtitle = "Current project chat context with iOS-matched controls."
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { scope.launch { runCatching { service.startThread() } } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("New Thread") }
                                OutlinedButton(
                                    onClick = { scope.launch { runCatching { service.refreshThreads() } } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Refresh Threads") }
                            }
                            if (threads.isEmpty()) {
                                Text(
                                    text = "No threads yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                threads.forEach { thread ->
                                    ThreadRow(
                                        thread = thread,
                                        isSelected = selectedThreadId == thread.id,
                                        onClick = { scope.launch { runCatching { service.openThread(thread.id) } } }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = composerInput,
                                onValueChange = onComposerInputChange,
                                label = { Text("Ask anything... @files, \$skills, /commands") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { service.sendTurnStart(composerInput) }
                                                .onSuccess { onComposerInputChange("") }
                                        }
                                    },
                                    enabled = composerInput.isNotBlank() && selectedThreadId != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Send") }
                                OutlinedButton(
                                    onClick = { scope.launch { runCatching { service.interruptActiveTurn() } } },
                                    enabled = selectedThreadId != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Stop") }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "Timeline",
                            subtitle = "Latest conversation stream and reasoning entries."
                        ) {
                            val visibleTimeline = timeline.takeLast(24)
                            if (visibleTimeline.isEmpty()) {
                                Text(
                                    text = "No timeline events yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                visibleTimeline.forEach { entry ->
                                    TimelineRow(item = entry)
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "Notifications + Sync",
                            subtitle = "Push registration, rate-limit state, CI/CD state, and event feed."
                        ) {
                            if (!notificationsEnabled) {
                                Button(
                                    onClick = onRequestNotificationPermission,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Enable Notification Permission")
                                }
                            }
                            OutlinedTextField(
                                value = pushToken,
                                onValueChange = onPushTokenChange,
                                label = { Text("FCM token") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            service.registerPushToken(
                                                PushRegistrationPayload(
                                                    deviceToken = pushToken,
                                                    alertsEnabled = true
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = pushToken.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Register Push") }
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
                            if (eventLog.isEmpty()) {
                                Text(
                                    text = "No notifications yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                eventLog.take(8).forEach { event ->
                                    EventRow(event = event)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
