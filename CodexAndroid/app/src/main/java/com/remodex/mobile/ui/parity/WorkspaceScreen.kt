package com.remodex.mobile.ui.parity

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.model.TimelineRole
import com.remodex.mobile.service.ConnectionState
import com.remodex.mobile.service.FileAutocompleteMatch
import com.remodex.mobile.service.PendingPermissionRequest
import com.remodex.mobile.service.RecoveryAccessorySnapshot
import com.remodex.mobile.service.RecoveryAccessoryStatus
import com.remodex.mobile.service.ReviewTarget
import com.remodex.mobile.service.SkillSuggestion
import com.remodex.mobile.service.TurnImageAttachment
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

private const val MAX_COMPOSER_ATTACHMENTS = 4
private const val SUBAGENTS_PROMPT =
    "Run subagents for different tasks. Delegate distinct work in parallel when helpful and synthesize the results."

@Composable
fun WorkspaceScreen(
    actions: WorkspaceActionHandler,
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
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId && !it.isArchived }
    val isConnected = connectionState == ConnectionState.Connected
    val projectPath = currentProjectPath.takeUnless { it == "Project path not resolved." }.orEmpty()
    val listState = rememberLazyListState()
    val userMessageIndexes = remember(timeline) {
        timeline.mapIndexedNotNull { index, entry -> index.takeIf { entry.role == TimelineRole.USER } }
    }

    val mediaAttachments = remember { mutableStateListOf<TurnImageAttachment>() }
    val mentionedFiles = remember { mutableStateListOf<String>() }
    val mentionedSkills = remember { mutableStateListOf<SkillSuggestion>() }
    val queuedDrafts = remember { mutableStateListOf<QueuedComposerDraft>() }
    var fileSuggestions by remember { mutableStateOf<List<FileAutocompleteMatch>>(emptyList()) }
    var skillSuggestions by remember { mutableStateOf<List<SkillSuggestion>>(emptyList()) }
    var attachmentHint by rememberSaveable { mutableStateOf<String?>(null) }
    var isDispatching by rememberSaveable(selectedThreadId) { mutableStateOf(false) }
    var queuePaused by rememberSaveable { mutableStateOf(false) }
    var subagentsArmed by rememberSaveable { mutableStateOf(false) }
    var reviewTarget by rememberSaveable { mutableStateOf<ReviewTarget?>(null) }
    var showReviewTargets by rememberSaveable { mutableStateOf(false) }
    var showForkTargets by rememberSaveable { mutableStateOf(false) }
    var showGitDialog by rememberSaveable { mutableStateOf(false) }
    var showDiffDialog by rememberSaveable { mutableStateOf(false) }
    var diffPatch by rememberSaveable { mutableStateOf("") }
    var showCommitDialog by rememberSaveable { mutableStateOf(false) }
    var commitMessage by rememberSaveable { mutableStateOf("") }

    val activeToken = remember(composerInput) { detectComposerAutocompleteToken(composerInput) }
    val commandSuggestions = remember(activeToken, selectedThreadId) {
        when (val token = activeToken) {
            is ComposerAutocompleteToken.Command -> filterComposerCommands(token.query, includeFork = !selectedThreadId.isNullOrBlank())
            else -> emptyList()
        }
    }

    val context = LocalContext.current
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val attachment = uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
                    TurnImageAttachment("data:image/jpeg;base64,$base64", it.lastPathSegment ?: "gallery-image")
                }
            }.getOrNull()
        }
        if (attachment != null) {
            if (mediaAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                attachmentHint = "You can attach up to $MAX_COMPOSER_ATTACHMENTS images."
            } else {
                mediaAttachments.add(attachment)
                attachmentHint = null
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val attachment = bitmap?.toJpegDataUrl()?.let { dataUrl ->
            TurnImageAttachment(dataUrl, "camera-${System.currentTimeMillis()}.jpg")
        }
        if (attachment != null) {
            if (mediaAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                attachmentHint = "You can attach up to $MAX_COMPOSER_ATTACHMENTS images."
            } else {
                mediaAttachments.add(attachment)
                attachmentHint = null
            }
        }
    }

    LaunchedEffect(isConnected, selectedThreadId) {
        if (!isConnected) return@LaunchedEffect
        runCatching { actions.refreshThreads() }
        runCatching { actions.refreshActiveThreadTimeline() }
        runCatching { actions.refreshGitStatus() }
        runCatching { actions.refreshRateLimitInfo() }
        if (!selectedThreadId.isNullOrBlank()) {
            runCatching { actions.reconcileThreadRunningState(selectedThreadId) }
        }
    }

    LaunchedEffect(activeToken, selectedThreadId, projectPath) {
        when (val token = activeToken) {
            is ComposerAutocompleteToken.File -> {
                val roots = listOfNotNull(selectedThread?.cwd, projectPath.takeIf { it.isNotBlank() }).distinct()
                fileSuggestions = runCatching { actions.fuzzyFileSearch(token.query, roots = roots, limit = 8) }.getOrDefault(emptyList())
                skillSuggestions = emptyList()
            }
            is ComposerAutocompleteToken.Skill -> {
                val roots = listOfNotNull(selectedThread?.cwd, projectPath.takeIf { it.isNotBlank() }).distinct()
                skillSuggestions = runCatching { actions.listSkills(cwds = roots, forceReload = false, limit = 8) }.getOrDefault(emptyList())
                fileSuggestions = emptyList()
            }
            else -> {
                fileSuggestions = emptyList()
                skillSuggestions = emptyList()
            }
        }
    }

    LaunchedEffect(timeline.size, selectedThreadId) {
        if (timeline.isNotEmpty()) {
            listState.animateScrollToItem(timeline.lastIndex)
        }
    }

    fun clearComposer() {
        onComposerInputChange("")
        mediaAttachments.clear()
        mentionedFiles.clear()
        mentionedSkills.clear()
        attachmentHint = null
        subagentsArmed = false
        reviewTarget = null
        showReviewTargets = false
        showForkTargets = false
    }

    fun normalizedInput(): String = buildComposerPayloadText(
        input = composerInput,
        mentionedFiles = mentionedFiles,
        mentionedSkills = mentionedSkills,
        subagentsArmed = subagentsArmed,
        armedReviewTarget = reviewTarget
    )

    fun dispatchMessage() {
        if (isDispatching) return
        val text = normalizedInput()
        if (text.isBlank() && mediaAttachments.isEmpty()) return
        if (selectedThreadId == null || queuePaused) {
            queuedDrafts.add(QueuedComposerDraft(text = text, attachments = mediaAttachments.toList()))
            clearComposer()
            return
        }
        isDispatching = true
        val pendingText = text
        val pendingAttachments = mediaAttachments.toList()
        scope.launch {
            runCatching { actions.sendTurnStart(pendingText, attachments = pendingAttachments) }
                .onSuccess { clearComposer() }
                .onFailure { error ->
                    attachmentHint = error.message?.takeIf { it.isNotBlank() } ?: "Send failed. Check connection logs."
                }
            isDispatching = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(330.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background
            ) {
                SidebarDrawerContent(
                    threads = threads,
                    selectedThreadId = selectedThreadId,
                    currentProjectPath = currentProjectPath,
                    onOpenThread = { threadId ->
                        scope.launch {
                            drawerState.close()
                            runCatching { actions.openThread(threadId) }
                        }
                    },
                    onStartThread = { projectHint ->
                        scope.launch {
                            drawerState.close()
                            runCatching { actions.startThread(projectPath = projectHint) }
                        }
                    },
                    rateLimitInfo = rateLimitInfo,
                    ciStatus = ciStatus,
                    autoRefreshEnabled = isConnected,
                    onAutoRefreshChanged = { _ -> },
                    onRefreshWorkspace = { scope.launch { actions.forceRefreshWorkspace() } },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onGitDiff = {
                        scope.launch {
                            diffPatch = runCatching { actions.gitDiff() }.getOrElse { it.message ?: "Diff unavailable." }
                            showDiffDialog = true
                        }
                    },
                    onGitCommit = { showCommitDialog = true },
                    onGitCommitAndPush = { scope.launch { actions.gitCommitAndPush(null) } },
                    onGitPull = { scope.launch { actions.gitPull() } },
                    onGitPush = { scope.launch { actions.gitPush() } },
                    onArchiveThread = { threadId -> scope.launch { actions.archiveThread(threadId) } },
                    onUnarchiveThread = { threadId -> scope.launch { actions.unarchiveThread(threadId) } },
                    onDeleteThreadLocally = { threadId -> scope.launch { actions.deleteThreadLocally(threadId) } },
                    onArchiveProjectGroup = { ids -> scope.launch { actions.archiveThreadGroup(ids) } },
                    onRenameThread = { threadId, title -> scope.launch { actions.renameThread(threadId, title) } },
                    onDisconnect = {
                        scope.launch {
                            actions.disconnect()
                            onOpenPairing()
                        }
                    }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WorkspacePrincipalHeader(
                    title = selectedThread?.displayTitle ?: "Remodex",
                    subtitle = selectedThread?.cwd ?: projectPath.ifBlank { status },
                    connectionState = connectionState,
                    onOpenSidebar = { scope.launch { drawerState.open() } },
                    onRefresh = { scope.launch { actions.forceRefreshWorkspace() } },
                    onOpenSettings = onOpenSettings,
                    onHeaderTap = onHeaderTap
                )

                if (selectedThreadId == null) {
                    EmptyWorkspaceHome(
                        connectionState = connectionState,
                        status = status,
                        trustedPairLabel = actions.currentPairingMacDeviceId(),
                        projectPath = projectPath.takeIf { it.isNotBlank() },
                        rateLimitInfo = rateLimitInfo,
                        ciStatus = ciStatus,
                        onOpenSidebar = { scope.launch { drawerState.open() } },
                        onOpenPairing = onOpenPairing,
                        onReconnect = { scope.launch { actions.reconnect() } },
                        onForgetPair = {
                            scope.launch {
                                actions.disconnect()
                                onOpenPairing()
                            }
                        },
                        onStartThread = { scope.launch { actions.startThread(projectPath = null) } }
                    )
                } else {
                    WorkspaceStatusStrip(
                        projectPath = projectPath.ifBlank { selectedThread?.cwd.orEmpty() },
                        gitStatusSummary = gitActionStatus ?: gitStatusSummary,
                        rateLimitInfo = rateLimitInfo,
                        ciStatus = ciStatus,
                        branch = checkoutBranch,
                        onOpenGit = { showGitDialog = true },
                        onCheckRateLimits = { scope.launch { actions.refreshRateLimitInfo() } }
                    )

                    PendingPermissionStrip(
                        pendingPermissions = pendingPermissions,
                        onGrant = { id -> scope.launch { actions.grantPermission(id, allow = true) } },
                        onDeny = { id -> scope.launch { actions.grantPermission(id, allow = false) } }
                    )

                    voiceRecoverySnapshot?.let { snapshot ->
                        RecoveryAccessoryCard(snapshot, onVoiceRecoveryAction, onDismissVoiceRecovery)
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (timeline.isEmpty()) {
                                item { EmptyTimelineHint(status = status) }
                            } else {
                                items(timeline, key = { it.id }) { entry -> TimelineRow(entry) }
                            }
                        }
                        TimelineScrubber(
                            indexes = userMessageIndexes,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onJumpToIndex = { index -> scope.launch { listState.animateScrollToItem(index) } }
                        )
                    }
                }
            }

            ComposerDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedModel = selectedModel,
                availableModels = availableModels,
                selectedReasoningEffort = selectedReasoningEffort,
                availableReasoningEfforts = availableReasoningEfforts,
                composerInput = composerInput,
                onComposerInputChange = onComposerInputChange,
                dockCollapsedSide = dockCollapsedSide,
                mediaAttachments = mediaAttachments,
                mentionedFiles = mentionedFiles,
                mentionedSkills = mentionedSkills,
                attachmentHint = attachmentHint,
                activeToken = activeToken,
                fileSuggestions = fileSuggestions,
                skillSuggestions = skillSuggestions,
                commandSuggestions = commandSuggestions,
                queuedDrafts = queuedDrafts,
                queuePaused = queuePaused,
                subagentsArmed = subagentsArmed,
                reviewTarget = reviewTarget,
                showReviewTargets = showReviewTargets,
                showForkTargets = showForkTargets,
                isDispatching = isDispatching,
                isRunning = selectedThreadId != null && actions.isThreadRunning(selectedThreadId),
                onQueuePausedChange = { queuePaused = it },
                onSwitchModel = onSwitchModel,
                onSwitchReasoningEffort = onSwitchReasoningEffort,
                onAttachGallery = { galleryPicker.launch("image/*") },
                onAttachCamera = { cameraLauncher.launch(null) },
                onVoice = onTriggerVoiceRecovery,
                onCheckRateLimits = { scope.launch { actions.refreshRateLimitInfo() } },
                onSelectFile = { token, match ->
                    onComposerInputChange(applyComposerAutocompleteSelection(composerInput, token, match.path))
                    mentionedFiles.add(match.path)
                },
                onSelectSkill = { token, skill ->
                    onComposerInputChange(applyComposerAutocompleteSelection(composerInput, token, skill.name))
                    mentionedSkills.add(skill)
                },
                onSelectCommand = { command ->
                    when (command.token) {
                        "/status" -> scope.launch { actions.forceRefreshWorkspace() }
                        "/subagents" -> subagentsArmed = true
                        "/review" -> showReviewTargets = true
                        "/fork" -> showForkTargets = true
                    }
                    onComposerInputChange(stripTrailingSlashCommandToken(composerInput))
                },
                onRemoveAttachment = { mediaAttachments.remove(it) },
                onRemoveMentionedFile = { mentionedFiles.remove(it) },
                onRemoveMentionedSkill = { mentionedSkills.remove(it) },
                onToggleSubagents = { subagentsArmed = !subagentsArmed },
                onSelectReviewTarget = {
                    reviewTarget = it
                    showReviewTargets = false
                },
                onDismissReviewTargets = { showReviewTargets = false },
                onForkLocal = {
                    showForkTargets = false
                    scope.launch { actions.threadFork() }
                },
                onForkWorktree = {
                    showForkTargets = false
                    scope.launch {
                        actions.threadFork(
                            targetProjectPath = selectedThread?.cwd?.takeIf { it.isNotBlank() } ?: projectPath.takeIf { it.isNotBlank() }
                        )
                    }
                },
                onDismissForkTargets = { showForkTargets = false },
                onRestoreQueuedDraft = { draft ->
                    onComposerInputChange(draft.text)
                    queuedDrafts.remove(draft)
                },
                onRemoveQueuedDraft = { queuedDrafts.remove(it) },
                onClearQueue = { queuedDrafts.clear() },
                onSend = { dispatchMessage() },
                onStop = { scope.launch { actions.interruptActiveTurn() } }
            )
        }
    }

    if (showGitDialog) {
        GitActionsDialog(
            branches = gitBranches,
            selectedBranch = checkoutBranch,
            gitStatusSummary = gitActionStatus ?: gitStatusSummary,
            onBranchSelected = { branch ->
                onCheckoutBranchChange(branch)
                scope.launch { actions.checkoutGitBranch(branch) }
            },
            onPull = { scope.launch { actions.gitPull() } },
            onPush = { scope.launch { actions.gitPush() } },
            onCommit = { showCommitDialog = true },
            onDiff = {
                scope.launch {
                    diffPatch = runCatching { actions.gitDiff() }.getOrElse { it.message ?: "Diff unavailable." }
                    showDiffDialog = true
                }
            },
            onDismiss = { showGitDialog = false }
        )
    }
    if (showDiffDialog) {
        TextPreviewDialog(title = "File Changes", body = diffPatch, onDismiss = { showDiffDialog = false })
    }
    if (showCommitDialog) {
        CommitDialog(
            value = commitMessage,
            onValueChange = { commitMessage = it },
            onDismiss = { showCommitDialog = false },
            onCommit = {
                scope.launch {
                    actions.gitCommitAndPush(commitMessage.takeIf { it.isNotBlank() })
                    commitMessage = ""
                    showCommitDialog = false
                }
            }
        )
    }
}

