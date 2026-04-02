package com.remodex.mobile.ui.parity

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.FileAutocompleteMatch
import com.remodex.mobile.service.PendingPermissionRequest
import com.remodex.mobile.service.RecoveryAccessorySnapshot
import com.remodex.mobile.service.RecoveryAccessoryStatus
import com.remodex.mobile.service.ReviewTarget
import com.remodex.mobile.service.SkillSuggestion
import com.remodex.mobile.service.TurnImageAttachment
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
    availableReasoningEfforts: List<String>,
    selectedReasoningEffort: String,
    pendingPermissions: List<PendingPermissionRequest>,
    rateLimitInfo: String,
    ciStatus: String,
    gitStatusSummary: String,
    gitBranches: List<String>,
    checkoutBranch: String,
    onCheckoutBranchChange: (String) -> Unit,
    threads: List<ThreadSummary>,
    selectedThreadId: String?,
    timeline: List<TimelineEntry>,
    composerInput: String,
    onComposerInputChange: (String) -> Unit,
    onSwitchModel: (String) -> Unit,
    onSwitchReasoningEffort: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPairing: () -> Unit,
    onHeaderTap: () -> Unit,
    dockCollapsedSide: String,
    gitActionStatus: String?,
    voiceRecoverySnapshot: RecoveryAccessorySnapshot?,
    onVoiceRecoveryAction: () -> Unit,
    onDismissVoiceRecovery: () -> Unit,
    onTriggerVoiceRecovery: () -> Unit
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
    var isDispatching by rememberSaveable(selectedThreadId) { mutableStateOf(false) }
    var voiceDraftText by rememberSaveable { mutableStateOf("") }
    var showVoiceSetupSheet by rememberSaveable { mutableStateOf(false) }
    var showGitActionsMenu by rememberSaveable { mutableStateOf(false) }
    var showRepositoryDiffDialog by rememberSaveable { mutableStateOf(false) }
    var repositoryDiffPatch by rememberSaveable { mutableStateOf("") }
    var showCommitSheet by rememberSaveable { mutableStateOf(false) }
    var commitMessageDraft by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val timelineListState = rememberLazyListState()
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
    val userMessageIndexes = remember(timeline) {
        timeline.mapIndexedNotNull { index, item ->
            index.takeIf { item.role == com.remodex.mobile.model.TimelineRole.USER }
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

    LaunchedEffect(selectedThreadId, timeline.size) {
        if (timeline.isNotEmpty()) {
            timelineListState.animateScrollToItem(timeline.lastIndex)
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
                    autoRefreshEnabled = autoRefreshEnabled,
                    onAutoRefreshChanged = { autoRefreshEnabled = it },
                    onRefreshWorkspace = {
                        scope.launch {
                            runCatching { service.forceRefreshWorkspace() }
                        }
                    },
                    onOpenSettings = {
                        scope.launch { runCatching { drawerState.close() } }
                        onOpenSettings()
                    },
                    onGitDiff = {
                        scope.launch {
                            runCatching { service.gitDiff() }
                                .onSuccess { patch ->
                                    repositoryDiffPatch = patch
                                    showRepositoryDiffDialog = true
                                }
                        }
                    },
                    onGitCommit = {
                        showCommitSheet = true
                    },
                    onGitCommitAndPush = {
                        commitMessageDraft = ""
                        showCommitSheet = true
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
                        onOpenPairing()
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
                    currentProjectPath = normalizedProjectPath,
                    gitStatusSummary = gitStatusSummary,
                    checkoutBranch = checkoutBranch,
                    hasPendingPermissions = pendingPermissions.isNotEmpty(),
                    onMenu = {
                        scope.launch { drawerState.open() }
                    },
                    onRefresh = {
                        scope.launch { runCatching { service.forceRefreshWorkspace() } }
                    },
                    onShowRepositoryDiff = {
                        scope.launch {
                            runCatching { service.gitDiff() }
                                .onSuccess { patch ->
                                    repositoryDiffPatch = patch
                                    showRepositoryDiffDialog = true
                                }
                        }
                    },
                    onOpenGitActions = { showGitActionsMenu = true },
                    onTap = onHeaderTap
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
                            state = timelineListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 276.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            if (pendingPermissions.isNotEmpty()) {
                                item {
                                    InlineStatusCard(
                                        title = "Approval request",
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
                                    gitActionStatus = gitActionStatus,
                                    rateLimitInfo = rateLimitInfo,
                                    ciStatus = ciStatus,
                                    branches = gitBranches,
                                    selectedBranch = checkoutBranch,
                                    onBranchSelected = onCheckoutBranchChange,
                                    onPull = { scope.launch { runCatching { service.gitPull() } } },
                                    onPush = { scope.launch { runCatching { service.gitPush() } } }
                                )
                            }

                            if (voiceRecoverySnapshot != null) {
                                item {
                                    RecoveryAccessoryCard(
                                        snapshot = voiceRecoverySnapshot,
                                        onAction = {
                                            if (voiceRecoverySnapshot.actionLabel == "How To Fix") {
                                                showVoiceSetupSheet = true
                                            } else {
                                                onVoiceRecoveryAction()
                                            }
                                        },
                                        onDismiss = onDismissVoiceRecovery
                                    )
                                }
                            }

                            if (timeline.isEmpty() && voiceRecoverySnapshot == null) {
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
                            selectedReasoningEffort = selectedReasoningEffort,
                            availableReasoningEfforts = availableReasoningEfforts,
                            selectedBranch = checkoutBranch,
                            rateLimitInfo = rateLimitInfo,
                            ciStatus = ciStatus,
                            selectedThreadId = selectedThreadId,
                            composerInput = composerInput,
                            onComposerInputChange = onComposerInputChange,
                            dockCollapsedSide = dockCollapsedSide,
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
                            isDispatching = isDispatching,
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
                                } else {
                                    onTriggerVoiceRecovery()
                                }
                            },
                            onCheckRateLimits = {
                                scope.launch {
                                    runCatching {
                                        service.refreshRateLimitInfo(silentStatus = false)
                                    }.onFailure {
                                        attachmentHint = it.message ?: "Failed to refresh rate limits."
                                    }
                                }
                            },
                            onSwitchModel = onSwitchModel,
                            onSwitchReasoningEffort = onSwitchReasoningEffort,
                            onRemoveAttachment = { attachment ->
                                mediaAttachments.remove(attachment)
                                if (mediaAttachments.size < MAX_COMPOSER_ATTACHMENTS) {
                                    attachmentHint = null
                                }
                            },
                            onToggleSubagentsArmed = { subagentsArmed = !subagentsArmed },
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
                                if (isDispatching) {
                                    return@ComposerDock
                                }
                                val selectedReviewTarget = armedReviewTarget
                                val trimmed = composerInput.trim()
                                if (selectedReviewTarget == null && trimmed.isEmpty() && mediaAttachments.isEmpty() && !subagentsArmed) {
                                    return@ComposerDock
                                }
                                isDispatching = true
                                scope.launch {
                                    try {
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
                                    } finally {
                                        isDispatching = false
                                    }
                                }
                            },
                            onStop = {
                                showReviewTargetSuggestions = false
                                showForkDestinationSuggestions = false
                                scope.launch { runCatching { service.interruptActiveTurn() } }
                            }
                        )
                        if (userMessageIndexes.size > 1) {
                            TimelineScrubber(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 6.dp, bottom = 144.dp),
                                count = userMessageIndexes.size,
                                onPositionChanged = { normalized ->
                                    val target = userMessageIndexes[
                                        (normalized * userMessageIndexes.lastIndex)
                                            .toInt()
                                            .coerceIn(0, userMessageIndexes.lastIndex)
                                    ]
                                    scope.launch { timelineListState.scrollToItem(target) }
                                }
                            )
                        }

                        if (showVoiceSetupSheet) {
                            VoiceSetupHelpDialog(
                                onDismiss = { showVoiceSetupSheet = false }
                            )
                        }

                        if (showGitActionsMenu) {
                            GitActionsDialog(
                                selectedBranch = checkoutBranch,
                                onDismiss = { showGitActionsMenu = false },
                                onShowDiff = {
                                    showGitActionsMenu = false
                                    scope.launch {
                                        runCatching { service.gitDiff() }
                                            .onSuccess { patch ->
                                                repositoryDiffPatch = patch
                                                showRepositoryDiffDialog = true
                                            }
                                    }
                                },
                                onPull = {
                                    showGitActionsMenu = false
                                    scope.launch { runCatching { service.gitPull() } }
                                },
                                onPush = {
                                    showGitActionsMenu = false
                                    scope.launch { runCatching { service.gitPush() } }
                                },
                                onCommit = {
                                    showGitActionsMenu = false
                                    showCommitSheet = true
                                },
                                onCommitAndPush = {
                                    showGitActionsMenu = false
                                    showCommitSheet = true
                                }
                            )
                        }

                        if (showRepositoryDiffDialog) {
                            DiffPreviewDialog(
                                title = "Repository Changes",
                                rawPatch = repositoryDiffPatch,
                                onDismiss = { showRepositoryDiffDialog = false }
                            )
                        }

                        if (showCommitSheet) {
                            CommitComposerDialog(
                                message = commitMessageDraft,
                                onMessageChange = { commitMessageDraft = it },
                                onDismiss = {
                                    showCommitSheet = false
                                    commitMessageDraft = ""
                                },
                                onCommit = {
                                    val message = commitMessageDraft
                                    showCommitSheet = false
                                    commitMessageDraft = ""
                                    scope.launch { runCatching { service.gitCommit(message) } }
                                },
                                onCommitAndPush = {
                                    val message = commitMessageDraft
                                    showCommitSheet = false
                                    commitMessageDraft = ""
                                    scope.launch { runCatching { service.gitCommitAndPush(message) } }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun VoiceSetupHelpDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use ChatGPT on Mac") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Open ChatGPT on your Mac.")
                Text("2. Sign in there with the account you want for voice mode.")
                Text("3. Keep the bridge connected and come back to Remodex.")
                Text(
                    text = "You do not need to start ChatGPT login from Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun WorkspaceTopBar(
    status: String,
    selectedThreadTitle: String?,
    currentProjectPath: String?,
    gitStatusSummary: String,
    checkoutBranch: String,
    hasPendingPermissions: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onShowRepositoryDiff: () -> Unit,
    onOpenGitActions: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactToolbarButton(label = "≡", onClick = onMenu, compact = true)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onTap),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = selectedThreadTitle ?: "Remodex",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            selectedThreadTitle == null -> status
                            !currentProjectPath.isNullOrBlank() -> currentProjectPath
                            else -> gitStatusSummary
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedThreadTitle != null) {
                        StatusPill(
                            text = checkoutBranch.ifBlank { "chat" }.take(10),
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                    CompactToolbarButton(label = "∆", onClick = onShowRepositoryDiff, compact = true)
                    CompactToolbarButton(label = "⋯", onClick = onOpenGitActions, compact = true)
                    CompactToolbarButton(label = "↻", onClick = onRefresh, compact = true)
                }
            }

            if (hasPendingPermissions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(text = "Needs approval", accent = Color(0xFFE6A23C))
                }
            }
        }
    }
}

