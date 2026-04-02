package com.remodex.mobile.ui.parity

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.model.ThreadSummary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

@Composable
fun SidebarDrawerContent(
    threads: List<ThreadSummary>,
    selectedThreadId: String?,
    currentProjectPath: String,
    onOpenThread: (String) -> Unit,
    onStartThread: (String?) -> Unit,
    rateLimitInfo: String,
    ciStatus: String,
    autoRefreshEnabled: Boolean,
    onAutoRefreshChanged: (Boolean) -> Unit,
    onRefreshWorkspace: () -> Unit,
    onOpenSettings: () -> Unit,
    onGitDiff: () -> Unit,
    onGitCommit: () -> Unit,
    onGitCommitAndPush: () -> Unit,
    onGitPull: () -> Unit,
    onGitPush: () -> Unit,
    onRenameThread: (threadId: String, name: String) -> Unit,
    onArchiveThread: (threadId: String) -> Unit,
    onUnarchiveThread: (threadId: String) -> Unit,
    onDeleteThreadLocally: (threadId: String) -> Unit,
    onArchiveProjectGroup: (threadIds: List<String>) -> Unit,
    onDisconnect: () -> Unit
) {
    var threadSearchQuery by remember { mutableStateOf("") }
    var renameInput by rememberSaveable { mutableStateOf("") }
    var isRenameMode by rememberSaveable { mutableStateOf(false) }
    var showProjectChooser by rememberSaveable { mutableStateOf(false) }
    val collapsedSubagentParentIds = rememberSaveable {
        mutableStateOf(setOf<String>())
    }

    val selectedThread = remember(threads, selectedThreadId) {
        threads.firstOrNull { it.id == selectedThreadId }
    }
    val threadGroups = remember(threads, threadSearchQuery) {
        groupThreadsByProject(threads, threadSearchQuery)
    }
    val projectGroups = threadGroups.filter { it.kind == ThreadProjectGroupKind.PROJECT && it.projectPath != null }
    val currentProjectGroupPath = currentProjectPath
        .takeUnless { it == "Project path not resolved." }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    LaunchedEffect(selectedThread?.id) {
        renameInput = selectedThread?.displayTitle.orEmpty()
        isRenameMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SidebarIdentityHeader(onOpenSettings = onOpenSettings)

        SectionCard(title = "Chats", subtitle = "Search, create, and switch.") {
            OutlinedTextField(
                value = threadSearchQuery,
                onValueChange = { threadSearchQuery = it },
                label = { Text("Search chats") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onStartThread(currentProjectGroupPath) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("New Chat")
                }
                OutlinedButton(
                    onClick = { showProjectChooser = !showProjectChooser },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (showProjectChooser) "Hide Projects" else "Choose Project")
                }
            }
            if (showProjectChooser) {
                projectGroups.forEach { group ->
                    OutlinedButton(
                        onClick = { onStartThread(group.projectPath) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(group.label)
                    }
                }
                OutlinedButton(
                    onClick = { onStartThread(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Without Project")
                }
            }

            if (threadGroups.isEmpty()) {
                Text(
                    text = "No conversations yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                threadGroups.forEach { group ->
                    val hierarchyRows = flattenThreadHierarchy(
                        threads = group.threads,
                        collapsedParentIds = collapsedSubagentParentIds.value
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${group.label} (${group.threads.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (group.kind == ThreadProjectGroupKind.PROJECT && group.projectPath != null) {
                            val liveGroupThreadIds = group.threads.filterNot { it.isArchived }.map { it.id }
                            if (liveGroupThreadIds.size > 1) {
                                OutlinedButton(
                                    onClick = { onArchiveProjectGroup(liveGroupThreadIds) }
                                ) {
                                    Text("Archive Group")
                                }
                            }
                        }
                    }
                    hierarchyRows.forEach { hierarchyRow ->
                        val thread = hierarchyRow.thread
                        ThreadRow(
                            thread = thread,
                            isSelected = selectedThreadId == thread.id,
                            onClick = { onOpenThread(thread.id) },
                            depth = hierarchyRow.depth,
                            childCount = hierarchyRow.childCount,
                            isChildrenExpanded = !collapsedSubagentParentIds.value.contains(thread.id),
                            onToggleChildren = if (hierarchyRow.childCount > 0) {
                                {
                                    collapsedSubagentParentIds.value = collapsedSubagentParentIds.value.toMutableSet().also { ids ->
                                        if (!ids.add(thread.id)) {
                                            ids.remove(thread.id)
                                        }
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }

        if (selectedThread != null) {
            SectionCard(
                title = "Selected Chat",
                subtitle = "Rename or archive the current conversation."
            ) {
                if (isRenameMode) {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val normalizedName = renameInput.trim()
                                if (normalizedName.isNotEmpty()) {
                                    onRenameThread(selectedThread.id, normalizedName)
                                    isRenameMode = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = {
                                renameInput = selectedThread.displayTitle
                                isRenameMode = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            renameInput = selectedThread.displayTitle
                            isRenameMode = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rename")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (selectedThread.isArchived) {
                                onUnarchiveThread(selectedThread.id)
                            } else {
                                onArchiveThread(selectedThread.id)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selectedThread.isArchived) "Restore" else "Archive")
                    }
                    OutlinedButton(
                        onClick = { onDeleteThreadLocally(selectedThread.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }

        SectionCard(
            title = "Workspace",
            subtitle = "Refresh, git, and connection."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAutoRefreshChanged(!autoRefreshEnabled) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (autoRefreshEnabled) "Auto On" else "Auto Off")
                }
                OutlinedButton(
                    onClick = onRefreshWorkspace,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onGitDiff, modifier = Modifier.weight(1f)) {
                    Text("Diff")
                }
                OutlinedButton(onClick = onGitCommit, modifier = Modifier.weight(1f)) {
                    Text("Commit")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onGitCommitAndPush, modifier = Modifier.weight(1f)) {
                    Text("Commit & Push")
                }
                OutlinedButton(onClick = onGitPull, modifier = Modifier.weight(1f)) {
                    Text("Pull")
                }
                OutlinedButton(onClick = onGitPush, modifier = Modifier.weight(1f)) {
                    Text("Push")
                }
            }
            Text(
                text = rateLimitInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ciStatus.isNotBlank()) {
                Text(
                    text = ciStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun SidebarIdentityHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.original_author_icon),
                contentDescription = "Remodex",
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(28.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "Remodex",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        SmallChip(text = "⚙", selected = false, onClick = onOpenSettings)
    }
}
