package com.remodex.mobile.ui.parity

import com.remodex.mobile.service.CodexService
import com.remodex.mobile.service.FileAutocompleteMatch
import com.remodex.mobile.service.ReviewTarget
import com.remodex.mobile.service.SkillSuggestion
import com.remodex.mobile.service.TurnImageAttachment
import com.remodex.mobile.ui.contracts.ComposerActions
import com.remodex.mobile.ui.contracts.SettingsActions
import com.remodex.mobile.ui.contracts.SidebarActions
import com.remodex.mobile.ui.contracts.TimelineActions

interface WorkspaceActionHandler : SidebarActions, TimelineActions, ComposerActions, SettingsActions {
    override suspend fun refreshThreads()
    suspend fun refreshActiveThreadTimeline()
    suspend fun refreshGitStatus()
    suspend fun refreshRateLimitInfo()
    suspend fun reconcileThreadRunningState(threadId: String?)
    suspend fun fuzzyFileSearch(query: String, roots: List<String>, limit: Int): List<FileAutocompleteMatch>
    suspend fun listSkills(cwds: List<String>, forceReload: Boolean, limit: Int): List<SkillSuggestion>
    suspend fun sendTurnStart(inputText: String, attachments: List<TurnImageAttachment>)
    override suspend fun openThread(threadId: String)
    override suspend fun startThread(projectPath: String?)
    suspend fun forceRefreshWorkspace()
    suspend fun gitDiff(): String
    suspend fun gitCommitAndPush(message: String?)
    suspend fun gitPull()
    suspend fun gitPush()
    override suspend fun archiveThread(threadId: String)
    override suspend fun unarchiveThread(threadId: String)
    suspend fun deleteThreadLocally(threadId: String)
    suspend fun archiveThreadGroup(threadIds: List<String>)
    suspend fun renameThread(threadId: String, title: String)
    override suspend fun disconnect()
    suspend fun checkoutGitBranch(branch: String)
    suspend fun grantPermission(id: String, allow: Boolean)
    suspend fun threadFork(targetProjectPath: String? = null)
    override suspend fun interruptActiveTurn()
    suspend fun reconnect()
    fun currentPairingMacDeviceId(): String?
    fun isThreadRunning(threadId: String?): Boolean
    override fun updateInput(input: String) = Unit
    override suspend fun send() = Unit
    override suspend fun stop() = interruptActiveTurn()
    override fun switchModel(model: String) = Unit
    override fun switchReasoningEffort(effort: String) = Unit
    override fun setSearchQuery(query: String) = Unit
    override fun setToneMode(mode: String) = Unit
    override fun setFontStyle(style: String) = Unit
    override fun setLoggerLevel(level: String) = Unit
    override fun setLoggerMaxLines(maxLines: Int) = Unit
    override suspend fun refreshActiveTimeline() = refreshActiveThreadTimeline()
}

class ServiceWorkspaceActionHandler(
    private val service: CodexService
) : WorkspaceActionHandler {
    override suspend fun refreshThreads() {
        service.refreshThreads(silentStatus = true, includeTimeline = false)
    }

    override suspend fun refreshActiveThreadTimeline() {
        service.refreshActiveThreadTimeline(silentStatus = true)
    }

    override suspend fun refreshGitStatus() {
        service.refreshGitStatus(silentStatus = true)
    }

    override suspend fun refreshRateLimitInfo() {
        service.refreshRateLimitInfo(silentStatus = true)
    }

    override suspend fun reconcileThreadRunningState(threadId: String?) {
        service.reconcileThreadRunningState(threadId)
    }

    override suspend fun fuzzyFileSearch(query: String, roots: List<String>, limit: Int): List<FileAutocompleteMatch> {
        return service.fuzzyFileSearch(query = query, roots = roots, limit = limit)
    }

    override suspend fun listSkills(cwds: List<String>, forceReload: Boolean, limit: Int): List<SkillSuggestion> {
        return service.listSkills(cwds = cwds, forceReload = forceReload, limit = limit)
    }

    override suspend fun sendTurnStart(inputText: String, attachments: List<TurnImageAttachment>) {
        service.sendTurnStart(inputText = inputText, attachments = attachments)
    }

    override suspend fun openThread(threadId: String) {
        service.openThread(threadId)
    }

    override suspend fun startThread(projectPath: String?) {
        service.startThread(preferredProjectPath = projectPath)
    }

    override suspend fun forceRefreshWorkspace() {
        service.forceRefreshWorkspace()
    }

    override suspend fun gitDiff(): String = service.gitDiff()

    override suspend fun gitCommitAndPush(message: String?) {
        service.gitCommitAndPush(message)
    }

    override suspend fun gitPull() {
        service.gitPull()
    }

    override suspend fun gitPush() {
        service.gitPush()
    }

    override suspend fun archiveThread(threadId: String) {
        service.archiveThread(threadId)
    }

    override suspend fun unarchiveThread(threadId: String) {
        service.unarchiveThread(threadId)
    }

    override suspend fun deleteThreadLocally(threadId: String) {
        service.deleteThreadLocally(threadId)
    }

    override suspend fun archiveThreadGroup(threadIds: List<String>) {
        service.archiveThreadGroup(threadIds)
    }

    override suspend fun renameThread(threadId: String, title: String) {
        service.renameThread(threadId = threadId, newName = title)
    }

    override suspend fun disconnect() {
        service.disconnect()
    }

    override suspend fun checkoutGitBranch(branch: String) {
        service.checkoutGitBranch(branch)
    }

    override suspend fun grantPermission(id: String, allow: Boolean) {
        service.grantPermission(permissionId = id, allow = allow)
    }

    override suspend fun threadFork(targetProjectPath: String?) {
        service.threadFork(targetProjectPath = targetProjectPath)
    }

    override suspend fun interruptActiveTurn() {
        service.interruptActiveTurn()
    }

    override suspend fun reconnect() {
        service.reconnect()
    }

    override fun currentPairingMacDeviceId(): String? = service.currentPairing()?.macDeviceId

    override fun isThreadRunning(threadId: String?): Boolean = service.isThreadRunning(threadId)
}
