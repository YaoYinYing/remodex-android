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
import com.remodex.mobile.service.ReviewTarget
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
private const val SUBAGENTS_CANNED_PROMPT =
    "Run subagents for different tasks. Delegate distinct work in parallel when helpful and then synthesize the results."

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
    val commandSuggestions = remember(activeComposerToken, selectedThreadId) {
        when (val token = activeComposerToken) {
            is ComposerAutocompleteToken.Command -> filterComposerCommands(
                query = token.query,
                includeFork = !selectedThreadId.isNullOrBlank()
            )
            else -> emptyList()
        }
    }
    var queuePaused by rememberSaveable { mutableStateOf(false) }
    var subagentsArmed by rememberSaveable { mutableStateOf(false) }
    var armedReviewTarget by rememberSaveable { mutableStateOf<ReviewTarget?>(null) }
    var showReviewTargetSuggestions by rememberSaveable { mutableStateOf(false) }
    var showForkDestinationSuggestions by rememberSaveable { mutableStateOf(false) }
    var steeringQueuedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val clearComposerAfterDispatch: () -> Unit = {
        onComposerInputChange("")
        mediaAttachments.clear()
        voiceDraftText = ""
        mentionedFiles.clear()
        mentionedSkills.clear()
        subagentsArmed = false
        showReviewTargetSuggestions = false
        showForkDestinationSuggestions = false
        attachmentHint = null
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
        if (threadId.isNullOrBlank() || queuedDrafts.isEmpty() || queuePaused) {
            return@LaunchedEffect
        }
        val threadBusy = if (service.isThreadRunning(threadId)) {
            runCatching { service.reconcileThreadRunningState(threadId) }
                .getOrElse { true }
        } else {
            false
        }
        if (threadBusy) {
            return@LaunchedEffect
        }
        val nextDraft = queuedDrafts.firstOrNull() ?: return@LaunchedEffect
        runCatching {
            service.sendTurnStart(
                inputText = buildComposerPayloadText(nextDraft.text, nextDraft.subagentsArmed),
                attachments = nextDraft.attachments,
                skillMentions = nextDraft.skillMentions,
                fileMentions = nextDraft.fileMentions
            )
        }.onSuccess {
            queuedDrafts.removeAt(0)
            attachmentHint = null
        }.onFailure { error ->
            queuePaused = true
            attachmentHint = "Queue paused: ${error.message ?: "Failed to send queued draft."}"
        }
    }

    LaunchedEffect(composerInput, mentionedFiles.size, mentionedSkills.size, mediaAttachments.size, subagentsArmed) {
        val hasConflictingDraftContent = composerInput.trim().isNotEmpty()
            || mentionedFiles.isNotEmpty()
            || mentionedSkills.isNotEmpty()
            || mediaAttachments.isNotEmpty()
            || subagentsArmed
        if (armedReviewTarget != null && hasConflictingDraftContent) {
            armedReviewTarget = null
        }
        if (hasConflictingDraftContent) {
            showForkDestinationSuggestions = false
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
                            subagentsArmed = subagentsArmed,
                            armedReviewTarget = armedReviewTarget,
                            showReviewTargetSuggestions = showReviewTargetSuggestions,
                            showForkDestinationSuggestions = showForkDestinationSuggestions,
                            mentionedFiles = mentionedFiles,
                            mentionedSkills = mentionedSkills,
                            activeComposerToken = activeComposerToken,
                            fileSuggestions = fileSuggestions,
                            skillSuggestions = skillSuggestions,
                            commandSuggestions = commandSuggestions,
                            queuedDrafts = queuedDrafts,
                            queuePaused = queuePaused,
                            steeringQueuedDraftId = steeringQueuedDraftId,
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
                            onClearSubagentsArmed = { subagentsArmed = false },
                            onClearReviewTarget = { armedReviewTarget = null },
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
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
                                        scope.launch {
                                            runCatching { service.forceRefreshWorkspace() }
                                            runCatching { service.refreshThreads(includeTimeline = false) }
                                            runCatching { service.refreshActiveThreadTimeline() }
                                            runCatching { service.refreshGitStatus() }
                                        }
                                    }

                                    "/new" -> {
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
                                        scope.launch {
                                            runCatching { service.startThread(preferredProjectPath = normalizedProjectPath) }
                                        }
                                    }

                                    "/refresh" -> {
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
                                        scope.launch { runCatching { service.forceRefreshWorkspace() } }
                                    }

                                    "/resume" -> {
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
                                        scope.launch {
                                            runCatching { service.threadResume(selectedThreadId) }
                                        }
                                    }

                                    "/fork" -> {
                                        showReviewTargetSuggestions = false
                                        if (selectedThreadId != null && service.isThreadRunning(selectedThreadId)) {
                                            showForkDestinationSuggestions = false
                                            attachmentHint = "Wait for the current response to finish before forking."
                                        } else {
                                            attachmentHint = null
                                            onComposerInputChange(stripTrailingSlashCommandToken(composerInput))
                                            showForkDestinationSuggestions = true
                                        }
                                    }

                                    "/review" -> {
                                        onComposerInputChange(stripTrailingSlashCommandToken(composerInput))
                                        showReviewTargetSuggestions = true
                                        showForkDestinationSuggestions = false
                                    }

                                    "/subagents" -> {
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
                                        armedReviewTarget = null
                                        onComposerInputChange(stripTrailingSlashCommandToken(composerInput))
                                        subagentsArmed = true
                                    }

                                    "/steer" -> {
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
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
                                        showReviewTargetSuggestions = false
                                        showForkDestinationSuggestions = false
                                        onComposerInputChange(
                                            "Help: use @files, \$skills, /status, /new, /refresh, /resume, /fork, /review, /subagents, /steer."
                                        )
                                    }
                                }
                                fileSuggestions = emptyList()
                                skillSuggestions = emptyList()
                            },
                            onRestoreQueuedDraft = { draftId ->
                                val index = queuedDrafts.indexOfFirst { it.id == draftId }
                                if (index < 0) {
                                    return@ComposerDock
                                }
                                val restored = queuedDrafts.removeAt(index)
                                onComposerInputChange(restored.text)
                                subagentsArmed = restored.subagentsArmed
                                armedReviewTarget = null
                                showReviewTargetSuggestions = false
                                showForkDestinationSuggestions = false
                                mentionedFiles.clear()
                                mentionedFiles.addAll(restored.fileMentions)
                                mentionedSkills.clear()
                                mentionedSkills.addAll(restored.skillMentions)
                                mediaAttachments.clear()
                                mediaAttachments.addAll(restored.attachments)
                            },
                            onSteerQueuedDraft = { draftId ->
                                val index = queuedDrafts.indexOfFirst { it.id == draftId }
                                if (index < 0 || selectedThreadId.isNullOrBlank()) {
                                    return@ComposerDock
                                }
                                val draft = queuedDrafts[index]
                                steeringQueuedDraftId = draftId
                                scope.launch {
                                    runCatching {
                                        service.turnSteer(buildComposerPayloadText(draft.text, draft.subagentsArmed))
                                    }.onSuccess {
                                        queuedDrafts.removeAll { it.id == draftId }
                                        attachmentHint = null
                                    }.onFailure {
                                        attachmentHint = it.message ?: "Failed to steer queued draft."
                                    }.also {
                                        steeringQueuedDraftId = null
                                    }
                                }
                            },
                            onRemoveQueuedDraft = { draftId ->
                                queuedDrafts.removeAll { it.id == draftId }
                                if (steeringQueuedDraftId == draftId) {
                                    steeringQueuedDraftId = null
                                }
                            },
                            onClearQueue = {
                                queuedDrafts.clear()
                                steeringQueuedDraftId = null
                            },
                            onSelectReviewTargetSuggestion = { target ->
                                val hasConflictingDraftContent = composerInput.trim().isNotEmpty()
                                    || mentionedFiles.isNotEmpty()
                                    || mentionedSkills.isNotEmpty()
                                    || mediaAttachments.isNotEmpty()
                                    || subagentsArmed
                                if (hasConflictingDraftContent) {
                                    attachmentHint = "Review mode requires an empty draft with no mentions, attachments, or /subagents."
                                    showReviewTargetSuggestions = false
                                    showForkDestinationSuggestions = false
                                } else {
                                    attachmentHint = null
                                    armedReviewTarget = target
                                    showReviewTargetSuggestions = false
                                    showForkDestinationSuggestions = false
                                    onComposerInputChange(stripTrailingSlashCommandToken(composerInput))
                                }
                            },
                            onDismissReviewTargetSuggestions = {
                                showReviewTargetSuggestions = false
                                showForkDestinationSuggestions = false
                            },
                            onSelectForkDestinationLocal = {
                                showForkDestinationSuggestions = false
                                scope.launch {
                                    runCatching { service.threadFork(selectedThreadId) }
                                }
                            },
                            onSelectForkDestinationNewWorktree = {
                                showForkDestinationSuggestions = false
                                val normalizedBaseBranch = checkoutBranch.trim()
                                if (normalizedBaseBranch.isEmpty()) {
                                    attachmentHint = "Pick a base branch before creating a worktree fork."
                                    return@ComposerDock
                                }
                                val generatedBranch = "android-fork-${System.currentTimeMillis().toString(16)}"
                                scope.launch {
                                    runCatching {
                                        val worktreePath = service.gitCreateWorktree(
                                            name = generatedBranch,
                                            baseBranch = normalizedBaseBranch,
                                            changeTransfer = "copy"
                                        )
                                        service.threadFork(
                                            threadId = selectedThreadId,
                                            targetProjectPath = worktreePath
                                        )
                                    }.onSuccess {
                                        attachmentHint = null
                                    }.onFailure {
                                        attachmentHint = it.message ?: "Failed to create worktree fork."
                                    }
                                }
                            },
                            onDismissForkDestinationSuggestions = {
                                showForkDestinationSuggestions = false
                            },
                            onSend = {
                                val selectedReviewTarget = armedReviewTarget
                                val trimmed = composerInput.trim()
                                if (selectedReviewTarget == null && trimmed.isEmpty() && mediaAttachments.isEmpty() && !subagentsArmed) {
                                    return@ComposerDock
                                }
                                scope.launch {
                                    val activeThreadId = selectedThreadId
                                    val threadBusy = if (!activeThreadId.isNullOrBlank() && service.isThreadRunning(activeThreadId)) {
                                        runCatching { service.reconcileThreadRunningState(activeThreadId) }
                                            .getOrElse { true }
                                    } else {
                                        false
                                    }

                                    if (selectedReviewTarget != null) {
                                        if (threadBusy) {
                                            attachmentHint = "Wait for the current response to finish before starting a review."
                                            return@launch
                                        }
                                        runCatching {
                                            service.reviewStart(
                                                threadId = activeThreadId,
                                                target = selectedReviewTarget,
                                                baseBranch = checkoutBranch.trim().takeIf { it.isNotEmpty() }
                                            )
                                        }.onSuccess {
                                            armedReviewTarget = null
                                            clearComposerAfterDispatch()
                                        }.onFailure {
                                            attachmentHint = it.message ?: "Failed to start review."
                                        }
                                        return@launch
                                    }

                                    if (threadBusy) {
                                        queuedDrafts.add(
                                            QueuedComposerDraft(
                                                id = "draft-${System.currentTimeMillis()}-${queuedDrafts.size}",
                                                text = trimmed,
                                                subagentsArmed = subagentsArmed,
                                                fileMentions = mentionedFiles.toList(),
                                                skillMentions = mentionedSkills.toList(),
                                                attachments = mediaAttachments.toList()
                                            )
                                        )
                                        clearComposerAfterDispatch()
                                        return@launch
                                    }

                                    runCatching {
                                        service.sendTurnStart(
                                            inputText = buildComposerPayloadText(trimmed, subagentsArmed),
                                            attachments = mediaAttachments.toList(),
                                            skillMentions = mentionedSkills.toList(),
                                            fileMentions = mentionedFiles.toList()
                                        )
                                    }.onSuccess {
                                        clearComposerAfterDispatch()
                                    }.onFailure {
                                        attachmentHint = it.message ?: "Failed to send message."
                                    }
                                }
                            },
                            onStop = {
                                showReviewTargetSuggestions = false
                                showForkDestinationSuggestions = false
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
    subagentsArmed: Boolean,
    armedReviewTarget: ReviewTarget?,
    showReviewTargetSuggestions: Boolean,
    showForkDestinationSuggestions: Boolean,
    mentionedFiles: List<String>,
    mentionedSkills: List<SkillSuggestion>,
    activeComposerToken: ComposerAutocompleteToken?,
    fileSuggestions: List<FileAutocompleteMatch>,
    skillSuggestions: List<SkillSuggestion>,
    commandSuggestions: List<ComposerCommand>,
    queuedDrafts: List<QueuedComposerDraft>,
    queuePaused: Boolean,
    steeringQueuedDraftId: String?,
    isRunning: Boolean,
    onQueuePausedChange: (Boolean) -> Unit,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onUseVoiceDraft: () -> Unit,
    onSwitchModel: (String) -> Unit,
    onRemoveAttachment: (TurnImageAttachment) -> Unit,
    onClearSubagentsArmed: () -> Unit,
    onClearReviewTarget: () -> Unit,
    onRemoveMentionedFile: (String) -> Unit,
    onRemoveMentionedSkill: (SkillSuggestion) -> Unit,
    onSelectFileSuggestion: (ComposerAutocompleteToken.File, FileAutocompleteMatch) -> Unit,
    onSelectSkillSuggestion: (ComposerAutocompleteToken.Skill, SkillSuggestion) -> Unit,
    onSelectCommandSuggestion: (ComposerCommand) -> Unit,
    onRestoreQueuedDraft: (String) -> Unit,
    onSteerQueuedDraft: (String) -> Unit,
    onRemoveQueuedDraft: (String) -> Unit,
    onClearQueue: () -> Unit,
    onSelectReviewTargetSuggestion: (ReviewTarget) -> Unit,
    onDismissReviewTargetSuggestions: () -> Unit,
    onSelectForkDestinationLocal: () -> Unit,
    onSelectForkDestinationNewWorktree: () -> Unit,
    onDismissForkDestinationSuggestions: () -> Unit,
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                        SmallChip(text = "Clear", selected = false, onClick = onClearQueue)
                    }
                    queuedDrafts.forEach { draft ->
                        QueuedDraftRow(
                            draft = draft,
                            canSteer = isRunning && selectedThreadId != null,
                            canRestore = steeringQueuedDraftId == null,
                            steeringQueuedDraftId = steeringQueuedDraftId,
                            onRestore = { onRestoreQueuedDraft(draft.id) },
                            onSteer = { onSteerQueuedDraft(draft.id) },
                            onRemove = { onRemoveQueuedDraft(draft.id) }
                        )
                    }
                }
            }

            if (
                mediaAttachments.isNotEmpty()
                || mentionedFiles.isNotEmpty()
                || mentionedSkills.isNotEmpty()
                || subagentsArmed
                || armedReviewTarget != null
            ) {
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
                    if (subagentsArmed) {
                        SmallChip(
                            text = "/subagents",
                            selected = true,
                            onClick = onClearSubagentsArmed
                        )
                    }
                    armedReviewTarget?.let { target ->
                        SmallChip(
                            text = reviewTargetChipLabel(target),
                            selected = true,
                            onClick = onClearReviewTarget
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

            if (showForkDestinationSuggestions) {
                SuggestionTray(
                    labels = listOf("Fork into local", "Fork into new worktree", "Cancel"),
                    onSelected = { index ->
                        when (index) {
                            0 -> onSelectForkDestinationLocal()
                            1 -> onSelectForkDestinationNewWorktree()
                            else -> onDismissForkDestinationSuggestions()
                        }
                    }
                )
            } else if (showReviewTargetSuggestions) {
                SuggestionTray(
                    labels = listOf("Uncommitted changes", "Base branch", "Cancel"),
                    onSelected = { index ->
                        when (index) {
                            0 -> onSelectReviewTargetSuggestion(ReviewTarget.UNCOMMITTED_CHANGES)
                            1 -> onSelectReviewTargetSuggestion(ReviewTarget.BASE_BRANCH)
                            else -> onDismissReviewTargetSuggestions()
                        }
                    }
                )
            } else {
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
                    enabled = (
                        composerInput.isNotBlank()
                            || mediaAttachments.isNotEmpty()
                            || subagentsArmed
                            || armedReviewTarget != null
                        ) && selectedThreadId != null,
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

@Composable
private fun QueuedDraftRow(
    draft: QueuedComposerDraft,
    canSteer: Boolean,
    canRestore: Boolean,
    steeringQueuedDraftId: String?,
    onRestore: () -> Unit,
    onSteer: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = draft.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        SmallChip(
            text = "Restore",
            selected = false,
            onClick = if (canRestore) onRestore else ({})
        )
        if (canSteer) {
            SmallChip(
                text = if (steeringQueuedDraftId == draft.id) "Steering..." else "Steer",
                selected = false,
                onClick = if (steeringQueuedDraftId == null) onSteer else ({})
            )
        }
        SmallChip(
            text = "Remove",
            selected = false,
            onClick = if (steeringQueuedDraftId == draft.id) ({}) else onRemove
        )
    }
}

private data class QueuedComposerDraft(
    val id: String,
    val text: String,
    val subagentsArmed: Boolean = false,
    val fileMentions: List<String>,
    val skillMentions: List<SkillSuggestion>,
    val attachments: List<TurnImageAttachment>
)

private fun reviewTargetChipLabel(target: ReviewTarget): String {
    return when (target) {
        ReviewTarget.UNCOMMITTED_CHANGES -> "Review: Uncommitted"
        ReviewTarget.BASE_BRANCH -> "Review: Base branch"
    }
}

private fun stripTrailingSlashCommandToken(input: String): String {
    val token = detectComposerAutocompleteToken(input) as? ComposerAutocompleteToken.Command ?: return input
    if (token.endIndexExclusive != input.length) {
        return input
    }
    return input.substring(0, token.startIndex).trimEnd()
}

private fun buildComposerPayloadText(
    text: String,
    subagentsArmed: Boolean
): String {
    val normalized = text.trim()
    if (!subagentsArmed) {
        return normalized
    }
    return if (normalized.isEmpty()) {
        SUBAGENTS_CANNED_PROMPT
    } else {
        "$SUBAGENTS_CANNED_PROMPT\n\n$normalized"
    }
}

private fun Bitmap.toJpegDataUrl(quality: Int = 85): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 100), output)
    val bytes = output.toByteArray()
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}
