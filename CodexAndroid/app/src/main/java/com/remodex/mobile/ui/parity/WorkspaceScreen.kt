package com.remodex.mobile.ui.parity

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
    onOpenSettings: () -> Unit,
    onOpenPairing: () -> Unit,
    onHeaderTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isConnected = connectionState == ConnectionState.Connected
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId && !it.isArchived }
    val trustedPairing = service.currentPairing()
    val normalizedProjectPath = currentProjectPath
        .takeUnless { it == "Project path not resolved." }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
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
                val roots = listOfNotNull(selectedThreadRoot, normalizedProjectPath).distinct()
                fileSuggestions = runCatching {
                    service.fuzzyFileSearch(query = token.query, roots = roots, limit = 8)
                }.getOrDefault(emptyList())
                skillSuggestions = emptyList()
            }

            is ComposerAutocompleteToken.Skill -> {
                val selectedThreadRoot = threads
                    .firstOrNull { it.id == selectedThreadId }
                    ?.cwd
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val roots = listOfNotNull(selectedThreadRoot, normalizedProjectPath).distinct()
                skillSuggestions = runCatching {
                    service.listSkills(cwds = roots, forceReload = false, limit = 8)
                }.getOrDefault(emptyList())
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
        if (threadId.isNullOrBlank() || queuedDrafts.isEmpty() || queuePaused || service.isThreadRunning(threadId)) {
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
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.background
        )
    )

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
                    onOpenSettings = {
                        scope.launch { runCatching { drawerState.close() } }
                        onOpenSettings()
                    },
                    onGitPull = {
                        scope.launch { runCatching { service.gitPull() } }
                    },
                    onGitPush = {
                        scope.launch { runCatching { service.gitPush() } }
                    },
                    onRenameThread = { threadId, name ->
                        scope.launch { runCatching { service.renameThread(threadId, name) } }
                    },
                    onArchiveThread = { threadId ->
                        scope.launch { runCatching { service.archiveThread(threadId) } }
                    },
                    onUnarchiveThread = { threadId ->
                        scope.launch { runCatching { service.unarchiveThread(threadId) } }
                    },
                    onDeleteThreadLocally = { threadId ->
                        scope.launch { runCatching { service.deleteThreadLocally(threadId) } }
                    },
                    onArchiveProjectGroup = { threadIds ->
                        scope.launch { runCatching { service.archiveThreadGroup(threadIds) } }
                    },
                    onDisconnect = {
                        scope.launch { runCatching { service.disconnect() } }
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
            Column(modifier = Modifier.fillMaxSize()) {
                WorkspaceTopBar(
                    status = status,
                    selectedThreadTitle = selectedThread?.displayTitle,
                    selectedModel = selectedModel,
                    gitStatusSummary = gitStatusSummary,
                    hasPendingPermissions = pendingPermissions.isNotEmpty(),
                    onMenu = {
                        scope.launch { drawerState.open() }
                    },
                    onRefresh = {
                        scope.launch { runCatching { service.forceRefreshWorkspace() } }
                    },
                    onTap = onHeaderTap
                )

                CompactRefreshStrip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                                        scope.launch { runCatching { service.forceRefreshWorkspace() } }
                                    }
                                    refreshGestureDelta = 0f
                                },
                                onDragCancel = {
                                    refreshGestureDelta = 0f
                                }
                            )
                        },
                    progress = refreshGestureDelta
                )

                if (selectedThread == null) {
                    EmptyWorkspaceHome(
                        connectionState = connectionState,
                        status = status,
                        trustedPairLabel = trustedPairing?.let { pairing ->
                            "Trusted Mac: ${pairing.macDeviceId} · ${pairing.relayUrl}"
                        },
                        projectPath = normalizedProjectPath,
                        rateLimitInfo = rateLimitInfo,
                        ciStatus = ciStatus,
                        onOpenSidebar = {
                            scope.launch { drawerState.open() }
                        },
                        onOpenPairing = onOpenPairing,
                        onReconnect = {
                            scope.launch {
                                runCatching { service.connectLive() }
                            }
                        },
                        onForgetPair = { service.forgetPairing() },
                        onStartThread = {
                            scope.launch {
                                runCatching { service.startThread(preferredProjectPath = normalizedProjectPath) }
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 276.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (pendingPermissions.isNotEmpty()) {
                                item {
                                    InlineStatusCard(
                                        title = "Permission required",
                                        body = "${pendingPermissions.size} action${if (pendingPermissions.size == 1) "" else "s"} waiting for approval.",
                                        accent = Color(0xFFE6A23C)
                                    )
                                }
                                items(pendingPermissions, key = { it.id }) { request ->
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

                            item {
                                ConversationMetaRow(
                                    projectPath = normalizedProjectPath ?: "No repo bound yet",
                                    gitStatusSummary = gitStatusSummary,
                                    rateLimitInfo = rateLimitInfo,
                                    ciStatus = ciStatus,
                                    branches = gitBranches,
                                    selectedBranch = checkoutBranch,
                                    onBranchSelected = onCheckoutBranchChange,
                                    onPull = { scope.launch { runCatching { service.gitPull() } } },
                                    onPush = { scope.launch { runCatching { service.gitPush() } } }
                                )
                            }

                            if (timeline.isEmpty()) {
                                item {
                                    InlineStatusCard(
                                        title = "Conversation",
                                        body = "This chat is ready. Start with a prompt, mention files, or attach an image.",
                                        accent = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                items(timeline, key = { it.id }) { entry ->
                                    TimelineRow(item = entry)
                                }
                            }
                        }

                        ComposerDock(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            selectedModel = selectedModel,
                            availableModels = availableModels,
                            selectedThreadId = selectedThreadId,
                            composerInput = composerInput,
                            onComposerInputChange = onComposerInputChange,
                            voiceDraftText = voiceDraftText,
                            onVoiceDraftTextChange = { voiceDraftText = it },
                            mediaAttachments = mediaAttachments,
                            attachmentHint = attachmentHint,
                            mentionedFiles = mentionedFiles,
                            mentionedSkills = mentionedSkills,
                            activeComposerToken = activeComposerToken,
                            fileSuggestions = fileSuggestions,
                            skillSuggestions = skillSuggestions,
                            commandSuggestions = commandSuggestions,
                            queuedDrafts = queuedDrafts,
                            queuePaused = queuePaused,
                            isRunning = service.isThreadRunning(selectedThreadId),
                            onQueuePausedChange = { queuePaused = it },
                            onAttachGallery = { galleryPicker.launch("image/*") },
                            onAttachCamera = { cameraPreviewLauncher.launch(null) },
                            onUseVoiceDraft = {
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
                            onSwitchModel = { model ->
                                scope.launch { runCatching { service.switchModel(model) } }
                            },
                            onRemoveAttachment = { attachment ->
                                mediaAttachments.remove(attachment)
                                if (mediaAttachments.size < MAX_COMPOSER_ATTACHMENTS) {
                                    attachmentHint = null
                                }
                            },
                            onRemoveMentionedFile = { mention -> mentionedFiles.remove(mention) },
                            onRemoveMentionedSkill = { skill -> mentionedSkills.remove(skill) },
                            onSelectFileSuggestion = { token, match ->
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
                            },
                            onSelectSkillSuggestion = { token, skill ->
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
                            },
                            onSelectCommandSuggestion = { command ->
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
                                            runCatching { service.startThread(preferredProjectPath = normalizedProjectPath) }
                                        }
                                    }

                                    "/refresh" -> {
                                        scope.launch { runCatching { service.forceRefreshWorkspace() } }
                                    }

                                    "/resume" -> {
                                        scope.launch {
                                            runCatching { service.threadResume(selectedThreadId) }
                                        }
                                    }

                                    "/fork" -> {
                                        scope.launch {
                                            runCatching { service.threadFork(selectedThreadId) }
                                        }
                                    }

                                    "/review" -> {
                                        scope.launch {
                                            runCatching { service.reviewStart(selectedThreadId) }
                                        }
                                    }

                                    "/subagents" -> {
                                        onComposerInputChange("/subagents ")
                                    }

                                    "/steer" -> {
                                        val steerInput = composerInput
                                            .removePrefix("/steer")
                                            .trim()
                                        if (steerInput.isNotEmpty()) {
                                            scope.launch {
                                                runCatching { service.turnSteer(steerInput) }
                                            }
                                            onComposerInputChange("")
                                        } else {
                                            onComposerInputChange("/steer ")
                                        }
                                    }

                                    else -> {
                                        onComposerInputChange(
                                            "Help: use @files, \$skills, /status, /new, /refresh, /resume, /fork, /review, /subagents, /steer."
                                        )
                                    }
                                }
                                fileSuggestions = emptyList()
                                skillSuggestions = emptyList()
                            },
                            onRestoreLatest = {
                                val latest = queuedDrafts.removeLastOrNull() ?: return@ComposerDock
                                onComposerInputChange(latest.text)
                                mentionedFiles.clear()
                                mentionedFiles.addAll(latest.fileMentions)
                                mentionedSkills.clear()
                                mentionedSkills.addAll(latest.skillMentions)
                                mediaAttachments.clear()
                                mediaAttachments.addAll(latest.attachments)
                            },
                            onClearQueue = { queuedDrafts.clear() },
                            onSend = {
                                val trimmed = composerInput.trim()
                                if (trimmed.isEmpty() && mediaAttachments.isEmpty()) {
                                    return@ComposerDock
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
                                    attachmentHint = null
                                    return@ComposerDock
                                }
                                scope.launch {
                                    runCatching {
                                        service.sendTurnStart(
                                            inputText = trimmed,
                                            attachments = mediaAttachments.toList(),
                                            skillMentions = mentionedSkills.toList(),
                                            fileMentions = mentionedFiles.toList()
                                        )
                                    }.onSuccess {
                                        onComposerInputChange("")
                                        mentionedFiles.clear()
                                        mentionedSkills.clear()
                                        mediaAttachments.clear()
                                        attachmentHint = null
                                        voiceDraftText = ""
                                    }
                                }
                            },
                            onStop = {
                                scope.launch { runCatching { service.interruptActiveTurn() } }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTopBar(
    status: String,
    selectedThreadTitle: String?,
    selectedModel: String,
    gitStatusSummary: String,
    hasPendingPermissions: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onTap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactToolbarButton(label = "Menu", onClick = onMenu)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onTap)
                ) {
                    Text(
                        text = selectedThreadTitle ?: "Remodex",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedThreadTitle == null) status else gitStatusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CompactToolbarButton(label = "Sync", onClick = onRefresh)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(text = selectedModel)
                if (hasPendingPermissions) {
                    StatusPill(text = "Needs approval", accent = Color(0xFFE6A23C))
                }
            }
        }
    }
}

@Composable
private fun CompactRefreshStrip(
    modifier: Modifier = Modifier,
    progress: Float
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (progress > 180f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            )
            Text(
                text = if (progress > 180f) "Release to refresh workspace" else "Pull down here to refresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyWorkspaceHome(
    connectionState: ConnectionState,
    status: String,
    trustedPairLabel: String?,
    projectPath: String?,
    rateLimitInfo: String,
    ciStatus: String,
    onOpenSidebar: () -> Unit,
    onOpenPairing: () -> Unit,
    onReconnect: () -> Unit,
    onForgetPair: () -> Unit,
    onStartThread: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        EmptyHomeCard(
            connectionState = connectionState,
            status = status,
            trustedPairLabel = trustedPairLabel,
            projectPath = projectPath,
            rateLimitInfo = rateLimitInfo,
            ciStatus = ciStatus,
            primaryActionLabel = when (connectionState) {
                ConnectionState.Connected -> "New Chat"
                ConnectionState.Connecting -> "Reconnecting..."
                ConnectionState.Paired -> "Reconnect"
                ConnectionState.Disconnected -> "Reconnect"
                is ConnectionState.Failed -> "Reconnect"
            },
            onPrimaryAction = when (connectionState) {
                ConnectionState.Connected -> onStartThread
                else -> onReconnect
            },
            secondaryActionLabel = when (connectionState) {
                ConnectionState.Connected -> "Sidebar"
                else -> "Scan QR"
            },
            onSecondaryAction = when (connectionState) {
                ConnectionState.Connected -> onOpenSidebar
                else -> onOpenPairing
            },
            onForgetPair = if (connectionState == ConnectionState.Connected) {
                null
            } else {
                onForgetPair
            }
        )
    }
}

@Composable
private fun ConversationMetaRow(
    projectPath: String,
    gitStatusSummary: String,
    rateLimitInfo: String,
    ciStatus: String,
    branches: List<String>,
    selectedBranch: String,
    onBranchSelected: (String) -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = projectPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = gitStatusSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = rateLimitInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ciStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (branches.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    branches.forEach { branch ->
                        SmallChip(
                            text = branch,
                            selected = branch == selectedBranch,
                            onClick = { onBranchSelected(branch) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPull, modifier = Modifier.weight(1f)) {
                    Text("Pull")
                }
                OutlinedButton(onClick = onPush, modifier = Modifier.weight(1f)) {
                    Text("Push")
                }
            }
        }
    }
}

@Composable
private fun ComposerDock(
    modifier: Modifier,
    selectedModel: String,
    availableModels: List<String>,
    selectedThreadId: String?,
    composerInput: String,
    onComposerInputChange: (String) -> Unit,
    voiceDraftText: String,
    onVoiceDraftTextChange: (String) -> Unit,
    mediaAttachments: List<TurnImageAttachment>,
    attachmentHint: String?,
    mentionedFiles: List<String>,
    mentionedSkills: List<SkillSuggestion>,
    activeComposerToken: ComposerAutocompleteToken?,
    fileSuggestions: List<FileAutocompleteMatch>,
    skillSuggestions: List<SkillSuggestion>,
    commandSuggestions: List<ComposerCommand>,
    queuedDrafts: List<QueuedComposerDraft>,
    queuePaused: Boolean,
    isRunning: Boolean,
    onQueuePausedChange: (Boolean) -> Unit,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onUseVoiceDraft: () -> Unit,
    onSwitchModel: (String) -> Unit,
    onRemoveAttachment: (TurnImageAttachment) -> Unit,
    onRemoveMentionedFile: (String) -> Unit,
    onRemoveMentionedSkill: (SkillSuggestion) -> Unit,
    onSelectFileSuggestion: (ComposerAutocompleteToken.File, FileAutocompleteMatch) -> Unit,
    onSelectSkillSuggestion: (ComposerAutocompleteToken.Skill, SkillSuggestion) -> Unit,
    onSelectCommandSuggestion: (ComposerCommand) -> Unit,
    onRestoreLatest: () -> Unit,
    onClearQueue: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = modifier.navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (queuedDrafts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queued ${queuedDrafts.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    SmallChip(
                        text = if (queuePaused) "Resume" else "Pause",
                        selected = false,
                        onClick = { onQueuePausedChange(!queuePaused) }
                    )
                    SmallChip(text = "Restore", selected = false, onClick = onRestoreLatest)
                    SmallChip(text = "Clear", selected = false, onClick = onClearQueue)
                }
            }

            if (mediaAttachments.isNotEmpty() || mentionedFiles.isNotEmpty() || mentionedSkills.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaAttachments.forEach { attachment ->
                        SmallChip(
                            text = attachment.label ?: "image",
                            selected = true,
                            onClick = { onRemoveAttachment(attachment) }
                        )
                    }
                    mentionedFiles.forEach { mention ->
                        SmallChip(
                            text = "@${mention.substringAfterLast('/')}",
                            selected = true,
                            onClick = { onRemoveMentionedFile(mention) }
                        )
                    }
                    mentionedSkills.forEach { skill ->
                        SmallChip(
                            text = "\$${skill.name}",
                            selected = true,
                            onClick = { onRemoveMentionedSkill(skill) }
                        )
                    }
                }
            }

            if (!attachmentHint.isNullOrBlank()) {
                Text(
                    text = attachmentHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            when (val token = activeComposerToken) {
                is ComposerAutocompleteToken.File -> {
                    if (fileSuggestions.isNotEmpty()) {
                        SuggestionTray(
                            labels = fileSuggestions.map { it.fileName },
                            onSelected = { index -> onSelectFileSuggestion(token, fileSuggestions[index]) }
                        )
                    }
                }

                is ComposerAutocompleteToken.Skill -> {
                    if (skillSuggestions.isNotEmpty()) {
                        SuggestionTray(
                            labels = skillSuggestions.map { "\$${it.name}" },
                            onSelected = { index -> onSelectSkillSuggestion(token, skillSuggestions[index]) }
                        )
                    }
                }

                is ComposerAutocompleteToken.Command -> {
                    if (commandSuggestions.isNotEmpty()) {
                        SuggestionTray(
                            labels = commandSuggestions.map { it.token },
                            onSelected = { index -> onSelectCommandSuggestion(commandSuggestions[index]) }
                        )
                    }
                }

                null -> Unit
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = composerInput,
                        onValueChange = onComposerInputChange,
                        label = { Text("Ask anything... @files, \$skills, /commands") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (voiceDraftText.isNotBlank()) {
                        OutlinedTextField(
                            value = voiceDraftText,
                            onValueChange = onVoiceDraftTextChange,
                            label = { Text("Voice draft") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableModels.forEach { model ->
                            SmallChip(
                                text = model,
                                selected = model == selectedModel,
                                onClick = { onSwitchModel(model) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactToolbarButton(label = "Gallery", onClick = onAttachGallery, modifier = Modifier.weight(1f))
                        CompactToolbarButton(label = "Camera", onClick = onAttachCamera, modifier = Modifier.weight(1f))
                        CompactToolbarButton(label = "Voice", onClick = onUseVoiceDraft, modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSend,
                    enabled = (composerInput.isNotBlank() || mediaAttachments.isNotEmpty()) && selectedThreadId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRunning) "Queue" else "Send")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = selectedThreadId != null && isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun SuggestionTray(
    labels: List<String>,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            SmallChip(text = label, selected = false, onClick = { onSelected(index) })
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