@Composable
private fun WorkspacePrincipalHeader(
    title: String,
    subtitle: String,
    connectionState: ConnectionState,
    onOpenSidebar: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onHeaderTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactToolbarButton(label = "☰", compact = true, onClick = onOpenSidebar)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onHeaderTap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        StatusDot(connectionState)
        CompactToolbarButton(label = "↻", compact = true, onClick = onRefresh)
        CompactToolbarButton(label = "⚙", compact = true, onClick = onOpenSettings)
    }
}

@Composable
private fun StatusDot(connectionState: ConnectionState) {
    val color = when (connectionState) {
        ConnectionState.Connected -> Color(0xFF34C759)
        ConnectionState.Connecting -> Color(0xFFFFCC00)
        is ConnectionState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun WorkspaceStatusStrip(
    projectPath: String,
    gitStatusSummary: String,
    rateLimitInfo: String,
    ciStatus: String,
    branch: String,
    onOpenGit: () -> Unit,
    onCheckRateLimits: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(projectPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(gitStatusSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (branch.isNotBlank()) SmallChip(branch, selected = true, onClick = onOpenGit)
                SmallChip("Git", selected = false, onClick = onOpenGit)
                SmallChip(compactRateLimitLabel(rateLimitInfo), selected = false, onClick = onCheckRateLimits)
                if (ciStatus.isNotBlank()) SmallChip(ciStatus.removePrefix("CI status: ").trim(), selected = false, onClick = onOpenGit)
            }
        }
    }
}

@Composable
private fun PendingPermissionStrip(
    pendingPermissions: List<PendingPermissionRequest>,
    onGrant: (String) -> Unit,
    onDeny: (String) -> Unit
) {
    pendingPermissions.firstOrNull()?.let { permission ->
        SectionCard(title = "Approval request", subtitle = permission.summary ?: permission.title) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onGrant(permission.id) }, modifier = Modifier.weight(1f)) { Text("Allow") }
                OutlinedButton(onClick = { onDeny(permission.id) }, modifier = Modifier.weight(1f)) { Text("Deny") }
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        EmptyHomeCard(
            connectionState = connectionState,
            status = status,
            trustedPairLabel = trustedPairLabel,
            projectPath = projectPath,
            rateLimitInfo = rateLimitInfo,
            ciStatus = ciStatus,
            primaryActionLabel = if (connectionState == ConnectionState.Connected) "New Chat" else "Reconnect",
            onPrimaryAction = if (connectionState == ConnectionState.Connected) onStartThread else onReconnect,
            secondaryActionLabel = if (connectionState == ConnectionState.Connected) "Chats" else "Scan QR",
            onSecondaryAction = if (connectionState == ConnectionState.Connected) onOpenSidebar else onOpenPairing,
            onForgetPair = if (connectionState == ConnectionState.Connected) null else onForgetPair
        )
    }
}

