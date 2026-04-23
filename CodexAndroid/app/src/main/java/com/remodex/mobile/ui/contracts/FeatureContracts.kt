package com.remodex.mobile.ui.contracts

data class ShellState(
    val route: String,
    val isConnected: Boolean,
    val isSidebarOpen: Boolean,
    val isSettingsOpen: Boolean
)

interface ShellActions {
    fun openSidebar()
    fun closeSidebar()
    fun openSettings()
    fun closeSettings()
    fun openPairing()
}

data class SidebarState(
    val searchQuery: String,
    val selectedThreadId: String?,
    val hasArchivedSection: Boolean
)

interface SidebarActions {
    fun setSearchQuery(query: String)
    suspend fun openThread(threadId: String)
    suspend fun startThread(projectPath: String?)
    suspend fun archiveThread(threadId: String)
    suspend fun unarchiveThread(threadId: String)
}

data class TimelineState(
    val selectedThreadId: String?,
    val messageCount: Int,
    val isRunning: Boolean
)

interface TimelineActions {
    suspend fun refreshActiveTimeline()
    suspend fun refreshThreads()
    suspend fun interruptActiveTurn()
}

data class ComposerState(
    val input: String,
    val isDispatching: Boolean,
    val selectedModel: String,
    val selectedReasoningEffort: String
)

interface ComposerActions {
    fun updateInput(input: String)
    suspend fun send()
    suspend fun stop()
    fun switchModel(model: String)
    fun switchReasoningEffort(effort: String)
}

data class SettingsState(
    val toneMode: String,
    val fontStyle: String,
    val loggerLevel: String,
    val loggerMaxLines: Int
)

interface SettingsActions {
    fun setToneMode(mode: String)
    fun setFontStyle(style: String)
    fun setLoggerLevel(level: String)
    fun setLoggerMaxLines(maxLines: Int)
    suspend fun disconnect()
}