@Composable
private fun GitActionsDialog(
    selectedBranch: String,
    onDismiss: () -> Unit,
    onShowDiff: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Git Actions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedBranch.isNotBlank()) {
                    Text(
                        text = "Branch $selectedBranch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GitActionMenuRow("∆", "Diff", onShowDiff)
                GitActionMenuRow("✓", "Commit", onCommit)
                GitActionMenuRow("⇪", "Commit & Push", onCommitAndPush)
                GitActionMenuRow("↑", "Push", onPush)
                GitActionMenuRow("↻", "Pull", onPull)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun GitActionMenuRow(
    iconGlyph: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = iconGlyph,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CommitComposerDialog(
    message: String,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Commit Changes", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Commit message") },
                    placeholder = { Text("Changes from Android") },
                    minLines = 2,
                    maxLines = 4
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    OutlinedButton(onClick = onCommit, modifier = Modifier.weight(1f)) {
                        Text("Commit")
                    }
                    Button(onClick = onCommitAndPush, modifier = Modifier.weight(1f)) {
                        Text("Commit & Push")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffPreviewDialog(
    title: String,
    rawPatch: String,
    onDismiss: () -> Unit
) {
    val entries = remember(rawPatch) { DiffPreviewParser.parse(rawPatch) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.path }) { entry ->
                        DiffPreviewCard(entry = entry)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun DiffPreviewCard(entry: DiffPreviewEntry) {
    var expanded by rememberSaveable(entry.path) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry.compactPath, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(entry.action, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("+${entry.additions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF22A95A))
                Text("-${entry.deletions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD74B4B))
            }
            entry.directoryPath?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = entry.diff,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show patch")
            }
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
    gitActionStatus: String?,
    rateLimitInfo: String,
    ciStatus: String,
    branches: List<String>,
    selectedBranch: String,
    onBranchSelected: (String) -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit
) {
    val hasCiStatus = ciStatus.isNotBlank()
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
                text = gitActionStatus ?: gitStatusSummary,
                style = MaterialTheme.typography.bodySmall,
                color = if (gitActionStatus.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = rateLimitInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasCiStatus) {
                Text(
                    text = ciStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun RecoveryAccessoryCard(
    snapshot: RecoveryAccessorySnapshot,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = when (snapshot.status) {
        RecoveryAccessoryStatus.INTERRUPTED -> Color(0xFFE6A23C)
        RecoveryAccessoryStatus.RECONNECTING -> MaterialTheme.colorScheme.primary
        RecoveryAccessoryStatus.ACTION_REQUIRED -> Color(0xFFE6A23C)
        RecoveryAccessoryStatus.SYNCING -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = snapshot.title,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!snapshot.detail.isNullOrBlank()) {
                Text(
                    text = snapshot.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!snapshot.actionLabel.isNullOrBlank()) {
                    OutlinedButton(onClick = onAction, modifier = Modifier.weight(1f)) {
                        Text(snapshot.actionLabel)
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Close")
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
    selectedReasoningEffort: String,
    availableReasoningEfforts: List<String>,
    selectedBranch: String,
    rateLimitInfo: String,
    ciStatus: String,
    selectedThreadId: String?,
    composerInput: String,
    onComposerInputChange: (String) -> Unit,
    dockCollapsedSide: String,
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
    isDispatching: Boolean,
    onQueuePausedChange: (Boolean) -> Unit,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onUseVoiceDraft: () -> Unit,
    onCheckRateLimits: () -> Unit,
    onSwitchModel: (String) -> Unit,
    onSwitchReasoningEffort: (String) -> Unit,
    onRemoveAttachment: (TurnImageAttachment) -> Unit,
    onToggleSubagentsArmed: () -> Unit,
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
    var showAdvancedActions by rememberSaveable { mutableStateOf(false) }
    var showModelMenu by rememberSaveable { mutableStateOf(false) }
    var showReasoningMenu by rememberSaveable { mutableStateOf(false) }
    var isInputFocused by rememberSaveable { mutableStateOf(false) }
    var isCollapsed by rememberSaveable(selectedThreadId) { mutableStateOf(false) }
    val showsPrimaryStop = !isDispatching && selectedThreadId != null && isRunning && composerInput.isBlank() && mediaAttachments.isEmpty()

    LaunchedEffect(isInputFocused, composerInput, isRunning, isDispatching, showAdvancedActions, showModelMenu, showReasoningMenu) {
        if (isInputFocused || composerInput.isNotBlank() || isDispatching || showAdvancedActions || showModelMenu || showReasoningMenu) {
            isCollapsed = false
        } else if (!isRunning) {
            isCollapsed = true
        }
    }

    Box(modifier = modifier.navigationBarsPadding()) {
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        .padding(top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (
                        mediaAttachments.isNotEmpty()
                        || mentionedFiles.isNotEmpty()
                        || mentionedSkills.isNotEmpty()
                        || subagentsArmed
                        || armedReviewTarget != null
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
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
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    if (isDispatching) {
                        Text(
                            text = "Sending to Codex...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    if (voiceDraftText.isNotBlank()) {
                        OutlinedTextField(
                            value = voiceDraftText,
                            onValueChange = onVoiceDraftTextChange,
                            label = { Text("Voice draft") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = composerInput,
                            onValueChange = onComposerInputChange,
                            placeholder = { Text("Ask anything... @files, \$skills, /commands") },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            enabled = !isDispatching,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    isInputFocused = focusState.isFocused
                                },
                            minLines = 1,
                            maxLines = 5,
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                    if (showModelMenu && availableModels.isNotEmpty()) {
                        SuggestionTray(
                            labels = availableModels,
                            onSelected = { index ->
                                onSwitchModel(availableModels[index])
                                showModelMenu = false
                            }
                        )
                    }
                    if (showReasoningMenu && availableReasoningEfforts.isNotEmpty()) {
                        SuggestionTray(
                            labels = availableReasoningEfforts,
                            onSelected = { index ->
                                onSwitchReasoningEffort(availableReasoningEfforts[index])
                                showReasoningMenu = false
                            }
                        )
                    }
                    if (showAdvancedActions) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComposerMenuPill(
                                title = "Photo library",
                                selected = false,
                                onClick = onAttachGallery,
                                modifier = Modifier.weight(1f)
                            )
                            ComposerMenuPill(
                                title = "Take photo",
                                selected = false,
                                onClick = onAttachCamera,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    ComposerMetaRow(
                        selectedBranch = selectedBranch,
                        hasBranch = selectedBranch.isNotBlank(),
                        rateLimitInfo = rateLimitInfo,
                        ciStatus = ciStatus,
                        onRefreshStatus = onCheckRateLimits
                    )
                    ComposerBottomBar(
                        selectedModel = selectedModel,
                        selectedReasoningEffort = selectedReasoningEffort,
                        subagentsArmed = subagentsArmed,
                        queuePaused = queuePaused,
                        queuedCount = queuedDrafts.size,
                        showAdvancedActions = showAdvancedActions,
                        showModelMenu = showModelMenu,
                        showReasoningMenu = showReasoningMenu,
                        isDispatching = isDispatching,
                        showsPrimaryStop = showsPrimaryStop,
                        onToggleAdvancedActions = { showAdvancedActions = !showAdvancedActions },
                        onToggleModelMenu = {
                            showModelMenu = !showModelMenu
                            if (showModelMenu) {
                                showReasoningMenu = false
                            }
                        },
                        onToggleReasoningMenu = {
                            showReasoningMenu = !showReasoningMenu
                            if (showReasoningMenu) {
                                showModelMenu = false
                            }
                        },
                        onToggleSubagents = onToggleSubagentsArmed,
                        onResumeQueue = { onQueuePausedChange(false) },
                        onUseVoiceDraft = onUseVoiceDraft,
                        onCheckRateLimits = onCheckRateLimits,
                        onStop = onStop,
                        onSend = onSend
                    )
                }
            }

                }
            }
        }
        if (!isInputFocused && isCollapsed) {
            CollapsedComposerHandle(
                modifier = Modifier.align(
                    if (dockCollapsedSide.equals("left", ignoreCase = true)) Alignment.BottomStart else Alignment.BottomEnd
                ),
                onExpand = { isCollapsed = false }
            )
        }
    }
}

@Composable
private fun ComposerMetaRow(
    selectedBranch: String,
    hasBranch: Boolean,
    rateLimitInfo: String,
    ciStatus: String,
    onRefreshStatus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComposerMenuPill(title = "Local", selected = false, onClick = {}, showChevron = false)
        if (hasBranch) {
            ComposerMenuPill(title = selectedBranch, selected = true, onClick = {}, showChevron = false)
        }
        val statusLabel = when {
            ciStatus.isNotBlank() -> ciStatus.removePrefix("CI status: ").trim()
            else -> rateLimitInfo.removePrefix("Rate limit: ").trim()
        }
        Spacer(modifier = Modifier.weight(1f))
        if (statusLabel.isNotBlank()) {
            ComposerMenuPill(
                title = statusLabel,
                selected = false,
                onClick = onRefreshStatus,
                showChevron = false
            )
        }
    }
}

@Composable
private fun ComposerBottomBar(
    selectedModel: String,
    selectedReasoningEffort: String,
    subagentsArmed: Boolean,
    queuePaused: Boolean,
    queuedCount: Int,
    showAdvancedActions: Boolean,
    showModelMenu: Boolean,
    showReasoningMenu: Boolean,
    isDispatching: Boolean,
    showsPrimaryStop: Boolean,
    onToggleAdvancedActions: () -> Unit,
    onToggleModelMenu: () -> Unit,
    onToggleReasoningMenu: () -> Unit,
    onToggleSubagents: () -> Unit,
    onResumeQueue: () -> Unit,
    onUseVoiceDraft: () -> Unit,
    onCheckRateLimits: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ComposerCircleButton(
                glyph = if (showAdvancedActions) "−" else "+",
                contentDescription = if (showAdvancedActions) "Hide attachments" else "Show attachments",
                filled = false,
                onClick = onToggleAdvancedActions,
                enabled = !isDispatching
            )
            ComposerMenuPill(
                title = selectedModel,
                selected = showModelMenu,
                onClick = { if (!isDispatching) onToggleModelMenu() },
                modifier = Modifier.weight(1f)
            )
            ComposerMenuPill(
                title = selectedReasoningEffort,
                selected = showReasoningMenu,
                onClick = { if (!isDispatching) onToggleReasoningMenu() }
            )
        }
        if (subagentsArmed) {
            ComposerCircleButton(
                glyph = "✦",
                contentDescription = "Toggle subagents",
                filled = true,
                onClick = onToggleSubagents,
                enabled = !isDispatching,
                compact = true
            )
        }
        if (queuePaused && queuedCount > 0) {
            ComposerCircleButton(
                glyph = "↻",
                contentDescription = "Resume queue",
                filled = false,
                onClick = onResumeQueue
            )
        }
        ComposerCircleButton(
            glyph = "•",
            contentDescription = "Voice draft",
            filled = false,
            onClick = onUseVoiceDraft,
            enabled = !isDispatching
        )
        ComposerCircleButton(
            glyph = when {
                isDispatching -> "…"
                showsPrimaryStop -> "■"
                else -> "↑"
            },
            contentDescription = if (showsPrimaryStop) "Stop current turn" else "Send message",
            filled = true,
            onClick = if (isDispatching) ({}) else if (showsPrimaryStop) onStop else onSend,
            enabled = !isDispatching || showsPrimaryStop
        )
    }
}

@Composable
private fun CollapsedComposerHandle(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 18.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✎",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ask",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TimelineScrubber(
    modifier: Modifier = Modifier,
    count: Int,
    onPositionChanged: (Float) -> Unit
) {
    Surface(
        modifier = modifier
            .pointerInput(count) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onPositionChanged((offset.y / size.height).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        onPositionChanged((change.position.y / size.height).coerceIn(0f, 1f))
                    }
                )
            }
            .clickable { onPositionChanged(1f) },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(count.coerceAtMost(18)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ComposerMenuPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showChevron) {
                Text(
                    text = "v",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComposerCircleButton(
    glyph: String,
    contentDescription: String,
    filled: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (filled) {
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.78f else 0.42f)
        },
        border = if (filled) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.6f else 0.3f)
        )
    ) {
        Text(
            text = glyph,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            color = if (filled) {
                MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 1f else 0.78f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.58f)
            },
            modifier = Modifier
                .padding(
                    horizontal = if (compact) 11.dp else 10.dp,
                    vertical = if (compact) 8.dp else 9.dp
                )
                .size(if (compact) 18.dp else 20.dp),
            textAlign = TextAlign.Center
        )
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

private fun nullIfBlank(value: String?): String? {
    val normalized = value?.trim().orEmpty()
    return normalized.takeIf { it.isNotEmpty() }
}

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

private fun middleClip(value: String, maxChars: Int): String {
    if (value.length <= maxChars || maxChars < 8) {
        return value
    }
    val keep = (maxChars - 1) / 2
    val start = value.take(keep)
    val end = value.takeLast(maxChars - keep - 1)
    return "$start…$end"
}

private fun Bitmap.toJpegDataUrl(quality: Int = 85): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 100), output)
    val bytes = output.toByteArray()
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:image/jpeg;base64,$base64"
}