@Composable
private fun EmptyTimelineHint(status: String) {
    SectionCard(title = "Start with a prompt", subtitle = status) {
        Text(
            text = "Use @files, ${'$'}skills, /commands, attachments, or voice from the composer below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecoveryAccessoryCard(
    snapshot: RecoveryAccessorySnapshot,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = when (snapshot.status) {
        RecoveryAccessoryStatus.INTERRUPTED -> Color(0xFFFF9500)
        RecoveryAccessoryStatus.ACTION_REQUIRED -> Color(0xFFFF9500)
        RecoveryAccessoryStatus.RECONNECTING -> MaterialTheme.colorScheme.primary
        RecoveryAccessoryStatus.SYNCING -> MaterialTheme.colorScheme.primary
    }
    SectionCard(title = snapshot.title, subtitle = snapshot.summary) {
        snapshot.detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.actionLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Button(onClick = onAction, modifier = Modifier.weight(1f)) { Text(label) }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
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
    composerInput: String,
    onComposerInputChange: (String) -> Unit,
    dockCollapsedSide: String,
    mediaAttachments: List<TurnImageAttachment>,
    mentionedFiles: List<String>,
    mentionedSkills: List<SkillSuggestion>,
    attachmentHint: String?,
    activeToken: ComposerAutocompleteToken?,
    fileSuggestions: List<FileAutocompleteMatch>,
    skillSuggestions: List<SkillSuggestion>,
    commandSuggestions: List<ComposerCommand>,
    queuedDrafts: List<QueuedComposerDraft>,
    queuePaused: Boolean,
    subagentsArmed: Boolean,
    reviewTarget: ReviewTarget?,
    showReviewTargets: Boolean,
    showForkTargets: Boolean,
    isDispatching: Boolean,
    isRunning: Boolean,
    onQueuePausedChange: (Boolean) -> Unit,
    onSwitchModel: (String) -> Unit,
    onSwitchReasoningEffort: (String) -> Unit,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onVoice: () -> Unit,
    onCheckRateLimits: () -> Unit,
    onSelectFile: (ComposerAutocompleteToken.File, FileAutocompleteMatch) -> Unit,
    onSelectSkill: (ComposerAutocompleteToken.Skill, SkillSuggestion) -> Unit,
    onSelectCommand: (ComposerCommand) -> Unit,
    onRemoveAttachment: (TurnImageAttachment) -> Unit,
    onRemoveMentionedFile: (String) -> Unit,
    onRemoveMentionedSkill: (SkillSuggestion) -> Unit,
    onToggleSubagents: () -> Unit,
    onSelectReviewTarget: (ReviewTarget) -> Unit,
    onDismissReviewTargets: () -> Unit,
    onForkLocal: () -> Unit,
    onForkWorktree: () -> Unit,
    onDismissForkTargets: () -> Unit,
    onRestoreQueuedDraft: (QueuedComposerDraft) -> Unit,
    onRemoveQueuedDraft: (QueuedComposerDraft) -> Unit,
    onClearQueue: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var focused by rememberSaveable { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var modelOpen by rememberSaveable { mutableStateOf(false) }
    var reasoningOpen by rememberSaveable { mutableStateOf(false) }
    val showStop = composerInput.isBlank() && mediaAttachments.isEmpty() && isRunning && !isDispatching
    val collapsedScale by animateFloatAsState(if (collapsed) 0.74f else 1f, animationSpec = tween(220), label = "composerScale")

    LaunchedEffect(focused, composerInput, isDispatching, advancedOpen, modelOpen, reasoningOpen) {
        collapsed = !focused && composerInput.isBlank() && !isDispatching && !advancedOpen && !modelOpen && !reasoningOpen
    }

    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().scale(collapsedScale),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComposerSuggestionLayer(
                        activeToken = activeToken,
                        fileSuggestions = fileSuggestions,
                        skillSuggestions = skillSuggestions,
                        commandSuggestions = commandSuggestions,
                        showReviewTargets = showReviewTargets,
                        showForkTargets = showForkTargets,
                        onSelectFile = onSelectFile,
                        onSelectSkill = onSelectSkill,
                        onSelectCommand = onSelectCommand,
                        onSelectReviewTarget = onSelectReviewTarget,
                        onDismissReviewTargets = onDismissReviewTargets,
                        onForkLocal = onForkLocal,
                        onForkWorktree = onForkWorktree,
                        onDismissForkTargets = onDismissForkTargets
                    )
                    if (queuedDrafts.isNotEmpty()) {
                        QueuedDraftsPanel(queuedDrafts, queuePaused, onQueuePausedChange, onRestoreQueuedDraft, onRemoveQueuedDraft, onClearQueue)
                    }
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComposerAccessoryChips(
                                mediaAttachments,
                                mentionedFiles,
                                mentionedSkills,
                                subagentsArmed,
                                reviewTarget,
                                onRemoveAttachment,
                                onRemoveMentionedFile,
                                onRemoveMentionedSkill,
                                onToggleSubagents
                            )
                            attachmentHint?.let {
                                Text(it, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (isDispatching) {
                                Text("Dispatching to Codex...", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            OutlinedTextField(
                                value = composerInput,
                                onValueChange = onComposerInputChange,
                                placeholder = { Text("Ask anything... @files, ${'$'}skills, /commands") },
                                minLines = 1,
                                maxLines = 4,
                                enabled = !isDispatching,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).onFocusChanged { focused = it.isFocused },
                                shape = RoundedCornerShape(20.dp),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            if (advancedOpen) {
                                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ComposerMenuPill("Photo", false, onAttachGallery, Modifier.weight(1f), showChevron = false)
                                    ComposerMenuPill("Camera", false, onAttachCamera, Modifier.weight(1f), showChevron = false)
                                    ComposerMenuPill("Subagents", subagentsArmed, onToggleSubagents, Modifier.weight(1f), showChevron = false)
                                }
                            }
                            ComposerRuntimeBar(
                                selectedModel = selectedModel,
                                availableModels = availableModels,
                                modelOpen = modelOpen,
                                onToggleModel = { modelOpen = !modelOpen; reasoningOpen = false },
                                onSelectModel = { onSwitchModel(it); modelOpen = false },
                                selectedReasoningEffort = selectedReasoningEffort,
                                availableReasoningEfforts = availableReasoningEfforts,
                                reasoningOpen = reasoningOpen,
                                onToggleReasoning = { reasoningOpen = !reasoningOpen; modelOpen = false },
                                onSelectReasoning = { onSwitchReasoningEffort(it); reasoningOpen = false },
                                advancedOpen = advancedOpen,
                                onToggleAdvanced = { advancedOpen = !advancedOpen },
                                onCheckRateLimits = onCheckRateLimits,
                                onVoice = onVoice,
                                showStop = showStop,
                                isDispatching = isDispatching,
                                onSend = onSend,
                                onStop = onStop
                            )
                        }
                    }
                }
            }
        }
        if (collapsed) {
            CollapsedComposerHandle(
                modifier = Modifier.align(if (dockCollapsedSide.equals("left", true)) Alignment.BottomStart else Alignment.BottomEnd),
                onExpand = { collapsed = false }
            )
        }
    }
}

@Composable
private fun ComposerSuggestionLayer(
    activeToken: ComposerAutocompleteToken?,
    fileSuggestions: List<FileAutocompleteMatch>,
    skillSuggestions: List<SkillSuggestion>,
    commandSuggestions: List<ComposerCommand>,
    showReviewTargets: Boolean,
    showForkTargets: Boolean,
    onSelectFile: (ComposerAutocompleteToken.File, FileAutocompleteMatch) -> Unit,
    onSelectSkill: (ComposerAutocompleteToken.Skill, SkillSuggestion) -> Unit,
    onSelectCommand: (ComposerCommand) -> Unit,
    onSelectReviewTarget: (ReviewTarget) -> Unit,
    onDismissReviewTargets: () -> Unit,
    onForkLocal: () -> Unit,
    onForkWorktree: () -> Unit,
    onDismissForkTargets: () -> Unit
) {
    when {
        showForkTargets -> SuggestionTray(listOf("Fork in current project", "Fork in new worktree", "Cancel")) { index ->
            when (index) { 0 -> onForkLocal(); 1 -> onForkWorktree(); else -> onDismissForkTargets() }
        }
        showReviewTargets -> SuggestionTray(listOf("Review uncommitted changes", "Review base branch", "Cancel")) { index ->
            when (index) { 0 -> onSelectReviewTarget(ReviewTarget.UNCOMMITTED_CHANGES); 1 -> onSelectReviewTarget(ReviewTarget.BASE_BRANCH); else -> onDismissReviewTargets() }
        }
        activeToken is ComposerAutocompleteToken.File && fileSuggestions.isNotEmpty() -> SuggestionTray(fileSuggestions.map { it.fileName }) { onSelectFile(activeToken, fileSuggestions[it]) }
        activeToken is ComposerAutocompleteToken.Skill && skillSuggestions.isNotEmpty() -> SuggestionTray(skillSuggestions.map { "$${it.name}" }) { onSelectSkill(activeToken, skillSuggestions[it]) }
        activeToken is ComposerAutocompleteToken.Command && commandSuggestions.isNotEmpty() -> SuggestionTray(commandSuggestions.map { it.token }) { onSelectCommand(commandSuggestions[it]) }
    }
}

@Composable
private fun ComposerAccessoryChips(
    mediaAttachments: List<TurnImageAttachment>,
    mentionedFiles: List<String>,
    mentionedSkills: List<SkillSuggestion>,
    subagentsArmed: Boolean,
    reviewTarget: ReviewTarget?,
    onRemoveAttachment: (TurnImageAttachment) -> Unit,
    onRemoveFile: (String) -> Unit,
    onRemoveSkill: (SkillSuggestion) -> Unit,
    onToggleSubagents: () -> Unit
) {
    if (mediaAttachments.isEmpty() && mentionedFiles.isEmpty() && mentionedSkills.isEmpty() && !subagentsArmed && reviewTarget == null) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        mediaAttachments.forEach { SmallChip(it.label ?: "image", true) { onRemoveAttachment(it) } }
        mentionedFiles.forEach { SmallChip("@${it.substringAfterLast('/')}", true) { onRemoveFile(it) } }
        mentionedSkills.forEach { SmallChip("$${it.name}", true) { onRemoveSkill(it) } }
        if (subagentsArmed) SmallChip("/subagents", true, onToggleSubagents)
        reviewTarget?.let { SmallChip(reviewTargetChipLabel(it), true) {} }
    }
}

