package com.remodex.mobile.service.delegates

import com.remodex.mobile.service.CodexService

interface CodexConnectionDelegate {
    suspend fun reconnect()
    suspend fun disconnect()
}

interface CodexRuntimeDelegate {
    suspend fun refreshRateLimitInfo()
    suspend fun refreshModels()
    suspend fun refreshPendingPermissions()
}

interface CodexIncomingDelegate {
    suspend fun refreshThreads()
    suspend fun refreshActiveThreadTimeline()
}

interface CodexThreadTurnDelegate {
    suspend fun startThread(projectPath: String?)
    suspend fun openThread(threadId: String)
    suspend fun interruptActiveTurn()
}

interface CodexGitAccountDelegate {
    suspend fun refreshGitStatus()
    suspend fun refreshGitBranches()
    suspend fun refreshCiStatus()
}

interface CodexNotificationDelegate {
    suspend fun forceRefreshWorkspace()
}

class DefaultCodexConnectionDelegate(
    private val service: CodexService
) : CodexConnectionDelegate {
    override suspend fun reconnect() = service.reconnect()
    override suspend fun disconnect() = service.disconnect()
}

class DefaultCodexRuntimeDelegate(
    private val service: CodexService
) : CodexRuntimeDelegate {
    override suspend fun refreshRateLimitInfo() = service.refreshRateLimitInfo(silentStatus = true)
    override suspend fun refreshModels() = service.refreshModels(silentStatus = true)
    override suspend fun refreshPendingPermissions() = service.refreshPendingPermissions(silentStatus = true)
}

class DefaultCodexIncomingDelegate(
    private val service: CodexService
) : CodexIncomingDelegate {
    override suspend fun refreshThreads() = service.refreshThreads(silentStatus = true, includeTimeline = false)
    override suspend fun refreshActiveThreadTimeline() = service.refreshActiveThreadTimeline(silentStatus = true)
}

class DefaultCodexThreadTurnDelegate(
    private val service: CodexService
) : CodexThreadTurnDelegate {
    override suspend fun startThread(projectPath: String?) = service.startThread(preferredProjectPath = projectPath)
    override suspend fun openThread(threadId: String) = service.openThread(threadId)
    override suspend fun interruptActiveTurn() = service.interruptActiveTurn()
}

class DefaultCodexGitAccountDelegate(
    private val service: CodexService
) : CodexGitAccountDelegate {
    override suspend fun refreshGitStatus() = service.refreshGitStatus(silentStatus = true)
    override suspend fun refreshGitBranches() = service.refreshGitBranches(silentStatus = true)
    override suspend fun refreshCiStatus() = service.refreshCiStatus(silentStatus = true)
}

class DefaultCodexNotificationDelegate(
    private val service: CodexService
) : CodexNotificationDelegate {
    override suspend fun forceRefreshWorkspace() = service.forceRefreshWorkspace()
}
