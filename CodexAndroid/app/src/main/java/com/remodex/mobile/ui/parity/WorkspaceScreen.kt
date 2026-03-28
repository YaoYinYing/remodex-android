package com.remodex.mobile.ui.parity

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.FileAutocompleteMatch
import com.remodex.mobile.service.PendingPermissionRequest
import com.remodex.mobile.service.SkillSuggestion
import com.remodex.mobile.service.TurnImageAttachment
import com.remodex.mobile.service.logging.LoggerLevel
import com.remodex.mobile.ui.theme.AppFontStyle
import com.remodex.mobile.ui.theme.AppToneMode
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_COMPOSER_ATTACHMENTS = 4

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
    threads: List<ThreadSummary>,
    selectedThreadId: String?,
    timeline: List<TimelineEntry>,
    composerInput: String,
    onComposerInputChange: (String) -> Unit,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
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
    val queuedDrafts = remember { mutableStateListOf<QueuedComposerDraft>() }
    val mentionedFiles = remember { mutableStateListOf<String>() }
    val mentionedSkills = remember { mutableStateListOf<SkillSuggestion>() }
    var fileSuggestions by remember { mutableStateOf<List<FileAutocompleteMatch>>(emptyList()) }
    var skillSuggestions by remember { mutableStateOf<List<SkillSuggestion>>(emptyList()) }
    val activeComposerToken = remember(composerInput) { detectComposerAutocompleteToken(composerInput) }
    val commandSuggestions = remember(activeComposerToken) {
        when (val token = activeComposerToken) {
            is ComposerAutocompleteToken.Command -> filterComposerCommands(token.query)
            else -> emptyList()
        }
    }
    var queuePaused by rememberSaveable { mutableStateOf(false) }
    val mediaAttachments = remember { mutableStateListOf<TurnImageAttachment>() }
    var attachmentHint by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceDraftText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val loaded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                TurnImageAttachment(
                    dataUrl = "data:image/jpeg;base64,$base64",
                    label = uri.lastPathSegment ?: "gallery-image"
                )
            }
        }.getOrNull()
        if (loaded != null) {
            if (mediaAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                attachmentHint = "You can attach up to $MAX_COMPOSER_ATTACHMENTS images per message."
            } else {
                mediaAttachments.add(loaded)
                attachmentHint = null
            }
        }
    }
    val cameraPreviewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val payload = bitmap?.toJpegDataUrl()?.let { dataUrl ->
            TurnImageAttachment(
                dataUrl = dataUrl,
                label = "camera-${System.currentTimeMillis()}.jpg"
            )
        }
        if (payload != null) {
            if (mediaAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                attachmentHint = "You can attach up to $MAX_COMPOSER_ATTACHMENTS images per message."
            } else {
                mediaAttachments.add(payload)
                attachmentHint = null
            }
        }
    }

    LaunchedEffect(isConnected, autoRefreshEnabled) {
        if (!isConnected || !autoRefreshEnabled) {
            return@LaunchedEffect
        }
        var refreshCycle = 0
        while (isActive) {
            delay(3_000L)
            runCatching {
                service.refreshThreads(silentStatus = true, includeTimeline = false)
                refreshCycle += 1
                if (refreshCycle % 3 == 0) {
                    service.refreshActiveThreadTimeline(silentStatus = true)
                }
                service.refreshGitStatus(silentStatus = true)
                service.refreshGitBranches(silentStatus = true)
                service.refreshRateLimitInfo(silentStatus = true)
                service.refreshPendingPermissions(silentStatus = true)
                service.refreshModels(silentStatus = true)
                service.refreshCiStatus(silentStatus = true)
            }
        }
    }

    LaunchedEffect(activeComposerToken, selectedThreadId, currentProjectPath, threads) {
        when (val token = activeComposerToken) {
            is ComposerAutocompleteToken.File -> {
                val selectedThreadRoot = threads
                    .firstOrNull { it.id == selectedThreadId }
                    ?.cwd
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val currentRoot = currentProjectPath
                    .takeUnless { it == "Project path not resolved." }
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val roots = listOfNotNull(selectedThreadRoot, currentRoot).distinct()
                val loadedFiles = runCatching {
                    service.fuzzyFileSearch(query = token.query, roots = roots, limit = 8)
                }.getOrDefault(emptyList())
                fileSuggestions = loadedFiles
                skillSuggestions = emptyList()
            }

            is ComposerAutocompleteToken.Skill -> {
                val selectedThreadRoot = threads
                    .firstOrNull { it.id == selectedThreadId }
                    ?.cwd
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val currentRoot = currentProjectPath
                    .takeUnless { it == "Project path not resolved." }
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val cwds = listOfNotNull(selectedThreadRoot, currentRoot).distinct()
                val loadedSkills = runCatching {
                    service.listSkills(cwds = cwds, forceReload = false, limit = 8)
                }.getOrDefault(emptyList())
                skillSuggestions = loadedSkills
                fileSuggestions = emptyList()
            }

            else -> {
                fileSuggestions = emptyList()
                skillSuggestions = emptyList()
            }
        }
    }

    LaunchedEffect(selectedThreadId, timeline.size, queuedDrafts.size, queuePaused) {
        val threadId = selectedThreadId
        if (threadId.isNullOrBlank()) {
            return@LaunchedEffect
        }
        if (queuedDrafts.isEmpty()) {
            return@LaunchedEffect
        }
        if (queuePaused) {
            return@LaunchedEffect
        }
        if (service.isThreadRunning(threadId)) {
            return@LaunchedEffect
        }
        val nextDraft = queuedDrafts.firstOrNull() ?: return@LaunchedEffect
        runCatching {
            service.sendTurnStart(
                inputText = nextDraft.text,
                attachments = nextDraft.attachments,
                skillMentions = nextDraft.skillMentions,
                fileMentions = nextDraft.fileMentions
            )
        }.onSuccess {
            queuedDrafts.removeAt(0)
        }
    }

    val pageGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.background
        )
    )
    val hasActiveThread = threads.any { it.id == selectedThreadId && !it.isArchived }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                SidebarDrawerContent(
                    threads = threads,
                    selectedThreadId = selectedThreadId,
                    currentProjectPath = currentProjectPath,
                    onOpenThread = { threadId ->
                        scope.launch {
                            runCatching { service.openThread(threadId) }
                            runCatching { drawerState.close() }
                        }
                    },
                    onStartThread = { projectPath ->
                        scope.launch {
                            runCatching { service.startThread(preferredProjectPath = projectPath) }
                            runCatching { drawerState.close() }
                        }
                    },
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
                    onRenameThread = { threadId, name ->
                        scope.launch {
                            runCatching { service.renameThread(threadId, name) }
                        }
                    },
                    onArchiveThread = { threadId ->
                        scope.launch {
                            runCatching { service.archiveThread(threadId) }
                        }
                    },
                    onUnarchiveThread = { threadId ->
                        scope.launch {
                            runCatching { service.unarchiveThread(threadId) }
                        }
                    },
                    onDeleteThreadLocally = { threadId ->
                        scope.launch {
                            runCatching { service.deleteThreadLocally(threadId) }
                        }
                    },
                    onArchiveProjectGroup = { threadIds ->
                        scope.launch {
                            runCatching { service.archiveThreadGroup(threadIds) }
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
                            text = "Workspace",
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
                            subtitle = "Current project session",
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

                    if (!hasActiveThread) {
                        item {
                            SectionCard(
                                title = "No Active Chat",
                                subtitle = "Select a chat from sidebar or start a new project-scoped chat."
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val preferredPath = currentProjectPath
                                                .takeUnless { it == "Project path not resolved." }
                                            scope.launch {
                                                runCatching { service.startThread(preferredProjectPath = preferredPath) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("New Chat") }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { drawerState.open() }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Open Sidebar") }
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
                        }
                    } else {
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
                                title = "Conversation",
                                subtitle = "Composer and send controls for the selected chat."
                            ) {
                                OutlinedTextField(
                                    value = composerInput,
                                    onValueChange = onComposerInputChange,
                                    label = { Text("Ask anything... @files, \$skills, /commands") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            OutlinedTextField(
                                value = voiceDraftText,
                                onValueChange = { voiceDraftText = it },
                                label = { Text("Voice transcript draft (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { galleryPicker.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gallery")
                                }
                                OutlinedButton(
                                    onClick = { cameraPreviewLauncher.launch(null) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Camera")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val line = voiceDraftText.trim()
                                        if (line.isNotEmpty()) {
                                            onComposerInputChange(
                                                listOf(composerInput.trim(), line)
                                                    .filter { it.isNotEmpty() }
                                                    .joinToString("\n")
                                            )
                                            voiceDraftText = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use Voice")
                                }
                            }
                            if (mediaAttachments.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    mediaAttachments.forEach { attachment ->
                                        OutlinedButton(
                                            onClick = {
                                                mediaAttachments.remove(attachment)
                                                if (mediaAttachments.size < MAX_COMPOSER_ATTACHMENTS) {
                                                    attachmentHint = null
                                                }
                                            }
                                        ) {
                                            Text(attachment.label ?: "image")
                                        }
                                    }
                                }
                            }
                            if (attachmentHint != null) {
                                Text(
                                    text = attachmentHint ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (mentionedFiles.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    mentionedFiles.forEach { mention ->
                                        OutlinedButton(
                                            onClick = { mentionedFiles.remove(mention) }
                                        ) {
                                            Text("@${mention.substringAfterLast('/')} ×")
                                        }
                                    }
                                }
                            }
                            if (mentionedSkills.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    mentionedSkills.forEach { skill ->
                                        OutlinedButton(
                                            onClick = { mentionedSkills.remove(skill) }
                                        ) {
                                            Text("\$${skill.name} ×")
                                        }
                                    }
                                }
                            }

                            when (val token = activeComposerToken) {
                                is ComposerAutocompleteToken.File -> {
                                    if (fileSuggestions.isEmpty()) {
                                        Text(
                                            text = "No files found for @${token.query}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            fileSuggestions.forEach { match ->
                                                OutlinedButton(
                                                    onClick = {
                                                        onComposerInputChange(
                                                            applyComposerAutocompleteSelection(
                                                                originalInput = composerInput,
                                                                token = token,
                                                                replacement = "@${match.fileName}"
                                                            )
                                                        )
                                                        if (!mentionedFiles.contains(match.path)) {
                                                            mentionedFiles.add(match.path)
                                                        }
                                                    }
                                                ) {
                                                    Text(match.fileName)
                                                }
                                            }
                                        }
                                    }
                                }

                                is ComposerAutocompleteToken.Skill -> {
                                    if (skillSuggestions.isEmpty()) {
                                        Text(
                                            text = "No skills found for \$${token.query}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            skillSuggestions.forEach { skill ->
                                                OutlinedButton(
                                                    onClick = {
                                                        onComposerInputChange(
                                                            applyComposerAutocompleteSelection(
                                                                originalInput = composerInput,
                                                                token = token,
                                                                replacement = "\$${skill.name}"
                                                            )
                                                        )
                                                        if (mentionedSkills.none { it.id == skill.id }) {
                                                            mentionedSkills.add(skill)
                                                        }
                                                    }
                                                ) {
                                                    Text(skill.name)
                                                }
                                            }
                                        }
                                    }
                                }

                                is ComposerAutocompleteToken.Command -> {
                                    if (commandSuggestions.isEmpty()) {
                                        Text(
                                            text = "No commands found for /${token.query}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            commandSuggestions.forEach { command ->
                                                OutlinedButton(
                                                    onClick = {
                                                        when (command.token) {
                                                            "/status" -> {
                                                                scope.launch {
                                                                    runCatching { service.forceRefreshWorkspace() }
                                                                    runCatching { service.refreshThreads(includeTimeline = false) }
                                                                    runCatching { service.refreshActiveThreadTimeline() }
                                                                    runCatching { service.refreshGitStatus() }
                                                                }
                                                            }

                                                            "/new" -> {
                                                                scope.launch {
                                                                    runCatching {
                                                                        service.startThread(
                                                                            preferredProjectPath = currentProjectPath.takeUnless {
                                                                                it == "Project path not resolved."
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            "/refresh" -> {
                                                                scope.launch { runCatching { service.forceRefreshWorkspace() } }
                                                            }

                                                            else -> {
                                                                onComposerInputChange("Help: use @files, \$skills, /status, /new, /refresh.")
                                                            }
                                                        }
                                                        fileSuggestions = emptyList()
                                                        skillSuggestions = emptyList()
                                                    }
                                                ) {
                                                    Text(command.token)
                                                }
                                            }
                                        }
                                    }
                                }

                                null -> Unit
                            }

                            if (queuedDrafts.isNotEmpty()) {
                                Text(
                                    text = "Queued drafts: ${queuedDrafts.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { queuePaused = !queuePaused },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (queuePaused) "Resume Queue" else "Pause Queue")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val latest = queuedDrafts.removeLastOrNull() ?: return@OutlinedButton
                                            onComposerInputChange(latest.text)
                                            mentionedFiles.clear()
                                            mentionedFiles.addAll(latest.fileMentions)
                                            mentionedSkills.clear()
                                            mentionedSkills.addAll(latest.skillMentions)
                                            mediaAttachments.clear()
                                            mediaAttachments.addAll(latest.attachments)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Restore Latest")
                                    }
                                    OutlinedButton(
                                        onClick = { queuedDrafts.clear() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Clear Queue")
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val trimmed = composerInput.trim()
                                        if (trimmed.isEmpty() && mediaAttachments.isEmpty()) {
                                            return@Button
                                        }
                                        if (selectedThreadId != null && service.isThreadRunning(selectedThreadId)) {
                                            queuedDrafts.add(
                                                QueuedComposerDraft(
                                                    text = trimmed,
                                                    fileMentions = mentionedFiles.toList(),
                                                    skillMentions = mentionedSkills.toList(),
                                                    attachments = mediaAttachments.toList()
                                                )
                                            )
                                            onComposerInputChange("")
                                            mediaAttachments.clear()
                                            voiceDraftText = ""
                                            mentionedFiles.clear()
                                            mentionedSkills.clear()
                                            return@Button
                                        }
                                        scope.launch {
                                            runCatching {
                                                service.sendTurnStart(
                                                    inputText = trimmed,
                                                    attachments = mediaAttachments.toList(),
                                                    skillMentions = mentionedSkills.toList(),
                                                    fileMentions = mentionedFiles.toList()
                                                )
                                            }
                                                .onSuccess {
                                                    onComposerInputChange("")
                                                    mentionedFiles.clear()
                                                    mentionedSkills.clear()
                                                    mediaAttachments.clear()
                                                    attachmentHint = null
                                                    voiceDraftText = ""
                                                }
                                        }
                                    },
                                    enabled = (composerInput.isNotBlank() || mediaAttachments.isNotEmpty()) && selectedThreadId != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Send") }
                                OutlinedButton(
                                    onClick = { scope.launch { runCatching { service.interruptActiveTurn() } } },
                                    enabled = selectedThreadId != null && service.isThreadRunning(selectedThreadId),
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
                    }

                }
            }
        }
    }
}

private data class QueuedComposerDraft(
    val text: String,
    val fileMentions: List<String>,
    val skillMentions: List<SkillSuggestion>,
    val attachments: List<TurnImageAttachment>
)

private fun Bitmap.toJpegDataUrl(quality: Int = 85): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 100), output)
    val bytes = output.toByteArray()
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}