@Composable
private fun ComposerRuntimeBar(
    selectedModel: String,
    availableModels: List<String>,
    modelOpen: Boolean,
    onToggleModel: () -> Unit,
    onSelectModel: (String) -> Unit,
    selectedReasoningEffort: String,
    availableReasoningEfforts: List<String>,
    reasoningOpen: Boolean,
    onToggleReasoning: () -> Unit,
    onSelectReasoning: (String) -> Unit,
    advancedOpen: Boolean,
    onToggleAdvanced: () -> Unit,
    onCheckRateLimits: () -> Unit,
    onVoice: () -> Unit,
    showStop: Boolean,
    isDispatching: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComposerCircleButton(if (advancedOpen) "-" else "+", "Attachments", false, onToggleAdvanced, enabled = !isDispatching)
        Box {
            ComposerMenuPill(selectedModel, modelOpen, onToggleModel, showChevron = true)
            DropdownMenu(expanded = modelOpen, onDismissRequest = onToggleModel) {
                availableModels.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelectModel(it) }) }
            }
        }
        Box {
            ComposerMenuPill(selectedReasoningEffort, reasoningOpen, onToggleReasoning, showChevron = true)
            DropdownMenu(expanded = reasoningOpen, onDismissRequest = onToggleReasoning) {
                availableReasoningEfforts.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelectReasoning(it) }) }
            }
        }
        ComposerCircleButton("L", "Rate limits", false, onCheckRateLimits, compact = true)
        Spacer(Modifier.weight(1f))
        ComposerCircleButton("M", "Voice", false, onVoice, enabled = !isDispatching)
        ComposerCircleButton(
            glyph = when { isDispatching -> "..."; showStop -> "[]"; else -> "^" },
            contentDescription = if (showStop) "Stop" else "Send",
            filled = true,
            onClick = if (showStop) onStop else onSend,
            enabled = !isDispatching || showStop
        )
    }
}

@Composable
private fun QueuedDraftsPanel(
    drafts: List<QueuedComposerDraft>,
    queuePaused: Boolean,
    onQueuePausedChange: (Boolean) -> Unit,
    onRestore: (QueuedComposerDraft) -> Unit,
    onRemove: (QueuedComposerDraft) -> Unit,
    onClear: () -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Queued ${drafts.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                SmallChip(if (queuePaused) "Resume" else "Pause", false) { onQueuePausedChange(!queuePaused) }
                Spacer(Modifier.width(6.dp))
                SmallChip("Clear", false, onClear)
            }
            drafts.take(3).forEach { draft ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(draft.text.ifBlank { "Attachment draft" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onRestore(draft) }) { Text("Restore") }
                    TextButton(onClick = { onRemove(draft) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun CollapsedComposerHandle(modifier: Modifier, onExpand: () -> Unit) {
    Surface(
        modifier = modifier.padding(14.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onExpand),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ask", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("^", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TimelineScrubber(indexes: List<Int>, modifier: Modifier, onJumpToIndex: (Int) -> Unit) {
    if (indexes.isEmpty()) return
    Column(
        modifier = modifier.padding(end = 6.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)).padding(horizontal = 5.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        indexes.forEach { index ->
            Box(Modifier.size(width = 5.dp, height = 16.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)).clickable { onJumpToIndex(index) })
        }
    }
}

@Composable
private fun SuggestionTray(labels: List<String>, onSelected: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEachIndexed { index, label -> SmallChip(label, selected = index == 0, onClick = { onSelected(index) }) }
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
        modifier = modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = if (showChevron) "$title v" else title,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ComposerCircleButton(
    glyph: String,
    contentDescription: String,
    filled: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f).size(if (compact) 34.dp else 40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, style = MaterialTheme.typography.titleMedium, color = fg, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GitActionsDialog(
    branches: List<String>,
    selectedBranch: String,
    gitStatusSummary: String,
    onBranchSelected: (String) -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onCommit: () -> Unit,
    onDiff: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Git") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(gitStatusSummary, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    branches.forEach { SmallChip(it, it == selectedBranch) { onBranchSelected(it) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPull, modifier = Modifier.weight(1f)) { Text("Pull") }
                    OutlinedButton(onClick = onPush, modifier = Modifier.weight(1f)) { Text("Push") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDiff, modifier = Modifier.weight(1f)) { Text("Diff") }
                    Button(onClick = onCommit, modifier = Modifier.weight(1f)) { Text("Commit & Push") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TextPreviewDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body.ifBlank { "No changes." }, style = MaterialTheme.typography.bodySmall, maxLines = 18, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CommitDialog(value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onCommit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit & Push") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Commit message") },
                placeholder = { Text("Changes from Remodex Android") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = onCommit) { Text("Commit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class QueuedComposerDraft(
    val id: String = System.currentTimeMillis().toString(16),
    val text: String,
    val attachments: List<TurnImageAttachment>
)

private fun stripTrailingSlashCommandToken(input: String): String {
    val token = detectComposerAutocompleteToken(input) as? ComposerAutocompleteToken.Command ?: return input
    return input.removeRange(token.startIndex, token.endIndexExclusive).trimEnd()
}

private fun buildComposerPayloadText(
    input: String,
    mentionedFiles: List<String>,
    mentionedSkills: List<SkillSuggestion>,
    subagentsArmed: Boolean,
    armedReviewTarget: ReviewTarget?
): String {
    val directives = mutableListOf<String>()
    directives.addAll(mentionedFiles.map { "@$it" })
    directives.addAll(mentionedSkills.map { "${'$'}${it.name}" })
    if (subagentsArmed) directives.add(SUBAGENTS_PROMPT)
    armedReviewTarget?.let { directives.add("/review ${reviewTargetChipLabel(it)}") }
    return (directives + input.trim()).filter { it.isNotBlank() }.joinToString("\n")
}

private fun reviewTargetChipLabel(target: ReviewTarget): String = when (target) {
    ReviewTarget.UNCOMMITTED_CHANGES -> "review changes"
    ReviewTarget.BASE_BRANCH -> "review base"
}

private fun compactRateLimitLabel(raw: String): String {
    val cleaned = raw.removePrefix("Rate limit:").replace(" left", "").trim()
    return cleaned.ifBlank { "Limits" }
}

private fun Bitmap.toJpegDataUrl(): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 88, output)
    val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$encoded"
}
