package com.remodex.mobile.service

import com.remodex.mobile.model.RpcMessage
import com.remodex.mobile.model.RpcError
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.model.normalizeFilesystemProjectPath
import com.remodex.mobile.service.logging.AppLogger
import com.remodex.mobile.service.push.PushRegistrationPayload
import com.remodex.mobile.service.secure.CodexSecureTransport
import com.remodex.mobile.service.transport.FixtureRpcTransport
import com.remodex.mobile.service.transport.RealSecureRelayRpcTransport
import com.remodex.mobile.service.transport.RpcTransport
import com.remodex.mobile.service.transport.RpcTransportParser
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Paired : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

enum class ServiceEventKind {
    WORK_STATUS_CHANGED,
    PERMISSION_REQUIRED,
    RATE_LIMIT_HIT,
    GIT_ACTION,
    CI_CD_DONE
}

data class ServiceEvent(
    val kind: ServiceEventKind,
    val title: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class PendingPermissionRequest(
    val id: String,
    val title: String,
    val summary: String?
)

data class FileAutocompleteMatch(
    val path: String,
    val fileName: String
)

data class SkillSuggestion(
    val id: String,
    val name: String,
    val description: String?,
    val path: String?,
    val enabled: Boolean
)

data class TurnImageAttachment(
    val dataUrl: String,
    val label: String? = null
)

enum class ReviewTarget {
    UNCOMMITTED_CHANGES,
    BASE_BRANCH
}

enum class BridgeManagedAccountStatus {
    UNKNOWN,
    AUTHENTICATED,
    NOT_LOGGED_IN,
    LOGIN_IN_PROGRESS,
    REAUTH_REQUIRED
}

enum class RecoveryAccessoryStatus {
    INTERRUPTED,
    RECONNECTING,
    ACTION_REQUIRED,
    SYNCING
}

data class RecoveryAccessorySnapshot(
    val title: String,
    val summary: String,
    val detail: String? = null,
    val status: RecoveryAccessoryStatus,
    val actionLabel: String? = null
)

enum class VoiceFailureReason {
    RECONNECT_REQUIRED,
    BRIDGE_SESSION_UNSUPPORTED,
    MAC_LOGIN_REQUIRED,
    MAC_REAUTH_REQUIRED,
    VOICE_SYNC_IN_PROGRESS,
    CHATGPT_REQUIRED,
    MICROPHONE_PERMISSION_REQUIRED,
    MICROPHONE_UNAVAILABLE,
    RECORDER_UNAVAILABLE,
    GENERIC
}

class CodexService(
    private val secureTransport: CodexSecureTransport = CodexSecureTransport(),
    private val parser: RpcTransportParser = RpcTransportParser(),
    private val pairingStore: PairingStateStore? = null,
    private val fixtureTransportFactory: () -> RpcTransport = { FixtureRpcTransport() },
    private val liveTransportFactory: (PairingPayload, CodexSecureTransport) -> RpcTransport =
        { pairing, secure -> RealSecureRelayRpcTransport(pairing = pairing, secureTransport = secure) }
) {
    companion object {
        private const val LOG_TAG = "CodexService"
    }

    private enum class ConnectionMode {
        NONE,
        FIXTURE,
        LIVE
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _threads = MutableStateFlow<List<ThreadSummary>>(emptyList())
    val threads: StateFlow<List<ThreadSummary>> = _threads

    private val _timeline = MutableStateFlow<List<TimelineEntry>>(emptyList())
    val timeline: StateFlow<List<TimelineEntry>> = _timeline
    private val timelineByThread = mutableMapOf<String, List<TimelineEntry>>()

    private val _selectedThreadId = MutableStateFlow<String?>(null)
    val selectedThreadId: StateFlow<String?> = _selectedThreadId

    private val _status = MutableStateFlow("Waiting for pairing.")
    val status: StateFlow<String> = _status

    private val _gitStatusSummary = MutableStateFlow("Git status not loaded.")
    val gitStatusSummary: StateFlow<String> = _gitStatusSummary

    private val _gitBranches = MutableStateFlow<List<String>>(emptyList())
    val gitBranches: StateFlow<List<String>> = _gitBranches

    private val _currentProjectPath = MutableStateFlow("Project path not resolved.")
    val currentProjectPath: StateFlow<String> = _currentProjectPath

    private val _availableModels = MutableStateFlow(listOf("gpt-5.4", "gpt-5.4-mini", "gpt-5.3-codex"))
    val availableModels: StateFlow<List<String>> = _availableModels

    private val _selectedModel = MutableStateFlow("gpt-5.4")
    val selectedModel: StateFlow<String> = _selectedModel

    private val _availableReasoningEfforts = MutableStateFlow(listOf("low", "medium", "high"))
    val availableReasoningEfforts: StateFlow<List<String>> = _availableReasoningEfforts

    private val _selectedReasoningEffort = MutableStateFlow("medium")
    val selectedReasoningEffort: StateFlow<String> = _selectedReasoningEffort

    private val _pendingPermissions = MutableStateFlow<List<PendingPermissionRequest>>(emptyList())
    val pendingPermissions: StateFlow<List<PendingPermissionRequest>> = _pendingPermissions

    private val _rateLimitInfo = MutableStateFlow("Rate limit info not loaded.")
    val rateLimitInfo: StateFlow<String> = _rateLimitInfo

    private val _ciStatus = MutableStateFlow("")
    val ciStatus: StateFlow<String> = _ciStatus

    private val _gitActionStatus = MutableStateFlow<String?>(null)
    val gitActionStatus: StateFlow<String?> = _gitActionStatus

    private val _bridgeInstalledVersion = MutableStateFlow<String?>(null)
    val bridgeInstalledVersion: StateFlow<String?> = _bridgeInstalledVersion

    private val _latestBridgePackageVersion = MutableStateFlow<String?>(null)
    val latestBridgePackageVersion: StateFlow<String?> = _latestBridgePackageVersion

    private val _gptAccountStatus = MutableStateFlow(BridgeManagedAccountStatus.UNKNOWN)
    val gptAccountStatus: StateFlow<BridgeManagedAccountStatus> = _gptAccountStatus

    private val _gptAccountEmail = MutableStateFlow<String?>(null)
    val gptAccountEmail: StateFlow<String?> = _gptAccountEmail

    private val _voiceRecoverySnapshot = MutableStateFlow<RecoveryAccessorySnapshot?>(null)
    val voiceRecoverySnapshot: StateFlow<RecoveryAccessorySnapshot?> = _voiceRecoverySnapshot

    private val _events = MutableSharedFlow<ServiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServiceEvent> = _events

    private val activeTurnIdByThread = mutableMapOf<String, String>()
    private val projectPathByThread = mutableMapOf<String, String>()
    private val locallyArchivedThreadIds = mutableSetOf<String>()
    private val locallyDeletedThreadIds = mutableSetOf<String>()
    private val rpcMethodAliasCache = mutableMapOf<String, String>()
    private val unavailableMethodKeyCache = mutableSetOf<String>()
    private val resumedThreadIds = mutableSetOf<String>()
    private val sessionInitMutex = Mutex()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ciHttpClient = OkHttpClient()
    private val ciJson = Json { ignoreUnknownKeys = true }
    private var rpcTransport: RpcTransport = fixtureTransportFactory()
    private var activeTransportLabel: String = "none"
    private var activeConnectionMode: ConnectionMode = ConnectionMode.NONE
    private var supportsBridgeVoiceAuth: Boolean = true
    private var isAppInForeground: Boolean = false
    private var lastOptionalBridgeUpdateVersion: String? = null

    init {
        val persistedPairing = pairingStore?.load()
        if (persistedPairing != null) {
            secureTransport.rememberPairing(persistedPairing)
            _connectionState.value = ConnectionState.Paired
            AppLogger.info(
                LOG_TAG,
                "Loaded saved pairing for mac=${persistedPairing.macDeviceId} relay=${persistedPairing.relayUrl}."
            )
            setStatus("Loaded saved pairing for ${persistedPairing.macDeviceId}.")
        } else {
            AppLogger.info(LOG_TAG, "No saved pairing found in local store.")
        }
    }

    fun rememberPairing(payload: PairingPayload) {
        secureTransport.rememberPairing(payload)
        pairingStore?.save(payload)
        _connectionState.value = ConnectionState.Paired
        AppLogger.info(
            LOG_TAG,
            "Pairing remembered for mac=${payload.macDeviceId} relay=${payload.relayUrl} expiresAt=${payload.expiresAt}."
        )
        setStatus("Pairing saved for ${payload.macDeviceId}.", notify = true)
    }

    fun currentPairing(): PairingPayload? = secureTransport.currentPairing()

    fun forgetPairing() {
        AppLogger.info(LOG_TAG, "forgetPairing requested.")
        rpcTransport.setServerMessageListener(null)
        serviceScope.launch {
            runCatching { rpcTransport.close() }
        }
        secureTransport.clearPairing()
        pairingStore?.clear()
        activeConnectionMode = ConnectionMode.NONE
        _connectionState.value = ConnectionState.Disconnected
        _threads.value = emptyList()
        _selectedThreadId.value = null
        _timeline.value = emptyList()
        timelineByThread.clear()
        _gitStatusSummary.value = "Git status not loaded."
        _gitBranches.value = emptyList()
        _currentProjectPath.value = "Project path not resolved."
        _gitActionStatus.value = null
        _bridgeInstalledVersion.value = null
        _latestBridgePackageVersion.value = null
        _gptAccountStatus.value = BridgeManagedAccountStatus.UNKNOWN
        _gptAccountEmail.value = null
        _voiceRecoverySnapshot.value = null
        supportsBridgeVoiceAuth = true
        activeTurnIdByThread.clear()
        timelineByThread.clear()
        projectPathByThread.clear()
        resumedThreadIds.clear()
        locallyArchivedThreadIds.clear()
        locallyDeletedThreadIds.clear()
        rpcMethodAliasCache.clear()
        unavailableMethodKeyCache.clear()
        setStatus("Pairing removed. Scan a new QR code to reconnect.", notify = true)
    }

    suspend fun connectWithFixture() {
        connect(
            transport = fixtureTransportFactory(),
            modeLabel = "demo fixture",
            connectionMode = ConnectionMode.FIXTURE
        )
    }

    suspend fun connectLive() {
        val pairing = secureTransport.currentPairing()
        if (pairing == null) {
            AppLogger.warn(LOG_TAG, "connectLive rejected because pairing is missing.")
            _connectionState.value = ConnectionState.Failed("Pairing is missing.")
            setStatus("Pair first.", notify = true)
            return
        }
        AppLogger.info(
            LOG_TAG,
            "connectLive starting for mac=${pairing.macDeviceId} relay=${pairing.relayUrl}."
        )
        connect(
            transport = liveTransportFactory(pairing, secureTransport),
            modeLabel = "live secure relay",
            connectionMode = ConnectionMode.LIVE
        )
    }

    private suspend fun connect(transport: RpcTransport, modeLabel: String, connectionMode: ConnectionMode) {
        val pairing = secureTransport.currentPairing()
        if (pairing == null) {
            AppLogger.warn(LOG_TAG, "connect($modeLabel) rejected because pairing is missing.")
            _connectionState.value = ConnectionState.Failed("Pairing is missing.")
            setStatus("Pair first.", notify = true)
            return
        }

        AppLogger.info(
            LOG_TAG,
            "connect($modeLabel) begin relay=${pairing.relayUrl} mac=${pairing.macDeviceId}."
        )
        _connectionState.value = ConnectionState.Connecting
        setStatus("Connecting to ${pairing.relayUrl} via $modeLabel.", notify = true)

        try {
            runCatching { rpcTransport.close() }
            rpcTransport = transport
            bindTransportListener(rpcTransport)
            rpcMethodAliasCache.clear()
            unavailableMethodKeyCache.clear()
            resumedThreadIds.clear()
            rpcTransport.open()
            initializeSession()
            activeTransportLabel = modeLabel
            activeConnectionMode = connectionMode
            _connectionState.value = ConnectionState.Connected

            refreshThreads(silentStatus = true, includeTimeline = false)
            runBootstrapRefresh("git/status") { refreshGitStatus(silentStatus = true) }
            runBootstrapRefresh("git/branches") { refreshGitBranches(silentStatus = true) }
            runBootstrapRefresh("rate_limit/status") { refreshRateLimitInfo(silentStatus = true) }
            runBootstrapRefresh("permissions/pending") { refreshPendingPermissions(silentStatus = true) }
            runBootstrapRefresh("models/list") { refreshModels(silentStatus = true) }
            runBootstrapRefresh("ci/status") { refreshCiStatus(silentStatus = true) }
            runBootstrapRefresh("account/status") { refreshBridgeManagedState(allowAvailableBridgeUpdatePrompt = false) }

            AppLogger.info(LOG_TAG, "connect($modeLabel) completed successfully.")
            setStatus("Connected via $modeLabel.", notify = true)
        } catch (error: Throwable) {
            activeConnectionMode = ConnectionMode.NONE
            AppLogger.error(LOG_TAG, "connect($modeLabel) failed.", error)
            _connectionState.value = ConnectionState.Failed(error.message ?: "Unknown connection failure.")
            setStatus("Connect failed: ${error.message ?: "unknown error"}", notify = true)
        }
    }

    fun setForegroundState(isForeground: Boolean) {
        val wasForeground = isAppInForeground
        isAppInForeground = isForeground
        if (!isForeground || wasForeground == isForeground) {
            return
        }
        serviceScope.launch {
            runCatching {
                refreshBridgeVersionState(allowAvailableBridgeUpdatePrompt = true)
            }
        }
    }

    private suspend fun initializeSession() {
        val modernParams = JsonObject(
            mapOf(
                "clientInfo" to JsonObject(
                    mapOf(
                        "name" to JsonPrimitive("codexmobile_android"),
                        "title" to JsonPrimitive("Remodex"),
                        "version" to JsonPrimitive("0.1.0")
                    )
                ),
                "capabilities" to JsonObject(
                    mapOf(
                        "experimentalApi" to JsonPrimitive(true)
                    )
                )
            )
        )

        val initializeResponse = rpcTransport.request(
            method = "initialize",
            params = modernParams
        )
        val initializeError = initializeResponse.error
        if (initializeError != null) {
            if (isAlreadyInitializedError(initializeError)) {
                AppLogger.info(
                    LOG_TAG,
                    "initialize reported already initialized; treating as success."
                )
            } else if (shouldRetryInitializeWithoutCapabilities(initializeError)) {
                AppLogger.warn(
                    LOG_TAG,
                    "initialize with capabilities failed; retrying with legacy params. code=${initializeError.code} message=${initializeError.message}"
                )
                val legacyResponse = rpcTransport.request(
                    method = "initialize",
                    params = JsonObject(
                        mapOf(
                            "clientInfo" to JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive("codexmobile_android"),
                                    "title" to JsonPrimitive("Remodex"),
                                    "version" to JsonPrimitive("0.1.0")
                                )
                            )
                        )
                    )
                )
                val legacyError = legacyResponse.error
                if (legacyError != null && !isAlreadyInitializedError(legacyError)) {
                    throwIfRpcError(legacyResponse, "initialize")
                }
            } else {
                throwIfRpcError(initializeResponse, "initialize")
            }
        }
        sendInitializedNotification()
        AppLogger.info(LOG_TAG, "initialize + initialized completed for active relay session.")
    }

    private suspend fun sendInitializedNotification() {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            runCatching {
                rpcTransport.notify(
                    method = "initialized",
                    params = JsonObject(emptyMap())
                )
            }.onSuccess {
                return
            }.onFailure { error ->
                lastError = error
                AppLogger.warn(
                    LOG_TAG,
                    "initialized notification failed (attempt ${attempt + 1}/2).",
                    error
                )
            }
        }
        throw lastError ?: IllegalStateException("initialized notification failed.")
    }

    private suspend fun reinitializeSession(reason: String) {
        sessionInitMutex.withLock {
            AppLogger.warn(LOG_TAG, "Re-initializing session: $reason")
            initializeSession()
        }
    }

    private suspend fun requestRpc(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        allowReinitialize: Boolean = true
    ): RpcMessage {
        val response = rpcTransport.request(method = method, params = params)
        val rpcError = response.error ?: return response

        if (allowReinitialize && shouldReinitializeAfterRpcError(method, rpcError)) {
            reinitializeSession(reason = "$method failed with Not initialized")
            return rpcTransport.request(method = method, params = params)
        }

        return response
    }

    private fun shouldReinitializeAfterRpcError(method: String, rpcError: RpcError): Boolean {
        if (method == "initialize") {
            return false
        }
        if (rpcError.code != -32600 && rpcError.code != -32000) {
            return false
        }
        val message = rpcError.message.lowercase()
        return message.contains("not initialized")
            || message.contains("session not initialized")
            || message.contains("client not initialized")
    }

    private suspend fun runBootstrapRefresh(label: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { error ->
            AppLogger.warn(
                LOG_TAG,
                "bootstrap refresh '$label' failed; keeping session connected.",
                error
            )
        }
    }

    private fun bindTransportListener(transport: RpcTransport) {
        transport.setServerMessageListener { message ->
            serviceScope.launch {
                runCatching {
                    handleServerMessage(message)
                }.onFailure { error ->
                    AppLogger.warn(
                        LOG_TAG,
                        "Ignoring inbound server message failure method=${message.method.orEmpty()}",
                        error
                    )
                }
            }
        }
    }

    private suspend fun handleServerMessage(message: RpcMessage) {
        val rawMethod = message.method?.trim().orEmpty()
        if (rawMethod.isEmpty()) {
            return
        }
        val params = message.params as? JsonObject ?: JsonObject(emptyMap())
        val method = normalizeIncomingMethodName(rawMethod, params)
        AppLogger.info(LOG_TAG, "Inbound server message method=$method")
        when (method) {
            "thread/started" -> handleThreadStartedNotification(params)
            "thread/status/changed" -> handleThreadStatusChangedNotification(params)
            "thread/name/updated" -> handleThreadNameUpdatedNotification(params)
            "turn/started" -> handleTurnStartedNotification(params)
            "turn/completed" -> handleTurnCompletedNotification(params)
            "turn/failed" -> handleTurnFailedNotification(params)
            "turn/plan/updated",
            "item/plan/delta" -> handleTypedDeltaNotification(
                params = params,
                type = "plan",
                append = true
            )
            "item/reasoning/summaryTextDelta",
            "item/reasoning/summaryPartAdded",
            "item/reasoning/textDelta" -> handleTypedDeltaNotification(
                params = params,
                type = "reasoning",
                append = true
            )
            "item/fileChange/outputDelta" -> handleTypedDeltaNotification(
                params = params,
                type = "filechange",
                append = true
            )
            "item/toolCall/outputDelta",
            "item/toolCall/output_delta",
            "item/tool_call/outputDelta",
            "item/tool_call/output_delta" -> handleTypedDeltaNotification(
                params = params,
                type = "toolcall",
                append = true
            )
            "item/commandExecution/outputDelta",
            "item/command_execution/outputDelta",
            "item/commandExecution/terminalInteraction",
            "item/command_execution/terminalInteraction" -> handleTypedDeltaNotification(
                params = params,
                type = "commandexecution",
                append = true
            )
            "turn/diff/updated",
            "codex/event/turn_diff_updated",
            "codex/event/turn_diff",
            "codex/event/patch_apply_begin",
            "codex/event/patch_apply_end" -> handleTypedDeltaNotification(
                params = params,
                type = "diff",
                append = false
            )
            "item/agentMessage/delta",
            "codex/event/agent_message_content_delta",
            "codex/event/agent_message_delta" -> handleAssistantDeltaNotification(params)
            "codex/event/user_message" -> handleUserMessageNotification(params)
            "thread/tokenUsage/updated",
            "account/rateLimits/updated" -> runCatching {
                refreshRateLimitInfo(silentStatus = true)
            }
            "account/updated",
            "account/login/completed" -> {
                val accountLabel = params.string("email", "name", "account", "status")
                if (!accountLabel.isNullOrBlank()) {
                    setStatus("Account updated: $accountLabel")
                }
            }
            "item/completed",
            "codex/event/item_completed",
            "codex/event/agent_message" -> handleItemCompletedNotification(params)
            "item/started",
            "codex/event/item_started" -> handleItemStartedNotification(params)
            "item/tool/requestUserInput" -> {
                setStatus("User input required to continue tool execution.", notify = true, eventKind = ServiceEventKind.PERMISSION_REQUIRED)
            }
            "error",
            "codex/event/error" -> handleTurnFailedNotification(params)
            "serverRequest/resolved" -> {
                val resolution = params.string("status", "result", "message") ?: "resolved"
                setStatus("Server request resolved: $resolution")
            }
            else -> {
                if (method.startsWith("codex/event/")) {
                    handleGenericCodexEventNotification(method = method, params = params)
                }
            }
        }
    }

    private fun normalizeIncomingMethodName(method: String, params: JsonObject): String {
        val trimmed = method.trim()
        if (trimmed == "item/agent_message/delta") {
            return "item/agentMessage/delta"
        }
        if (trimmed == "codex/event") {
            val msgObject = params["msg"] as? JsonObject
            val eventName = params.string("event", "type")
                ?: msgObject?.string("event", "type")
            val normalizedEvent = normalizeNotificationType(eventName)
            if (normalizedEvent.isNotEmpty()) {
                return "codex/event/$normalizedEvent"
            }
        }
        return trimmed
    }

    private fun handleThreadStartedNotification(params: JsonObject) {
        val threadObject = (params["thread"] as? JsonObject) ?: params
        val parsedThread = parser.parseThreadSummaryObject(threadObject) ?: return
        locallyDeletedThreadIds.remove(parsedThread.id)
        locallyArchivedThreadIds.remove(parsedThread.id)
        upsertThreadSummary(parsedThread)
        updateThreadProjectPath(parsedThread.id, parsedThread.cwd)
        if (_selectedThreadId.value == null && !parsedThread.isArchived) {
            _selectedThreadId.value = parsedThread.id
            updateCurrentProjectPathForThread(parsedThread.id)
        }
        setStatus("Thread ${parsedThread.displayTitle} started via $activeTransportLabel.")
    }

    private fun handleThreadStatusChangedNotification(params: JsonObject) {
        val threadId = params.string("threadId", "thread_id", "id") ?: return
        val status = params.string("status", "state")?.lowercase().orEmpty()
        val archived = params.bool("archived", "isArchived", "is_archived")
        if (archived == true || status == "archived") {
            locallyArchivedThreadIds.add(threadId)
        } else if (archived == false || status == "unarchived" || status == "resumed") {
            locallyArchivedThreadIds.remove(threadId)
        }

        if (status == "resumed") {
            resumedThreadIds.add(threadId)
            setStatus("Thread $threadId resumed via $activeTransportLabel.")
        } else if (status.isNotBlank()) {
            setStatus("Thread $threadId status changed to $status via $activeTransportLabel.")
        }

        _threads.value = _threads.value.map { thread ->
            if (thread.id == threadId) {
                thread.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    isArchived = archived ?: (status == "archived")
                )
            } else {
                thread
            }
        }.sortedWith(threadSummaryComparator())
    }

    private fun handleThreadNameUpdatedNotification(params: JsonObject) {
        val threadId = params.string("threadId", "thread_id", "id") ?: return
        val name = params.string("name", "title") ?: return
        _threads.value = _threads.value.map { thread ->
            if (thread.id == threadId) {
                thread.copy(name = name, title = name, updatedAtMillis = System.currentTimeMillis())
            } else {
                thread
            }
        }.sortedWith(threadSummaryComparator())
        setStatus("Renamed thread $threadId via $activeTransportLabel.")
    }

    private fun handleTurnStartedNotification(params: JsonObject) {
        val threadId = resolveThreadIdFromNotification(params) ?: return
        val turnId = params.string("turnId", "turn_id", "id")
            ?: "pending-$threadId-${System.currentTimeMillis()}"
        activeTurnIdByThread[threadId] = turnId
        updateThreadTimestamp(threadId)
        setStatus("Turn started on $threadId via $activeTransportLabel.")
    }

    private fun handleTurnCompletedNotification(params: JsonObject) {
        val threadId = resolveThreadIdFromNotification(params)
        val turnId = params.string("turnId", "turn_id", "id")
        clearActiveTurnState(threadId = threadId, turnId = turnId)
        if (threadId != null) {
            updateThreadTimestamp(threadId)
        }
        setStatus("Turn completed${threadId?.let { " on $it" } ?: ""} via $activeTransportLabel.")
    }

    private fun handleTurnFailedNotification(params: JsonObject) {
        val threadId = resolveThreadIdFromNotification(params)
        val turnId = params.string("turnId", "turn_id", "id")
        clearActiveTurnState(threadId = threadId, turnId = turnId)
        val message = params.string("message", "error", "reason")
            ?: "Turn failed."
        if (threadId != null) {
            val entry = TimelineEntry(
                id = "turn-failed-${turnId ?: System.currentTimeMillis()}",
                threadId = threadId,
                turnId = turnId,
                type = "turnfailed",
                role = com.remodex.mobile.model.TimelineRole.SYSTEM,
                text = message
            )
            appendOrMergeTimelineEntry(entry = entry, append = false)
            updateThreadPreviewAndTimestamp(threadId = threadId, preview = message)
        }
        setStatus("Turn failed: $message", notify = true)
    }

    private fun handleAssistantDeltaNotification(params: JsonObject) {
        val threadId = resolveThreadIdFromNotification(params) ?: return
        val turnId = params.string("turnId", "turn_id")
        val text = extractNotificationText(params) ?: return
        val itemId = params.string("itemId", "item_id", "id")
            ?: "assistant-${threadId}-${turnId ?: "unknown"}"
        val entry = TimelineEntry(
            id = itemId,
            threadId = threadId,
            turnId = turnId,
            type = "assistantmessage",
            role = com.remodex.mobile.model.TimelineRole.ASSISTANT,
            text = text
        )
        appendOrMergeTimelineEntry(entry = entry, append = true)
        updateThreadPreviewAndTimestamp(threadId = threadId, preview = text)
    }

    private fun handleUserMessageNotification(params: JsonObject) {
        val threadId = resolveThreadIdFromNotification(params) ?: return
        val turnId = params.string("turnId", "turn_id")
        val text = extractNotificationText(params) ?: return
        val itemId = params.string("itemId", "item_id", "id")
            ?: "user-${threadId}-${turnId ?: "unknown"}-${text.hashCode()}"
        val entry = TimelineEntry(
            id = itemId,
            threadId = threadId,
            turnId = turnId,
            type = "usermessage",
            role = com.remodex.mobile.model.TimelineRole.USER,
            text = text
        )
        appendOrMergeTimelineEntry(entry = entry, append = false)
        updateThreadPreviewAndTimestamp(threadId = threadId, preview = text)
    }

    private fun handleTypedDeltaNotification(
        params: JsonObject,
        type: String,
        append: Boolean
    ) {
        val threadId = resolveThreadIdFromNotification(params) ?: return
        val turnId = params.string("turnId", "turn_id")
        val text = extractNotificationText(params) ?: timelineFallbackText(type)
        val itemId = params.string("itemId", "item_id", "id")
            ?: "$type-${threadId}-${turnId ?: "unknown"}"
        val entry = TimelineEntry(
            id = itemId,
            threadId = threadId,
            turnId = turnId,
            type = type,
            role = com.remodex.mobile.model.TimelineRole.SYSTEM,
            text = text
        )
        appendOrMergeTimelineEntry(entry = entry, append = append)
        updateThreadPreviewAndTimestamp(threadId = threadId, preview = text)
    }

    private fun handleItemStartedNotification(params: JsonObject) {
        val itemType = normalizeNotificationType(
            params.string("itemType", "item_type")
                ?: (params["item"] as? JsonObject)?.string("type")
                ?: params.string("type")
        )
        val normalizedType = itemType.ifEmpty { "status" }
        handleTypedDeltaNotification(
            params = params,
            type = normalizedType,
            append = false
        )
    }

    private fun handleItemCompletedNotification(params: JsonObject) {
        val itemType = normalizeNotificationType(
            params.string("itemType", "item_type")
                ?: (params["item"] as? JsonObject)?.string("type")
                ?: params.string("type")
        )
        val normalizedType = itemType.ifEmpty { "status" }
        handleTypedDeltaNotification(
            params = params,
            type = normalizedType,
            append = true
        )
    }

    private fun handleGenericCodexEventNotification(method: String, params: JsonObject) {
        val eventName = method.removePrefix("codex/event/")
        when (eventName) {
            "agent_message_content_delta",
            "agent_message_delta" -> handleAssistantDeltaNotification(params)
            "user_message" -> handleUserMessageNotification(params)
            "item_started" -> handleItemStartedNotification(params)
            "item_completed",
            "agent_message" -> handleItemCompletedNotification(params)
            "turn_diff_updated",
            "turn_diff",
            "patch_apply_begin",
            "patch_apply_end" -> handleTypedDeltaNotification(params, type = "diff", append = false)
            "exec_command_begin",
            "exec_command_output_delta",
            "exec_command_end",
            "background_event",
            "read",
            "search",
            "list_files" -> handleTypedDeltaNotification(params, type = "commandexecution", append = true)
            "error" -> handleTurnFailedNotification(params)
            else -> {
                val normalizedType = normalizeNotificationType(eventName).ifEmpty { "status" }
                handleTypedDeltaNotification(params, type = normalizedType, append = true)
            }
        }
    }

    private fun normalizeNotificationType(value: String?): String {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            .orEmpty()
    }

    private fun timelineFallbackText(type: String): String {
        return when (normalizeNotificationType(type)) {
            "plan" -> "Plan updated."
            "reasoning" -> "Thinking..."
            "toolcall" -> "Tool output updated."
            "filechange" -> "File changes updated."
            "commandexecution" -> "Command output updated."
            "diff" -> "Diff updated."
            else -> "Status updated."
        }
    }

    private fun appendOrMergeTimelineEntry(entry: TimelineEntry, append: Boolean) {
        val existingTimeline = timelineByThread[entry.threadId].orEmpty()
        val existingIndex = existingTimeline.indexOfFirst { item -> item.id == entry.id }
        val updatedTimeline = if (existingIndex >= 0) {
            existingTimeline.toMutableList().also { items ->
                val existing = items[existingIndex]
                items[existingIndex] = existing.copy(
                    text = if (append) existing.text + entry.text else entry.text
                )
            }
        } else {
            existingTimeline + entry
        }
        timelineByThread[entry.threadId] = updatedTimeline
        if (_selectedThreadId.value == entry.threadId) {
            _timeline.value = updatedTimeline
        }
    }

    private fun upsertThreadSummary(thread: ThreadSummary) {
        val updated = _threads.value.toMutableList()
        val existingIndex = updated.indexOfFirst { it.id == thread.id }
        if (existingIndex >= 0) {
            updated[existingIndex] = thread
        } else {
            updated += thread
        }
        _threads.value = updated.sortedWith(
            threadSummaryComparator()
        )
    }

    private fun updateThreadTimestamp(threadId: String) {
        _threads.value = _threads.value.map { thread ->
            if (thread.id == threadId) {
                thread.copy(updatedAtMillis = System.currentTimeMillis())
            } else {
                thread
            }
        }.sortedWith(threadSummaryComparator())
    }

    private fun updateThreadPreviewAndTimestamp(threadId: String, preview: String) {
        _threads.value = _threads.value.map { thread ->
            if (thread.id == threadId) {
                thread.copy(
                    preview = preview,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                thread
            }
        }.sortedWith(threadSummaryComparator())
    }

    private fun clearActiveTurnState(threadId: String?, turnId: String?) {
        if (!threadId.isNullOrBlank()) {
            val activeTurnId = activeTurnIdByThread[threadId]
            if (turnId.isNullOrBlank() || activeTurnId == turnId) {
                activeTurnIdByThread.remove(threadId)
            }
        }
        if (!turnId.isNullOrBlank()) {
            activeTurnIdByThread.entries
                .firstOrNull { (_, candidateTurnId) -> candidateTurnId == turnId }
                ?.key
                ?.let { key -> activeTurnIdByThread.remove(key) }
        }
    }

    private fun resolveThreadIdFromNotification(params: JsonObject): String? {
        params.string("threadId", "thread_id", "thread")?.let { return it }
        val threadObject = params["thread"] as? JsonObject
        threadObject?.string("id")?.let { return it }
        val itemObject = params["item"] as? JsonObject
        itemObject?.string("threadId", "thread_id")?.let { return it }
        val metaObject = params["metadata"] as? JsonObject
        metaObject?.string("threadId", "thread_id")?.let { return it }
        val msgObject = params["msg"] as? JsonObject
        msgObject?.string("threadId", "thread_id")?.let { return it }
        val turnId = params.string("turnId", "turn_id", "id")
        if (!turnId.isNullOrBlank()) {
            activeTurnIdByThread.entries.firstOrNull { (_, activeTurnId) ->
                activeTurnId == turnId
            }?.key?.let { return it }
        }
        val selectedThreadId = _selectedThreadId.value
        if (!selectedThreadId.isNullOrBlank()) {
            val activeThreads = _threads.value.filterNot { it.isArchived }
            if (activeThreads.size <= 1) {
                return selectedThreadId
            }
        }
        return null
    }

    private fun extractNotificationText(params: JsonObject): String? {
        params.string(
            "text",
            "message",
            "delta",
            "contentDelta",
            "summary",
            "status",
            "error",
            "unified_diff",
            "patch"
        )?.let { text ->
            if (text.isNotBlank()) {
                return text
            }
        }
        val msgObject = params["msg"] as? JsonObject
        msgObject?.string(
            "text",
            "message",
            "delta",
            "contentDelta",
            "summary",
            "status",
            "error",
            "unified_diff",
            "patch"
        )?.let { text ->
            if (text.isNotBlank()) {
                return text
            }
        }
        val itemObject = params["item"] as? JsonObject
        if (itemObject != null) {
            parser.parseTimelineEntry(
                threadId = resolveThreadIdFromNotification(params) ?: return null,
                turnId = params.string("turnId", "turn_id"),
                itemObject = itemObject
            )?.text?.let { return it }
        }
        val detailsObject = params["details"] as? JsonObject
        if (detailsObject != null) {
            detailsObject.string("text", "message", "summary", "command", "query", "path")?.let { text ->
                if (text.isNotBlank()) {
                    return text
                }
            }
        }
        val content = params["content"] as? JsonArray ?: return null
        return content.firstNotNullOfOrNull { element ->
            val part = element as? JsonObject ?: return@firstNotNullOfOrNull null
            part.string("text", "message", "delta", "summary", "status", "patch", "unified_diff")
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun threadSummaryComparator(): Comparator<ThreadSummary> {
        return compareByDescending<ThreadSummary> { it.updatedAtMillis ?: Long.MIN_VALUE }
            .thenBy { it.id }
    }

    suspend fun reconnect() {
        AppLogger.info(LOG_TAG, "reconnect requested with mode=$activeConnectionMode.")
        when (activeConnectionMode) {
            ConnectionMode.FIXTURE -> connectWithFixture()
            ConnectionMode.LIVE -> connectLive()
            ConnectionMode.NONE -> {
                AppLogger.warn(LOG_TAG, "reconnect unavailable because no previous connection mode exists.")
                _connectionState.value = ConnectionState.Failed("No previous connection mode.")
                setStatus("Reconnect unavailable. Connect first.", notify = true)
            }
        }
    }

    suspend fun disconnect() {
        AppLogger.info(LOG_TAG, "disconnect requested for mode=$activeConnectionMode.")
        rpcTransport.setServerMessageListener(null)
        runCatching { rpcTransport.close() }
        activeConnectionMode = ConnectionMode.NONE
        _connectionState.value = ConnectionState.Paired
        _timeline.value = emptyList()
        _voiceRecoverySnapshot.value = null
        _gitActionStatus.value = null
        activeTurnIdByThread.clear()
        projectPathByThread.clear()
        resumedThreadIds.clear()
        rpcMethodAliasCache.clear()
        unavailableMethodKeyCache.clear()
        AppLogger.info(LOG_TAG, "disconnect completed; connection state set back to PAIRED.")
        setStatus("Disconnected.", notify = true)
    }

    suspend fun startThread(preferredProjectPath: String? = null) {
        ensureConnected()
        val normalizedPreferredPath = normalizeProjectPath(preferredProjectPath)
        val params = if (normalizedPreferredPath == null) {
            JsonObject(emptyMap())
        } else {
            JsonObject(
                mapOf(
                    "cwd" to JsonPrimitive(normalizedPreferredPath)
                )
            )
        }
        val response = requestRpc(
            method = "thread/start",
            params = params
        )
        throwIfRpcError(response, "thread/start")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("thread/start result is missing.")
        val createdThread = resultObject["thread"] as? JsonObject
        val createdThreadId = createdThread?.string("id")
        refreshThreads(silentStatus = true, includeTimeline = false)
        if (!createdThreadId.isNullOrBlank()) {
            resumedThreadIds.add(createdThreadId)
            openThread(createdThreadId, silentStatus = true)
            setStatus(
                if (normalizedPreferredPath != null) {
                    "Created thread $createdThreadId in $normalizedPreferredPath via $activeTransportLabel."
                } else {
                    "Created thread $createdThreadId via $activeTransportLabel."
                },
                notify = true
            )
        } else {
            setStatus("Created thread via $activeTransportLabel.", notify = true)
        }
    }

    suspend fun refreshThreads(silentStatus: Boolean = false, includeTimeline: Boolean = true) {
        ensureConnected()
        val response = requestRpc(
            method = "thread/list",
            params = JsonObject(
                mapOf(
                    "sourceKinds" to JsonArray(
                        listOf("cli", "vscode", "appServer", "exec", "unknown").map(::JsonPrimitive)
                    )
                )
            )
        )
        throwIfRpcError(response, "thread/list")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("thread/list result is missing.")
        val liveThreads = parser.parseThreadList(resultObject, forceArchived = false)

        val archivedThreads = runCatching {
            val archivedResponse = requestRpc(
                method = "thread/list",
                params = JsonObject(
                    mapOf(
                        "archived" to JsonPrimitive(true),
                        "sourceKinds" to JsonArray(
                            listOf("cli", "vscode", "appServer", "exec", "unknown").map(::JsonPrimitive)
                        )
                    )
                )
            )
            throwIfRpcError(archivedResponse, "thread/list")
            val archivedResult = archivedResponse.result as? JsonObject ?: JsonObject(emptyMap())
            parser.parseThreadList(archivedResult, forceArchived = true)
        }.getOrDefault(emptyList())

        val mergedById = linkedMapOf<String, ThreadSummary>()
        (liveThreads + archivedThreads).forEach { thread ->
            mergedById[thread.id] = thread
        }

        val projectedThreads = mergedById.values
            .filterNot { locallyDeletedThreadIds.contains(it.id) }
            .map { thread ->
                if (locallyArchivedThreadIds.contains(thread.id)) {
                    thread.copy(isArchived = true)
                } else {
                    thread
                }
            }

        val parsedThreads = projectedThreads.sortedWith(
            compareByDescending<ThreadSummary> { it.updatedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.id }
        )
        _threads.value = parsedThreads
        val parsedThreadIds = parsedThreads.map { it.id }.toSet()
        projectPathByThread.keys.retainAll(parsedThreadIds)
        parsedThreads.forEach { thread ->
            updateThreadProjectPath(thread.id, thread.cwd)
        }

        val selectedThreadId = _selectedThreadId.value
        val activeThreads = parsedThreads.filterNot { it.isArchived }
        val resolvedSelection = when {
            activeThreads.isEmpty() -> null
            selectedThreadId != null && activeThreads.any { it.id == selectedThreadId } -> selectedThreadId
            else -> activeThreads.first().id
        }

        _selectedThreadId.value = resolvedSelection
        if (resolvedSelection != null) {
            updateCurrentProjectPathForThread(resolvedSelection)
            if (includeTimeline) {
                openThread(resolvedSelection, silentStatus = true)
            }
        } else {
            _timeline.value = emptyList()
        }

        if (!silentStatus) {
            setStatus("Loaded ${parsedThreads.size} thread(s) via $activeTransportLabel.")
        }
    }

    suspend fun refreshActiveThreadTimeline(silentStatus: Boolean = true) {
        ensureConnected()
        val threadId = _selectedThreadId.value ?: return
        openThread(threadId, silentStatus = silentStatus)
    }

    suspend fun openThread(threadId: String, silentStatus: Boolean = false) {
        ensureConnected()
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            return
        }
        _selectedThreadId.value = normalizedThreadId
        val response = requestRpc(
            method = "thread/read",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(normalizedThreadId),
                    "includeTurns" to JsonPrimitive(true)
                )
            )
        )
        runCatching {
            throwIfRpcError(response, "thread/read")
        }.onFailure { error ->
            if (shouldTreatAsMissingThread(error)) {
                AppLogger.warn(
                    LOG_TAG,
                    "thread/read reported missing thread=$normalizedThreadId; creating continuation thread.",
                    error
                )
                recoverMissingThreadAndResolveContinuation(normalizedThreadId)
                return
            }
            throw error
        }
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("thread/read result is missing.")
        val parsedTimeline = parser.parseThreadTimeline(resultObject)
        timelineByThread[normalizedThreadId] = parsedTimeline
        _timeline.value = parsedTimeline
        val activeTurnId = parseActiveTurnId(resultObject)
        if (activeTurnId != null) {
            activeTurnIdByThread[normalizedThreadId] = activeTurnId
        } else {
            activeTurnIdByThread.remove(normalizedThreadId)
        }

        val threadReadCwd = parseThreadReadCwd(resultObject)
        updateThreadProjectPath(normalizedThreadId, threadReadCwd)
        updateCurrentProjectPathForThread(normalizedThreadId)
        resumedThreadIds.add(normalizedThreadId)

        if (!silentStatus) {
            setStatus("Loaded ${parsedTimeline.size} timeline item(s) for $normalizedThreadId via $activeTransportLabel.")
        }
    }

    suspend fun renameThread(threadId: String, newName: String) {
        ensureConnected()
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            throw IllegalArgumentException("Thread id is required.")
        }
        val normalizedName = newName.trim()
        if (normalizedName.isEmpty()) {
            throw IllegalArgumentException("Thread name is required.")
        }

        val responsePair = requestFirstAvailable(
            methods = listOf("thread/name/set", "thread/rename", "thread/name"),
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(normalizedThreadId),
                    "name" to JsonPrimitive(normalizedName)
                )
            )
        )
        if (responsePair == null) {
            throw IllegalStateException("Relay does not support thread renaming.")
        }

        _threads.value = _threads.value.map { thread ->
            if (thread.id == normalizedThreadId) {
                thread.copy(name = normalizedName, title = normalizedName)
            } else {
                thread
            }
        }
        setStatus("Renamed thread $normalizedThreadId via $activeTransportLabel.", notify = true)
    }

    suspend fun archiveThread(threadId: String) {
        setThreadArchivedState(threadId = threadId, archived = true, refreshAfter = true)
        setStatus("Archived thread ${threadId.trim()} via $activeTransportLabel.", notify = true)
    }

    suspend fun unarchiveThread(threadId: String) {
        setThreadArchivedState(threadId = threadId, archived = false, refreshAfter = true)
        setStatus("Unarchived thread ${threadId.trim()} via $activeTransportLabel.", notify = true)
    }

    suspend fun archiveThreadGroup(threadIds: List<String>) {
        ensureConnected()
        val normalizedIds = threadIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedIds.isEmpty()) {
            return
        }
        normalizedIds.forEach { threadId ->
            runCatching {
                setThreadArchivedState(threadId = threadId, archived = true, refreshAfter = false)
            }
        }
        refreshThreads(silentStatus = true, includeTimeline = false)
        setStatus("Archived ${normalizedIds.size} thread(s) via $activeTransportLabel.", notify = true)
    }

    suspend fun deleteThreadLocally(threadId: String) {
        ensureConnected()
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            throw IllegalArgumentException("Thread id is required.")
        }
        locallyDeletedThreadIds.add(normalizedThreadId)
        locallyArchivedThreadIds.remove(normalizedThreadId)
        projectPathByThread.remove(normalizedThreadId)
        activeTurnIdByThread.remove(normalizedThreadId)
        timelineByThread.remove(normalizedThreadId)
        _threads.value = _threads.value.filterNot { it.id == normalizedThreadId }
        val selectedId = _selectedThreadId.value
        if (selectedId == normalizedThreadId) {
            val fallback = _threads.value.firstOrNull { !it.isArchived }?.id
            _selectedThreadId.value = fallback
            if (fallback == null) {
                _timeline.value = emptyList()
            } else {
                openThread(fallback, silentStatus = true)
            }
        }
        setStatus("Deleted thread $normalizedThreadId locally.", notify = true)
    }

    private suspend fun setThreadArchivedState(threadId: String, archived: Boolean, refreshAfter: Boolean) {
        ensureConnected()
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            throw IllegalArgumentException("Thread id is required.")
        }
        if (archived) {
            val responsePair = requestFirstAvailable(
                methods = listOf("thread/archive"),
                params = JsonObject(
                    mapOf(
                        "threadId" to JsonPrimitive(normalizedThreadId),
                        "unarchive" to JsonPrimitive(false)
                    )
                )
            )
            if (responsePair == null) {
                throw IllegalStateException("Relay does not support thread archive.")
            }
            locallyArchivedThreadIds.add(normalizedThreadId)
            locallyDeletedThreadIds.remove(normalizedThreadId)
        } else {
            val responsePair = requestFirstAvailable(
                methods = listOf("thread/unarchive", "thread/archive"),
                params = JsonObject(
                    mapOf(
                        "threadId" to JsonPrimitive(normalizedThreadId),
                        "unarchive" to JsonPrimitive(true)
                    )
                )
            )
            if (responsePair == null) {
                throw IllegalStateException("Relay does not support thread unarchive.")
            }
            locallyArchivedThreadIds.remove(normalizedThreadId)
            locallyDeletedThreadIds.remove(normalizedThreadId)
        }

        _threads.value = _threads.value.map { thread ->
            if (thread.id == normalizedThreadId) {
                thread.copy(isArchived = archived)
            } else {
                thread
            }
        }
        if (_selectedThreadId.value == normalizedThreadId && archived) {
            val fallback = _threads.value.firstOrNull { !it.isArchived && it.id != normalizedThreadId }?.id
            _selectedThreadId.value = fallback
            if (fallback == null) {
                _timeline.value = emptyList()
            } else {
                openThread(fallback, silentStatus = true)
            }
        }
        if (refreshAfter) {
            refreshThreads(silentStatus = true, includeTimeline = false)
        }
    }

    suspend fun sendTurnStart(
        inputText: String,
        attachments: List<TurnImageAttachment> = emptyList(),
        skillMentions: List<SkillSuggestion> = emptyList(),
        fileMentions: List<String> = emptyList()
    ) {
        ensureConnected()
        val normalizedInput = inputText.trim()
        val normalizedAttachments = attachments
            .map { it.dataUrl.trim() }
            .filter { it.isNotEmpty() }
        if (normalizedInput.isEmpty() && normalizedAttachments.isEmpty()) {
            return
        }

        val normalizedFileMentions = fileMentions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var threadId = resolveThreadIdForTurnStart()
        var recoveryCount = 0
        val maxRecoveries = 2

        while (true) {
            try {
                runCatching { openThread(threadId, silentStatus = true) }
                    .onFailure { error ->
                        AppLogger.warn(
                            LOG_TAG,
                            "thread/read refresh before turn/start failed for thread=$threadId.",
                            error
                        )
                    }
                ensureThreadResumed(threadId = threadId, force = true)
                val response = requestTurnStart(
                    threadId = threadId,
                    normalizedInput = normalizedInput,
                    normalizedAttachments = normalizedAttachments,
                    skillMentions = skillMentions,
                    normalizedFileMentions = normalizedFileMentions
                )
                handleTurnStartSuccess(
                    threadId = threadId,
                    response = response,
                    normalizedFileMentions = normalizedFileMentions,
                    statusMessage = if (recoveryCount > 0) {
                        "Turn started on $threadId via $activeTransportLabel (continued from archived chat)."
                    } else {
                        null
                    }
                )
                return
            } catch (error: Throwable) {
                if (!shouldTreatAsMissingThread(error)) {
                    throw error
                }
                if (recoveryCount >= maxRecoveries) {
                    throw error
                }
                runCatching { refreshThreads(silentStatus = true, includeTimeline = false) }
                AppLogger.warn(
                    LOG_TAG,
                    "turn/start lifecycle reported missing thread=$threadId; creating continuation thread."
                )
                threadId = recoverMissingThreadAndResolveContinuation(threadId)
                recoveryCount += 1
            }
        }
    }

    private fun shouldRetryTurnStartWithImageUrlField(rpcError: RpcError): Boolean {
        val message = rpcError.message.lowercase()
        if (!message.contains("image")) {
            return false
        }
        return message.contains("image_url")
            || message.contains("imageurl")
            || message.contains("url")
    }

    private suspend fun resolveThreadIdForTurnStart(): String {
        val selectedId = _selectedThreadId.value?.trim().orEmpty()
        if (selectedId.isNotEmpty()) {
            val selectedThread = _threads.value.firstOrNull { it.id == selectedId }
            if (selectedThread != null && !selectedThread.isArchived) {
                return selectedId
            }
        }

        val fallbackThreadId = _threads.value.firstOrNull { !it.isArchived }?.id
        if (!fallbackThreadId.isNullOrBlank()) {
            _selectedThreadId.value = fallbackThreadId
            updateCurrentProjectPathForThread(fallbackThreadId)
            return fallbackThreadId
        }

        startThread(preferredProjectPath = normalizeProjectPath(_currentProjectPath.value))
        val createdThreadId = _selectedThreadId.value?.trim().orEmpty()
        if (createdThreadId.isEmpty()) {
            throw IllegalStateException("No thread is selected.")
        }
        return createdThreadId
    }

    private suspend fun ensureThreadResumed(threadId: String, force: Boolean = false) {
        val normalizedThreadId = threadId.trim()
        if (normalizedThreadId.isEmpty()) {
            return
        }
        if (!force && resumedThreadIds.contains(normalizedThreadId)) {
            return
        }

        val params = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("threadId", JsonPrimitive(normalizedThreadId))
            val threadScopedPath = projectPathByThread[normalizedThreadId]
                ?: _threads.value.firstOrNull { it.id == normalizedThreadId }?.cwd
            normalizeProjectPath(threadScopedPath)?.let { put("cwd", JsonPrimitive(it)) }
            val normalizedModel = _selectedModel.value.trim()
            if (normalizedModel.isNotEmpty()) {
                put("model", JsonPrimitive(normalizedModel))
            }
        }
        val responsePair = requestFirstAvailable(
            methods = listOf("thread/resume"),
            params = JsonObject(params)
        ) ?: run {
            resumedThreadIds.add(normalizedThreadId)
            return
        }
        val resumedThread = (responsePair.second.result as? JsonObject)?.let { result ->
            (result["thread"] as? JsonObject)?.let(parser::parseThreadSummaryObject)
        }
        if (resumedThread != null) {
            upsertThreadSummary(resumedThread)
            updateThreadProjectPath(resumedThread.id, resumedThread.cwd)
        }
        resumedThreadIds.add(normalizedThreadId)
    }

    private suspend fun requestTurnStart(
        threadId: String,
        normalizedInput: String,
        normalizedAttachments: List<String>,
        skillMentions: List<SkillSuggestion>,
        normalizedFileMentions: List<String>
    ): RpcMessage {
        var imageUrlKey = "url"
        var response = requestRpc(
            method = "turn/start",
            params = buildTurnStartParams(
                threadId = threadId,
                normalizedInput = normalizedInput,
                imageUrlKey = imageUrlKey,
                normalizedAttachments = normalizedAttachments,
                skillMentions = skillMentions,
                fileMentions = normalizedFileMentions
            )
        )
        val initialRpcError = response.error
        if (initialRpcError != null
            && normalizedAttachments.isNotEmpty()
            && shouldRetryTurnStartWithImageUrlField(initialRpcError)
        ) {
            imageUrlKey = "image_url"
            response = requestRpc(
                method = "turn/start",
                params = buildTurnStartParams(
                    threadId = threadId,
                    normalizedInput = normalizedInput,
                    imageUrlKey = imageUrlKey,
                    normalizedAttachments = normalizedAttachments,
                    skillMentions = skillMentions,
                    fileMentions = normalizedFileMentions
                )
            )
        }
        throwIfRpcError(response, "turn/start")
        return response
    }

    private suspend fun handleTurnStartSuccess(
        threadId: String,
        response: RpcMessage,
        normalizedFileMentions: List<String>,
        statusMessage: String? = null
    ) {
        val turnId = (response.result as? JsonObject)?.string("turnId", "turn_id")
        if (!turnId.isNullOrBlank()) {
            activeTurnIdByThread[threadId] = turnId
        }
        _selectedThreadId.value = threadId
        runCatching { openThread(threadId, silentStatus = true) }
            .onFailure { error ->
                AppLogger.warn(
                    LOG_TAG,
                    "turn/start succeeded but thread/read refresh failed for thread=$threadId.",
                    error
                )
            }
        runCatching { refreshActiveThreadTimeline(silentStatus = true) }
            .onFailure { error ->
                AppLogger.warn(
                    LOG_TAG,
                    "turn/start succeeded but timeline refresh failed for thread=$threadId.",
                    error
                )
            }
        if (normalizedFileMentions.isNotEmpty()) {
            AppLogger.info(
                LOG_TAG,
                "turn/start sent with file mentions count=${normalizedFileMentions.size}."
            )
        }
        setStatus(statusMessage ?: "Turn started on $threadId via $activeTransportLabel.", notify = true)
    }

    private fun shouldTreatAsMissingThread(error: Throwable): Boolean {
        val message = (error.message ?: error.localizedMessage ?: "")
            .trim()
            .lowercase()
        if (message.contains("not materialized") || message.contains("not yet materialized")) {
            return false
        }
        return message.contains("thread not found") || message.contains("unknown thread")
    }

    private suspend fun recoverMissingThreadAndResolveContinuation(missingThreadId: String): String {
        val missingThread = _threads.value.firstOrNull { it.id == missingThreadId }
        val preferredProjectPath = normalizeProjectPath(missingThread?.cwd)
            ?: projectPathByThread[missingThreadId]
            ?: normalizeProjectPath(_currentProjectPath.value)

        activeTurnIdByThread.remove(missingThreadId)
        resumedThreadIds.remove(missingThreadId)
        locallyArchivedThreadIds.add(missingThreadId)
        locallyDeletedThreadIds.remove(missingThreadId)

        if (missingThread != null) {
            _threads.value = _threads.value
                .map { thread ->
                    if (thread.id == missingThreadId) {
                        thread.copy(isArchived = true, updatedAtMillis = System.currentTimeMillis())
                    } else {
                        thread
                    }
                }
                .sortedWith(threadSummaryComparator())
        }

        runCatching { refreshThreads(silentStatus = true, includeTimeline = false) }
        startThread(preferredProjectPath = preferredProjectPath)
        val continuationThreadId = _selectedThreadId.value?.trim().orEmpty()
        if (continuationThreadId.isEmpty()) {
            throw IllegalStateException("Thread became unavailable and no continuation thread could be created.")
        }
        return continuationThreadId
    }

    private fun buildTurnStartParams(
        threadId: String,
        normalizedInput: String,
        imageUrlKey: String,
        normalizedAttachments: List<String>,
        skillMentions: List<SkillSuggestion>,
        fileMentions: List<String>
    ): JsonObject {
        val inputItems = mutableListOf<kotlinx.serialization.json.JsonElement>()

        normalizedAttachments.forEach { dataUrl ->
            inputItems += JsonObject(
                mapOf(
                    "type" to JsonPrimitive("image"),
                    imageUrlKey to JsonPrimitive(dataUrl)
                )
            )
        }

        if (normalizedInput.isNotEmpty()) {
            inputItems += JsonObject(
                mapOf(
                    "type" to JsonPrimitive("text"),
                    "text" to JsonPrimitive(normalizedInput)
                )
            )
        }

        skillMentions
            .filter { it.enabled }
            .forEach { skill ->
                val normalizedSkillId = skill.id.trim().ifEmpty { skill.name.trim() }
                if (normalizedSkillId.isEmpty()) {
                    return@forEach
                }
                val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                    "type" to JsonPrimitive("skill"),
                    "id" to JsonPrimitive(normalizedSkillId)
                )
                val normalizedName = skill.name.trim()
                if (normalizedName.isNotEmpty()) {
                    payload["name"] = JsonPrimitive(normalizedName)
                }
                val normalizedPath = skill.path?.trim().orEmpty()
                if (normalizedPath.isNotEmpty()) {
                    payload["path"] = JsonPrimitive(normalizedPath)
                }
                inputItems += JsonObject(payload)
            }

        fileMentions.forEach { filePath ->
            val normalizedPath = filePath.trim()
            if (normalizedPath.isEmpty()) {
                return@forEach
            }
            inputItems += JsonObject(
                mapOf(
                    "type" to JsonPrimitive("file"),
                    "path" to JsonPrimitive(normalizedPath),
                    "name" to JsonPrimitive(normalizedPath.substringAfterLast('/').substringAfterLast('\\'))
                )
            )
        }

        val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "threadId" to JsonPrimitive(threadId),
            "input" to JsonArray(inputItems)
        )
        val normalizedModel = _selectedModel.value.trim()
        if (normalizedModel.isNotEmpty()) {
            payload["model"] = JsonPrimitive(normalizedModel)
        }
        val normalizedReasoning = _selectedReasoningEffort.value.trim()
        if (normalizedReasoning.isNotEmpty()) {
            payload["effort"] = JsonPrimitive(normalizedReasoning)
            payload["reasoning_effort"] = JsonPrimitive(normalizedReasoning)
        }
        return JsonObject(payload)
    }

    suspend fun fuzzyFileSearch(
        query: String,
        roots: List<String>,
        limit: Int = 8
    ): List<FileAutocompleteMatch> {
        ensureConnected()
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        val normalizedRoots = roots
            .mapNotNull(::normalizeProjectPath)
            .distinct()
        if (normalizedRoots.isEmpty()) {
            return emptyList()
        }

        val params = JsonObject(
            mapOf(
                "query" to JsonPrimitive(normalizedQuery),
                "roots" to JsonArray(normalizedRoots.map(::JsonPrimitive))
            )
        )
        val responsePair = requestFirstAvailable(
            methods = listOf("fuzzyFileSearch", "fuzzy_file_search"),
            params = params
        ) ?: return emptyList()
        val result = responsePair.second.result as? JsonObject ?: return emptyList()
        return parseFuzzyFileMatches(result, limit)
    }

    suspend fun listSkills(
        cwds: List<String>,
        forceReload: Boolean = false,
        limit: Int = 12
    ): List<SkillSuggestion> {
        ensureConnected()
        val normalizedCwds = cwds
            .mapNotNull(::normalizeProjectPath)
            .distinct()
        val paramsMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        if (normalizedCwds.isNotEmpty()) {
            paramsMap["cwds"] = JsonArray(normalizedCwds.map(::JsonPrimitive))
            paramsMap["cwd"] = JsonPrimitive(normalizedCwds.first())
        }
        if (forceReload) {
            paramsMap["forceReload"] = JsonPrimitive(true)
        }
        val responsePair = requestFirstAvailable(
            methods = listOf("skills/list", "skill/list", "skills.list"),
            params = JsonObject(paramsMap)
        ) ?: return emptyList()
        val result = responsePair.second.result as? JsonObject ?: return emptyList()
        return parseSkills(result, limit)
    }

    suspend fun turnSteer(inputText: String): Boolean {
        ensureConnected()
        val normalizedInput = inputText.trim()
        if (normalizedInput.isEmpty()) {
            throw IllegalArgumentException("Steer input is required.")
        }
        val threadId = _selectedThreadId.value ?: return false
        val activeTurnId = activeTurnIdByThread[threadId]
        val responsePair = requestFirstAvailable(
            methods = listOf("turn/steer"),
            params = JsonObject(
                buildMap {
                    put("threadId", JsonPrimitive(threadId))
                    put("input", JsonPrimitive(normalizedInput))
                    put("text", JsonPrimitive(normalizedInput))
                    if (!activeTurnId.isNullOrBlank()) {
                        put("turnId", JsonPrimitive(activeTurnId))
                    }
                }
            )
        ) ?: run {
            setStatus("Turn steering unavailable from relay.")
            return false
        }
        val steeredTurnId = (responsePair.second.result as? JsonObject)?.string("turnId", "turn_id")
        if (!steeredTurnId.isNullOrBlank()) {
            activeTurnIdByThread[threadId] = steeredTurnId
        }
        setStatus("Steered active turn on $threadId via $activeTransportLabel.", notify = true)
        return true
    }

    suspend fun threadResume(threadId: String? = null): Boolean {
        ensureConnected()
        val normalizedThreadId = threadId?.trim()?.takeIf { it.isNotEmpty() } ?: _selectedThreadId.value
        val params = normalizedThreadId?.let {
            JsonObject(mapOf("threadId" to JsonPrimitive(it)))
        } ?: JsonObject(emptyMap())
        val responsePair = requestFirstAvailable(
            methods = listOf("thread/resume"),
            params = params
        ) ?: run {
            setStatus("Thread resume unavailable from relay.")
            return false
        }
        val resumedThread = (responsePair.second.result as? JsonObject)?.let { result ->
            (result["thread"] as? JsonObject)?.let(parser::parseThreadSummaryObject)
        }
        if (resumedThread != null) {
            upsertThreadSummary(resumedThread)
            _selectedThreadId.value = resumedThread.id
            updateCurrentProjectPathForThread(resumedThread.id)
        }
        setStatus("Resumed thread via $activeTransportLabel.", notify = true)
        return true
    }

    suspend fun threadFork(
        threadId: String? = null,
        targetProjectPath: String? = null
    ): String? {
        ensureConnected()
        val normalizedThreadId = threadId?.trim()?.takeIf { it.isNotEmpty() } ?: _selectedThreadId.value
            ?: return null
        val normalizedTargetProjectPath = normalizeProjectPath(targetProjectPath)
        val primaryParams = buildThreadForkParams(
            threadId = normalizedThreadId,
            targetProjectPath = normalizedTargetProjectPath
        )
        val attemptParams = buildList {
            add(primaryParams)
            val legacyParams = buildThreadForkParams(
                threadId = normalizedThreadId,
                targetProjectPath = null
            )
            if (legacyParams.toString() != primaryParams.toString()) {
                add(legacyParams)
            }
        }
        var usedFallbackParams = false
        var responsePair: Pair<String, RpcMessage>? = null
        for ((index, params) in attemptParams.withIndex()) {
            val candidate = try {
                requestFirstAvailable(
                    methods = listOf("thread/fork"),
                    params = params
                )
            } catch (error: IllegalStateException) {
                val hasFallback = index < attemptParams.lastIndex
                if (hasFallback && shouldFallbackThreadForkParams(error)) {
                    usedFallbackParams = true
                    continue
                }
                throw error
            }
            if (candidate != null) {
                if (index > 0) {
                    usedFallbackParams = true
                }
                responsePair = candidate
                break
            }
        }
        if (responsePair == null) {
            setStatus("Thread fork unavailable from relay.")
            return null
        }
        val resultObject = responsePair.second.result as? JsonObject ?: JsonObject(emptyMap())
        val forkedThreadObject = resultObject["thread"] as? JsonObject
        val parsedThread = forkedThreadObject?.let(parser::parseThreadSummaryObject)
        if (parsedThread != null) {
            upsertThreadSummary(parsedThread)
            _selectedThreadId.value = parsedThread.id
            updateCurrentProjectPathForThread(parsedThread.id)
        }
        val forkedThreadId = parsedThread?.id ?: resultObject.string("threadId", "thread_id")
        if (!forkedThreadId.isNullOrBlank()) {
            val targetHint = if (normalizedTargetProjectPath != null) " into $normalizedTargetProjectPath" else ""
            val compatibilityHint = if (usedFallbackParams && normalizedTargetProjectPath != null) {
                " (runtime ignored target override)"
            } else {
                ""
            }
            setStatus(
                "Forked thread $forkedThreadId$targetHint via $activeTransportLabel.$compatibilityHint",
                notify = true
            )
        }
        return forkedThreadId
    }

    suspend fun gitCreateWorktree(
        name: String,
        baseBranch: String,
        changeTransfer: String = "copy"
    ): String {
        ensureConnected()
        val gitScope = requireSelectedThreadGitScope()
        val normalizedName = name.trim()
        val normalizedBaseBranch = baseBranch.trim()
        if (normalizedName.isEmpty()) {
            throw IllegalArgumentException("Worktree branch name is required.")
        }
        if (normalizedBaseBranch.isEmpty()) {
            throw IllegalArgumentException("Base branch is required.")
        }
        val responsePair = requestFirstAvailable(
            methods = listOf("git/createWorktree"),
            params = JsonObject(
                mapOf(
                    "name" to JsonPrimitive(normalizedName),
                    "baseBranch" to JsonPrimitive(normalizedBaseBranch),
                    "changeTransfer" to JsonPrimitive(changeTransfer),
                    "cwd" to JsonPrimitive(gitScope.cwd),
                    "threadId" to JsonPrimitive(gitScope.threadId)
                )
            )
        ) ?: throw IllegalStateException("git/createWorktree unavailable from relay.")
        val resultObject = responsePair.second.result as? JsonObject ?: JsonObject(emptyMap())
        val worktreePath = resultObject.string("worktreePath", "worktree_path")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("git/createWorktree result missing worktree path.")
        setStatus(
            "Prepared worktree $worktreePath via $activeTransportLabel.",
            notify = true,
            eventKind = ServiceEventKind.GIT_ACTION
        )
        return worktreePath
    }

    suspend fun reviewStart(
        threadId: String? = null,
        target: ReviewTarget? = null,
        baseBranch: String? = null
    ): Boolean {
        ensureConnected()
        if (target == ReviewTarget.BASE_BRANCH && baseBranch?.trim().isNullOrEmpty()) {
            setStatus("Choose a base branch before starting review.")
            return false
        }
        val normalizedThreadId = threadId?.trim()?.takeIf { it.isNotEmpty() } ?: _selectedThreadId.value
        val primaryParams = buildReviewStartParams(
            threadId = normalizedThreadId,
            target = target,
            baseBranch = baseBranch
        )
        val attemptParams = buildList {
            add(primaryParams)
            val legacyParams = buildReviewStartParams(
                threadId = normalizedThreadId,
                target = null,
                baseBranch = null
            )
            if (legacyParams.toString() != primaryParams.toString()) {
                add(legacyParams)
            }
        }
        for ((index, params) in attemptParams.withIndex()) {
            val responsePair = try {
                requestFirstAvailable(
                    methods = listOf("review/start"),
                    params = params
                )
            } catch (error: IllegalStateException) {
                val hasFallback = index < attemptParams.lastIndex
                if (hasFallback && shouldFallbackReviewStartParams(error)) {
                    AppLogger.info(LOG_TAG, "review/start params rejected, retrying compatibility payload.")
                    continue
                }
                throw error
            } ?: continue
            val reviewId = (responsePair.second.result as? JsonObject)?.string("reviewId", "review_id")
            setStatus(
                if (!reviewId.isNullOrBlank()) {
                    "Started review $reviewId via $activeTransportLabel."
                } else {
                    "Started review via $activeTransportLabel."
                },
                notify = true
            )
            return true
        }
        setStatus("Review start unavailable from relay.")
        return false
    }

    private fun buildReviewStartParams(
        threadId: String?,
        target: ReviewTarget?,
        baseBranch: String?
    ): JsonObject {
        val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        val normalizedThreadId = threadId?.trim().orEmpty()
        if (normalizedThreadId.isNotEmpty()) {
            payload["threadId"] = JsonPrimitive(normalizedThreadId)
        }
        val targetObject = when (target) {
            ReviewTarget.UNCOMMITTED_CHANGES -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("uncommittedChanges")
                )
            )

            ReviewTarget.BASE_BRANCH -> {
                val normalizedBaseBranch = baseBranch?.trim().orEmpty()
                if (normalizedBaseBranch.isEmpty()) {
                    null
                } else {
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("baseBranch"),
                            "branch" to JsonPrimitive(normalizedBaseBranch)
                        )
                    )
                }
            }

            null -> null
        }
        if (targetObject != null) {
            payload["delivery"] = JsonPrimitive("inline")
            payload["target"] = targetObject
        }
        return JsonObject(payload)
    }

    private fun shouldFallbackReviewStartParams(error: IllegalStateException): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return message.contains("(-32600)")
            || message.contains("(-32602)")
            || message.contains("invalid params")
            || message.contains("invalid type")
            || message.contains("missing field")
            || message.contains("unknown field")
    }

    private fun buildThreadForkParams(
        threadId: String,
        targetProjectPath: String?
    ): JsonObject {
        val payload = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "threadId" to JsonPrimitive(threadId)
        )
        if (!targetProjectPath.isNullOrBlank()) {
            payload["cwd"] = JsonPrimitive(targetProjectPath)
        }
        return JsonObject(payload)
    }

    private fun shouldFallbackThreadForkParams(error: IllegalStateException): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return message.contains("(-32600)")
            || message.contains("(-32602)")
            || message.contains("invalid params")
            || message.contains("invalid type")
            || message.contains("missing field")
            || message.contains("unknown field")
            || message.contains("thread/fork")
    }

    suspend fun interruptActiveTurn() {
        ensureConnected()
        val threadId = selectedThreadId.value
            ?: threads.value.firstOrNull()?.id
            ?: throw IllegalStateException("No thread is selected.")
        val cachedTurnId = activeTurnIdByThread[threadId]
        val resolvedTurnId = if (!cachedTurnId.isNullOrBlank() && !cachedTurnId.startsWith("pending-")) {
            cachedTurnId
        } else {
            resolveInterruptTurnId(threadId)
        }
        if (resolvedTurnId.isNullOrBlank()) {
            throw IllegalStateException("No active turn found for $threadId.")
        }
        val response = requestRpc(
            method = "turn/interrupt",
            params = JsonObject(
                mapOf(
                    "turnId" to JsonPrimitive(resolvedTurnId),
                    "threadId" to JsonPrimitive(threadId)
                )
            )
        )
        throwIfRpcError(response, "turn/interrupt")
        activeTurnIdByThread.remove(threadId)
        openThread(threadId, silentStatus = true)
        setStatus("Interrupted turn $resolvedTurnId on $threadId via $activeTransportLabel.", notify = true)
    }

    suspend fun reconcileThreadRunningState(threadId: String?): Boolean {
        val normalizedThreadId = threadId?.trim().orEmpty()
        if (normalizedThreadId.isEmpty()) {
            return false
        }
        val cachedTurnId = activeTurnIdByThread[normalizedThreadId]
        if (cachedTurnId.isNullOrBlank()) {
            return false
        }
        val resolvedTurnId = resolveInterruptTurnId(normalizedThreadId)
        if (resolvedTurnId.isNullOrBlank()) {
            activeTurnIdByThread.remove(normalizedThreadId)
            return false
        }
        activeTurnIdByThread[normalizedThreadId] = resolvedTurnId
        return true
    }

    fun isThreadRunning(threadId: String?): Boolean {
        val normalized = threadId?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return false
        }
        return !activeTurnIdByThread[normalized].isNullOrBlank()
    }

    suspend fun registerPushToken(payload: PushRegistrationPayload) {
        ensureConnected()
        val normalizedDeviceToken = payload.deviceToken.trim()
        if (normalizedDeviceToken.isEmpty()) {
            throw IllegalArgumentException("Push token is required.")
        }

        val response = requestRpc(
            method = "notifications/push/register",
            params = JsonObject(
                mapOf(
                    "deviceToken" to JsonPrimitive(normalizedDeviceToken),
                    "alertsEnabled" to JsonPrimitive(payload.alertsEnabled),
                    "platform" to JsonPrimitive(payload.platform),
                    "pushProvider" to JsonPrimitive(payload.pushProvider),
                    "pushEnvironment" to JsonPrimitive(payload.pushEnvironment)
                )
            )
        )
        throwIfRpcError(response, "notifications/push/register")
        val result = response.result as? JsonObject
        val provider = (result?.get("pushProvider") as? JsonPrimitive)?.content ?: payload.pushProvider
        val environment = (result?.get("pushEnvironment") as? JsonPrimitive)?.content ?: payload.pushEnvironment
        setStatus("Push registered via $provider ($environment) on $activeTransportLabel.", notify = true)
    }

    suspend fun refreshGitStatus(silentStatus: Boolean = false) {
        ensureConnected()
        val gitScope = resolveSelectedThreadGitScopeOrNull()
        if (gitScope == null) {
            _gitStatusSummary.value = "Git status unavailable: select a thread bound to a local repo."
            if (!silentStatus) {
                setStatus("Select a repo-bound thread before running git actions.")
            }
            return
        }
        val response = requestRpc(
            method = "git/status",
            params = buildGitParams(gitScope)
        )
        throwIfRpcError(response, "git/status")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("git/status result is missing.")
        _gitStatusSummary.value = parseGitStatusSummary(resultObject)

        val repoRoot = parseGitRepoRoot(resultObject)
        if (!repoRoot.isNullOrBlank()) {
            updateThreadProjectPath(gitScope.threadId, repoRoot)
            _currentProjectPath.value = repoRoot
        }

        if (!silentStatus) {
            setStatus("Git status loaded via $activeTransportLabel.")
        }
    }

    suspend fun refreshGitBranches(silentStatus: Boolean = false) {
        ensureConnected()
        val gitScope = resolveSelectedThreadGitScopeOrNull()
        if (gitScope == null) {
            _gitBranches.value = emptyList()
            if (!silentStatus) {
                setStatus("Select a repo-bound thread before loading branches.")
            }
            return
        }
        val response = requestRpc(
            method = "git/branches",
            params = buildGitParams(gitScope)
        )
        throwIfRpcError(response, "git/branches")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("git/branches result is missing.")
        val parsedBranches = parseGitBranches(resultObject)
        _gitBranches.value = parsedBranches

        if (!silentStatus) {
            setStatus("Loaded ${parsedBranches.size} git branch(es) via $activeTransportLabel.")
        }
    }

    suspend fun checkoutGitBranch(branch: String) {
        ensureConnected()
        val normalizedBranch = branch.trim()
        if (normalizedBranch.isEmpty()) {
            throw IllegalArgumentException("Branch is required.")
        }
        val gitScope = requireSelectedThreadGitScope()
        withGitActionProgress("Checking out $normalizedBranch…") {
            val response = requestRpc(
                method = "git/checkout",
                params = JsonObject(
                    mapOf(
                        "branch" to JsonPrimitive(normalizedBranch),
                        "cwd" to JsonPrimitive(gitScope.cwd),
                        "threadId" to JsonPrimitive(gitScope.threadId)
                    )
                )
            )
            throwIfRpcError(response, "git/checkout")
            refreshGitStatus(silentStatus = true)
            refreshGitBranches(silentStatus = true)
            setStatus(
                "Checked out $normalizedBranch via $activeTransportLabel.",
                notify = true,
                eventKind = ServiceEventKind.GIT_ACTION
            )
        }
    }

    suspend fun gitPull() {
        ensureConnected()
        val gitScope = requireSelectedThreadGitScope()
        withGitActionProgress("Pulling latest changes…") {
            val response = requestRpc(
                method = "git/pull",
                params = buildGitParams(gitScope)
            )
            throwIfRpcError(response, "git/pull")
            refreshGitStatus(silentStatus = true)
            setStatus(
                "Pulled latest changes via $activeTransportLabel.",
                notify = true,
                eventKind = ServiceEventKind.GIT_ACTION
            )
        }
    }

    suspend fun gitPush() {
        ensureConnected()
        val gitScope = requireSelectedThreadGitScope()
        withGitActionProgress("Pushing changes…") {
            val response = requestRpc(
                method = "git/push",
                params = buildGitParams(gitScope)
            )
            throwIfRpcError(response, "git/push")
            refreshGitStatus(silentStatus = true)
            setStatus(
                "Pushed changes via $activeTransportLabel.",
                notify = true,
                eventKind = ServiceEventKind.GIT_ACTION
            )
        }
    }

    suspend fun gitDiff(): String {
        ensureConnected()
        val gitScope = requireSelectedThreadGitScope()
        val response = requestRpc(
            method = "git/diff",
            params = buildGitParams(gitScope)
        )
        throwIfRpcError(response, "git/diff")
        val result = response.result as? JsonObject
            ?: throw IllegalStateException("git/diff result is missing.")
        return result.string("patch", "diff", "unifiedDiff", "unified_diff")
            ?.trim()
            .orEmpty()
    }

    suspend fun gitCommit(message: String?) {
        ensureConnected()
        val gitScope = requireSelectedThreadGitScope()
        val normalizedMessage = message?.trim().orEmpty()
        val params = if (normalizedMessage.isEmpty()) {
            buildGitParams(gitScope)
        } else {
            JsonObject(
                mapOf(
                    "message" to JsonPrimitive(normalizedMessage),
                    "cwd" to JsonPrimitive(gitScope.cwd),
                    "threadId" to JsonPrimitive(gitScope.threadId)
                )
            )
        }
        withGitActionProgress("Committing changes…") {
            val response = requestRpc(
                method = "git/commit",
                params = params
            )
            throwIfRpcError(response, "git/commit")
            val result = response.result as? JsonObject
            val hash = result?.string("hash")
            val summary = result?.string("summary")
            refreshGitStatus(silentStatus = true)
            setStatus(
                if (!hash.isNullOrBlank()) {
                    "Committed $hash${if (!summary.isNullOrBlank()) " ($summary)" else ""} via $activeTransportLabel."
                } else {
                    "Committed changes via $activeTransportLabel."
                },
                notify = true,
                eventKind = ServiceEventKind.GIT_ACTION
            )
        }
    }

    suspend fun gitCommitAndPush(message: String?) {
        ensureConnected()
        val gitScope = requireSelectedThreadGitScope()
        val normalizedMessage = message?.trim().orEmpty()
        withGitActionProgress("Commit & push…") {
            val commitParams = if (normalizedMessage.isEmpty()) {
                buildGitParams(gitScope)
            } else {
                JsonObject(
                    mapOf(
                        "message" to JsonPrimitive(normalizedMessage),
                        "cwd" to JsonPrimitive(gitScope.cwd),
                        "threadId" to JsonPrimitive(gitScope.threadId)
                    )
                )
            }
            val commitResponse = requestRpc(
                method = "git/commit",
                params = commitParams
            )
            throwIfRpcError(commitResponse, "git/commit")
            val pushResponse = requestRpc(
                method = "git/push",
                params = buildGitParams(gitScope)
            )
            throwIfRpcError(pushResponse, "git/push")
            refreshGitStatus(silentStatus = true)
            refreshGitBranches(silentStatus = true)
            setStatus(
                "Commit & push completed via $activeTransportLabel.",
                notify = true,
                eventKind = ServiceEventKind.GIT_ACTION
            )
        }
    }

    suspend fun refreshRateLimitInfo(silentStatus: Boolean = false) {
        ensureConnected()
        val responsePair = requestFirstAvailable(
            methods = listOf("account/rateLimits/read", "rate_limit/status", "ratelimit/status", "limits/read")
        )
        if (responsePair == null) {
            _rateLimitInfo.value = "Rate limit info unavailable from relay."
            if (!silentStatus) {
                setStatus("Rate limits unavailable from relay.")
            }
            return
        }

        val result = responsePair.second.result as? JsonObject ?: JsonObject(emptyMap())
        val (summary, remaining) = parseRateLimitInfo(result)
        _rateLimitInfo.value = summary

        if (remaining != null && remaining <= 0) {
            publishEvent(
                kind = ServiceEventKind.RATE_LIMIT_HIT,
                title = "Rate Limit Hit",
                message = summary
            )
        }

        if (!silentStatus) {
            setStatus("Rate limit info refreshed via $activeTransportLabel.")
        }
    }

    suspend fun refreshModels(silentStatus: Boolean = false) {
        ensureConnected()
        val responsePair = requestFirstAvailable(
            methods = listOf("models/list", "model/list")
        )
        if (responsePair == null) {
            if (!silentStatus) {
                setStatus("Model list not exposed by relay.")
            }
            return
        }

        val result = responsePair.second.result as? JsonObject ?: JsonObject(emptyMap())
        val parsedModels = parseModelList(result)
        if (parsedModels.isNotEmpty()) {
            _availableModels.value = parsedModels
        }

        val selectedFromRelay = result.string("selectedModel", "activeModel", "currentModel", "model")?.trim()
        val availableModelSet = _availableModels.value.toSet()
        val currentSelection = _selectedModel.value.trim()
        _selectedModel.value = when {
            currentSelection.isNotEmpty() && availableModelSet.contains(currentSelection) -> currentSelection
            !selectedFromRelay.isNullOrBlank() && availableModelSet.contains(selectedFromRelay) -> selectedFromRelay
            _availableModels.value.isNotEmpty() -> _availableModels.value.first()
            else -> currentSelection
        }

        val parsedReasoningEfforts = parseReasoningEfforts(result)
        if (parsedReasoningEfforts.isNotEmpty()) {
            _availableReasoningEfforts.value = parsedReasoningEfforts
        }
        val selectedReasoning = parseSelectedReasoningEffort(result)
            ?: _selectedReasoningEffort.value.trim()
        _selectedReasoningEffort.value = if (_availableReasoningEfforts.value.contains(selectedReasoning)) {
            selectedReasoning
        } else {
            _availableReasoningEfforts.value.firstOrNull() ?: "medium"
        }

        if (!silentStatus) {
            setStatus("Model list refreshed via $activeTransportLabel.")
        }
    }

    suspend fun refreshBridgeManagedState(allowAvailableBridgeUpdatePrompt: Boolean = false) {
        if (_connectionState.value != ConnectionState.Connected) {
            applyDisconnectedBridgeManagedState()
            return
        }
        runCatching {
            val payload = fetchBridgeManagedStatusSnapshot()
            applyBridgePackageStatus(payload, allowAvailableBridgeUpdatePrompt)
            applyBridgeManagedAccountSnapshot(payload)
        }.onFailure {
            applyDisconnectedBridgeManagedState()
        }
    }

    suspend fun refreshGPTAccountState() {
        refreshBridgeManagedState(allowAvailableBridgeUpdatePrompt = false)
    }

    suspend fun refreshBridgeVersionState(allowAvailableBridgeUpdatePrompt: Boolean = false) {
        if (_connectionState.value != ConnectionState.Connected) {
            return
        }
        runCatching {
            val payload = fetchBridgeManagedStatusSnapshot()
            applyBridgePackageStatus(payload, allowAvailableBridgeUpdatePrompt)
        }
    }

    fun triggerVoiceRecoveryCheck() {
        _voiceRecoverySnapshot.value = when (_connectionState.value) {
            ConnectionState.Connected -> {
                when (_gptAccountStatus.value) {
                    BridgeManagedAccountStatus.AUTHENTICATED -> {
                        if (supportsBridgeVoiceAuth) {
                            RecoveryAccessorySnapshot(
                                title = "Voice Mode",
                                summary = "Voice mode is still syncing from your Mac.",
                                detail = "Keep the bridge connected for a moment, then try again.",
                                status = RecoveryAccessoryStatus.SYNCING
                            )
                        } else {
                            RecoveryAccessorySnapshot(
                                title = "Voice Mode",
                                summary = "This bridge session does not support voice mode yet.",
                                detail = "Restart Remodex on your Mac, then reconnect Android and try again.",
                                status = RecoveryAccessoryStatus.ACTION_REQUIRED,
                                actionLabel = "Reconnect"
                            )
                        }
                    }
                    BridgeManagedAccountStatus.LOGIN_IN_PROGRESS -> RecoveryAccessorySnapshot(
                        title = "Voice Mode",
                        summary = "Voice mode is still syncing from your Mac.",
                        detail = "Wait for ChatGPT login to finish on the paired Mac, then retry.",
                        status = RecoveryAccessoryStatus.SYNCING
                    )
                    BridgeManagedAccountStatus.REAUTH_REQUIRED -> RecoveryAccessorySnapshot(
                        title = "Voice Mode",
                        summary = "ChatGPT voice needs a fresh sign-in on your Mac.",
                        detail = "Open ChatGPT on the paired Mac, sign in again there, then retry here.",
                        status = RecoveryAccessoryStatus.ACTION_REQUIRED,
                        actionLabel = "How To Fix"
                    )
                    BridgeManagedAccountStatus.NOT_LOGGED_IN, BridgeManagedAccountStatus.UNKNOWN -> RecoveryAccessorySnapshot(
                        title = "Voice Mode",
                        summary = "Sign in to ChatGPT on your Mac to use voice mode.",
                        detail = "Open ChatGPT on the paired Mac, sign in there, then come back here and try again.",
                        status = RecoveryAccessoryStatus.ACTION_REQUIRED,
                        actionLabel = "How To Fix"
                    )
                }
            }
            ConnectionState.Connecting -> RecoveryAccessorySnapshot(
                title = "Voice Mode",
                summary = "Reconnect to your Mac to use voice mode.",
                detail = "Keep the Remodex bridge running on your Mac, then try again.",
                status = RecoveryAccessoryStatus.RECONNECTING
            )
            else -> RecoveryAccessorySnapshot(
                title = "Voice Mode",
                summary = "Reconnect to your Mac to use voice mode.",
                detail = "Keep the Remodex bridge running on your Mac, then try again.",
                status = RecoveryAccessoryStatus.INTERRUPTED,
                actionLabel = "Reconnect"
            )
        }
    }

    fun dismissVoiceRecovery() {
        _voiceRecoverySnapshot.value = null
    }

    suspend fun switchModel(model: String) {
        val normalizedModel = model.trim()
        if (normalizedModel.isEmpty()) {
            throw IllegalArgumentException("Model is required.")
        }
        _selectedModel.value = normalizedModel
        setStatus("Selected model $normalizedModel for upcoming turns.", notify = true)
    }

    fun switchReasoningEffort(effort: String) {
        val normalizedEffort = effort.trim().lowercase()
        if (normalizedEffort.isEmpty()) {
            return
        }
        if (!_availableReasoningEfforts.value.contains(normalizedEffort)) {
            return
        }
        _selectedReasoningEffort.value = normalizedEffort
        setStatus("Selected reasoning effort $normalizedEffort for upcoming turns.")
    }

    suspend fun refreshPendingPermissions(silentStatus: Boolean = false) {
        ensureConnected()
        val responsePair = requestFirstAvailable(
            methods = listOf("permissions/pending", "permissions/list", "permission/list")
        )
        if (responsePair == null) {
            _pendingPermissions.value = emptyList()
            if (!silentStatus) {
                setStatus("Permission queue unavailable from relay.")
            }
            return
        }

        val result = responsePair.second.result as? JsonObject ?: JsonObject(emptyMap())
        val requests = parsePendingPermissions(result)
        _pendingPermissions.value = requests

        if (requests.isNotEmpty()) {
            publishEvent(
                kind = ServiceEventKind.PERMISSION_REQUIRED,
                title = "Permission Required",
                message = "${requests.size} action(s) waiting for approval."
            )
        }

        if (!silentStatus) {
            setStatus("Permissions refreshed via $activeTransportLabel.")
        }
    }

    suspend fun grantPermission(permissionId: String, allow: Boolean = true) {
        ensureConnected()
        val normalizedId = permissionId.trim()
        if (normalizedId.isEmpty()) {
            throw IllegalArgumentException("Permission ID is required.")
        }

        val responsePair = requestFirstAvailable(
            methods = listOf("permissions/grant", "permission/grant"),
            params = JsonObject(
                mapOf(
                    "permissionId" to JsonPrimitive(normalizedId),
                    "decision" to JsonPrimitive(if (allow) "allow" else "deny"),
                    "approved" to JsonPrimitive(allow)
                )
            )
        )
        if (responsePair == null) {
            throw IllegalStateException("Relay does not support permission grants.")
        }

        _pendingPermissions.value = _pendingPermissions.value.filterNot { it.id == normalizedId }
        setStatus(
            "${if (allow) "Granted" else "Denied"} permission $normalizedId via $activeTransportLabel.",
            notify = true,
            eventKind = ServiceEventKind.PERMISSION_REQUIRED
        )
    }

    suspend fun refreshCiStatus(silentStatus: Boolean = false) {
        ensureConnected()
        val gitScope = resolveSelectedThreadGitScopeOrNull()
        if (gitScope == null) {
            _ciStatus.value = ""
            if (!silentStatus) {
                setStatus("CI status hidden: select a repo-bound thread.")
            }
            return
        }
        val responsePair = requestFirstAvailable(
            methods = listOf("ci/status", "cicd/status", "pipeline/status"),
            params = buildGitParams(gitScope)
        )
        if (responsePair == null) {
            val supplementalStatus = runCatching { fetchSupplementalCiStatus(gitScope) }
                .onFailure { AppLogger.warn(LOG_TAG, "Supplemental CI lookup failed: ${it.javaClass.simpleName}") }
                .getOrNull()
            if (supplementalStatus != null) {
                _ciStatus.value = supplementalStatus.summary
                if (supplementalStatus.isTerminal) {
                    publishEvent(
                        kind = ServiceEventKind.CI_CD_DONE,
                        title = "CI/CD Updated",
                        message = supplementalStatus.summary
                    )
                }
                if (!silentStatus) {
                    setStatus("CI status refreshed via supplemental provider API.")
                }
                return
            }
            _ciStatus.value = ""
            if (!silentStatus) {
                setStatus("CI status unavailable for this repository.")
            }
            return
        }

        val result = responsePair.second.result as? JsonObject ?: JsonObject(emptyMap())
        val state = result.string("status", "state", "conclusion")?.lowercase().orEmpty()
        val buildName = result.string("name", "pipeline", "workflow", "build")
        _ciStatus.value = if (buildName.isNullOrBlank()) {
            "CI status: ${if (state.isBlank()) "unknown" else state}"
        } else {
            "CI status: ${if (state.isBlank()) "unknown" else state} ($buildName)"
        }

        if (state == "passed" || state == "success" || state == "failed" || state == "error") {
            publishEvent(
                kind = ServiceEventKind.CI_CD_DONE,
                title = "CI/CD Updated",
                message = _ciStatus.value
            )
        }

        if (!silentStatus) {
            setStatus("CI status refreshed via $activeTransportLabel.")
        }
    }

    suspend fun forceRefreshWorkspace() {
        ensureConnected()
        val failedSections = mutableListOf<String>()

        runCatching { refreshThreads(silentStatus = true, includeTimeline = false) }.onFailure { failedSections += "threads" }
        runCatching { refreshActiveThreadTimeline(silentStatus = true) }.onFailure { failedSections += "timeline" }
        runCatching { refreshGitStatus(silentStatus = true) }.onFailure { failedSections += "git-status" }
        runCatching { refreshGitBranches(silentStatus = true) }.onFailure { failedSections += "git-branches" }
        runCatching { refreshRateLimitInfo(silentStatus = true) }.onFailure { failedSections += "rate-limits" }
        runCatching { refreshPendingPermissions(silentStatus = true) }.onFailure { failedSections += "permissions" }
        runCatching { refreshModels(silentStatus = true) }.onFailure { failedSections += "models" }
        runCatching { refreshCiStatus(silentStatus = true) }.onFailure { failedSections += "ci" }

        if (failedSections.isEmpty()) {
            setStatus("Workspace refreshed via press-and-slide gesture.", notify = true)
        } else {
            setStatus(
                "Workspace refresh completed with partial failures: ${failedSections.joinToString(", ")}.",
                notify = true
            )
        }
    }

    private suspend fun resolveInterruptTurnId(threadId: String): String? {
        val response = requestRpc(
            method = "thread/read",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "includeTurns" to JsonPrimitive(true)
                )
            )
        )
        throwIfRpcError(response, "thread/read")
        val resultObject = response.result as? JsonObject ?: return null
        return parseActiveTurnId(resultObject)
    }

    private fun parseActiveTurnId(result: JsonObject): String? {
        val threadObject = result["thread"] as? JsonObject ?: return null
        val turns = threadObject["turns"] as? JsonArray ?: return null

        var newestRunningTurnId: String? = null

        for (turnElement in turns) {
            val turnObject = turnElement as? JsonObject ?: continue
            val turnId = turnObject.string("id") ?: continue
            val status = turnObject.string("status", "turnStatus", "turn_status", "state")
                ?.lowercase()
                ?.trim()
                ?: continue
            if (status == "in_progress"
                || status == "running"
                || status == "streaming"
                || status == "queued"
                || status == "pending"
            ) {
                newestRunningTurnId = turnId
            }
        }

        return newestRunningTurnId
    }

    private data class GitScope(
        val threadId: String,
        val cwd: String
    )

    private fun parseThreadReadCwd(result: JsonObject): String? {
        val threadObject = result["thread"] as? JsonObject
        val threadScoped = threadObject?.string("cwd", "current_working_directory", "working_directory")
        if (!threadScoped.isNullOrBlank()) {
            return threadScoped
        }
        return result.string("cwd", "current_working_directory", "working_directory")
    }

    private fun updateThreadProjectPath(threadId: String, candidatePath: String?) {
        val normalized = normalizeProjectPath(candidatePath) ?: return
        projectPathByThread[threadId] = normalized
    }

    private fun updateCurrentProjectPathForThread(threadId: String) {
        val scopedPath = projectPathByThread[threadId]
            ?: _threads.value.firstOrNull { it.id == threadId }?.cwd
        val normalized = normalizeProjectPath(scopedPath) ?: return
        _currentProjectPath.value = normalized
    }

    private fun normalizeProjectPath(candidatePath: String?): String? {
        return normalizeFilesystemProjectPath(candidatePath)
    }

    private suspend fun fetchBridgeManagedStatusSnapshot(): JsonObject {
        val responsePair = requestFirstAvailable(
            methods = listOf("account/status/read", "getAuthStatus")
        ) ?: throw IllegalStateException("Bridge-managed account status unavailable from relay.")
        return responsePair.second.result as? JsonObject
            ?: throw IllegalStateException("Bridge-managed account status response missing payload.")
    }

    private fun applyBridgeManagedAccountSnapshot(payload: JsonObject) {
        val status = payload.string("status", "authStatus", "accountStatus")?.trim()?.lowercase()
        val loginInFlight = payload.bool("loginInFlight", "login_in_flight") == true
        val needsReauth = payload.bool("needsReauth", "needs_reauth") == true
        val isAuthenticated = status == "authenticated" || payload.bool("authenticated", "isAuthenticated") == true
        _gptAccountStatus.value = when {
            needsReauth -> BridgeManagedAccountStatus.REAUTH_REQUIRED
            loginInFlight -> BridgeManagedAccountStatus.LOGIN_IN_PROGRESS
            isAuthenticated -> BridgeManagedAccountStatus.AUTHENTICATED
            status == "not_logged_in" || status == "logged_out" || status == "unauthenticated" -> BridgeManagedAccountStatus.NOT_LOGGED_IN
            else -> BridgeManagedAccountStatus.UNKNOWN
        }
        _gptAccountEmail.value = payload.string("email", "accountEmail", "userEmail")
        val tokenReady = payload.bool("tokenReady", "token_ready") ?: false
        if (_gptAccountStatus.value == BridgeManagedAccountStatus.AUTHENTICATED && !tokenReady) {
            _voiceRecoverySnapshot.value = RecoveryAccessorySnapshot(
                title = "Voice Mode",
                summary = "Voice mode is still syncing from your Mac.",
                detail = "Keep the bridge connected for a moment, then try again.",
                status = RecoveryAccessoryStatus.SYNCING
            )
        } else if (_gptAccountStatus.value == BridgeManagedAccountStatus.AUTHENTICATED) {
            _voiceRecoverySnapshot.value = null
        }
    }

    private fun applyBridgePackageStatus(
        payload: JsonObject,
        allowAvailableBridgeUpdatePrompt: Boolean
    ) {
        _bridgeInstalledVersion.value = payload.string(
            "bridgeVersion",
            "bridge_version",
            "bridgePackageVersion",
            "bridge_package_version"
        )
        _latestBridgePackageVersion.value = payload.string(
            "bridgeLatestVersion",
            "bridge_latest_version",
            "bridgePublishedVersion",
            "bridge_published_version"
        )
        if (allowAvailableBridgeUpdatePrompt) {
            val latest = _latestBridgePackageVersion.value?.trim()
            val installed = _bridgeInstalledVersion.value?.trim()
            if (!latest.isNullOrEmpty()
                && !installed.isNullOrEmpty()
                && latest != installed
                && latest != lastOptionalBridgeUpdateVersion
            ) {
                lastOptionalBridgeUpdateVersion = latest
                setStatus("A newer Remodex update is available on your Mac ($installed -> $latest).", notify = true)
            }
        }
    }

    private fun applyDisconnectedBridgeManagedState() {
        if (_gptAccountStatus.value == BridgeManagedAccountStatus.UNKNOWN) {
            return
        }
        _gptAccountStatus.value = BridgeManagedAccountStatus.UNKNOWN
        _gptAccountEmail.value = null
        _voiceRecoverySnapshot.value = null
    }

    private fun resolveSelectedThreadGitScopeOrNull(): GitScope? {
        val threadId = _selectedThreadId.value ?: return null
        val scopedPath = projectPathByThread[threadId]
            ?: _threads.value.firstOrNull { it.id == threadId }?.cwd
        val normalizedPath = normalizeProjectPath(scopedPath) ?: return null
        projectPathByThread[threadId] = normalizedPath
        _currentProjectPath.value = normalizedPath
        return GitScope(threadId = threadId, cwd = normalizedPath)
    }

    private fun requireSelectedThreadGitScope(): GitScope {
        return resolveSelectedThreadGitScopeOrNull()
            ?: throw IllegalStateException("Select a thread bound to a local repository first.")
    }

    private fun buildGitParams(gitScope: GitScope): JsonObject {
        return JsonObject(
            mapOf(
                "cwd" to JsonPrimitive(gitScope.cwd),
                "threadId" to JsonPrimitive(gitScope.threadId)
            )
        )
    }

    private fun parseGitStatusSummary(result: JsonObject): String {
        val statusObject = (result["status"] as? JsonObject) ?: result
        val branch = statusObject.string("branch", "currentBranch", "current_branch") ?: "unknown"
        val clean = statusObject.bool("isClean", "clean", "is_clean")
        val ahead = statusObject.int("ahead", "aheadCount", "ahead_count")
        val behind = statusObject.int("behind", "behindCount", "behind_count")
        val staged = statusObject.int("stagedCount", "staged_count", "staged")
            ?: statusObject.arraySize("stagedFiles", "staged_files")
            ?: 0
        val unstaged = statusObject.int("unstagedCount", "unstaged_count", "unstaged")
            ?: statusObject.arraySize("unstagedFiles", "unstaged_files")
            ?: 0
        val untracked = statusObject.int("untrackedCount", "untracked_count", "untracked")
            ?: statusObject.arraySize("untrackedFiles", "untracked_files")
            ?: 0

        return if (clean == true && staged == 0 && unstaged == 0 && untracked == 0 && ahead == 0 && behind == 0) {
            "Branch $branch is clean."
        } else {
            "Branch $branch · ahead $ahead · behind $behind · staged $staged · unstaged $unstaged · untracked $untracked"
        }
    }

    private suspend fun <T> withGitActionProgress(label: String, block: suspend () -> T): T {
        _gitActionStatus.value = label
        return try {
            block()
        } finally {
            _gitActionStatus.value = null
        }
    }

    private fun parseGitRepoRoot(result: JsonObject): String? {
        val statusObject = (result["status"] as? JsonObject) ?: result
        return statusObject.string("repoRoot", "repo_root", "cwd", "working_directory")
    }

    private suspend fun fetchSupplementalCiStatus(gitScope: GitScope): SupplementalCiStatus? {
        val remote = fetchGitRemoteDescriptor(gitScope) ?: return null
        return when (remote.provider) {
            CiProvider.GITHUB -> fetchGitHubCiStatus(remote)
            CiProvider.GITLAB -> fetchGitLabCiStatus(remote)
        }
    }

    private suspend fun fetchGitRemoteDescriptor(gitScope: GitScope): GitRemoteDescriptor? {
        val responsePair = requestFirstAvailable(
            methods = listOf("git/remoteUrl"),
            params = buildGitParams(gitScope)
        ) ?: return null
        val result = responsePair.second.result as? JsonObject ?: return null
        return parseGitRemoteDescriptor(
            rawUrl = result.string("url", "remoteUrl", "remote_url"),
            ownerRepoHint = result.string("ownerRepo", "owner_repo")
        )
    }

    private suspend fun fetchGitHubCiStatus(remote: GitRemoteDescriptor): SupplementalCiStatus? {
        return fetchSupplementalCiJson(buildGitHubActionsUrl(remote.ownerRepo)) { body ->
            val root = ciJson.parseToJsonElement(body) as? JsonObject ?: return@fetchSupplementalCiJson null
            formatGitHubActionsStatus(root)
        }
    }

    private suspend fun fetchGitLabCiStatus(remote: GitRemoteDescriptor): SupplementalCiStatus? {
        return fetchSupplementalCiJson(buildGitLabPipelinesUrl(remote.ownerRepo)) { body ->
            val root = ciJson.parseToJsonElement(body) as? JsonArray ?: return@fetchSupplementalCiJson null
            formatGitLabPipelineStatus(root)
        }
    }

    private suspend fun fetchSupplementalCiJson(
        url: String,
        parser: (String) -> SupplementalCiStatus?
    ): SupplementalCiStatus? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Remodex-Android")
            .build()
        runCatching {
            ciHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val body = response.body?.string()?.trim().orEmpty()
                if (body.isEmpty()) {
                    return@withContext null
                }
                parser(body)
            }
        }.recoverCatching { error ->
            if (error is IOException) {
                AppLogger.info(LOG_TAG, "CI provider request skipped: ${error.javaClass.simpleName}")
                null
            } else {
                throw error
            }
        }.getOrNull()
    }

    private fun parseGitBranches(result: JsonObject): List<String> {
        val rawBranches = (result["branches"] as? JsonArray)
            ?: (result["data"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: JsonArray(emptyList())

        return rawBranches.mapNotNull { branchElement ->
            when (branchElement) {
                is JsonPrimitive -> branchElement.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                is JsonObject -> branchElement.string("name", "branch", "label")
                else -> null
            }
        }.distinct()
    }

    private fun parseFuzzyFileMatches(result: JsonObject, limit: Int): List<FileAutocompleteMatch> {
        val rootArray = (result["files"] as? JsonArray)
            ?: (result["data"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: JsonArray(emptyList())

        return rootArray.mapNotNull { element ->
            val objectValue = element as? JsonObject ?: return@mapNotNull null
            val rawPath = objectValue.string("path", "relativePath", "filePath", "fullPath")
                ?: return@mapNotNull null
            val normalizedPath = rawPath.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val fileName = objectValue.string("fileName", "name")
                ?: normalizedPath.substringAfterLast('/').substringAfterLast('\\')
            if (fileName.isBlank()) {
                return@mapNotNull null
            }
            FileAutocompleteMatch(path = normalizedPath, fileName = fileName)
        }
            .distinctBy { it.path.lowercase() }
            .take(limit.coerceAtLeast(1))
    }

    private fun parseSkills(result: JsonObject, limit: Int): List<SkillSuggestion> {
        val bucketedSkills = (result["data"] as? JsonArray)?.flatMap { bucketElement ->
            val bucket = bucketElement as? JsonObject ?: return@flatMap emptyList()
            ((bucket["skills"] as? JsonArray) ?: JsonArray(emptyList())).toList()
        } ?: emptyList()

        val directSkills = (result["skills"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: JsonArray(emptyList())

        val rawSkills = if (bucketedSkills.isNotEmpty()) {
            bucketedSkills
        } else {
            directSkills.toList()
        }

        return rawSkills.mapNotNull { element ->
            val skillObject = element as? JsonObject ?: return@mapNotNull null
            val name = skillObject.string("name", "id")?.trim().orEmpty()
            if (name.isEmpty()) {
                return@mapNotNull null
            }
            val id = skillObject.string("id", "name")?.trim().orEmpty().ifEmpty { name }
            SkillSuggestion(
                id = id,
                name = name,
                description = skillObject.string("description", "summary"),
                path = skillObject.string("path"),
                enabled = skillObject.bool("enabled", "isEnabled") ?: true
            )
        }
            .groupBy { it.name.lowercase() }
            .mapNotNull { (_, bucket) ->
                bucket.firstOrNull { it.enabled } ?: bucket.firstOrNull()
            }
            .sortedBy { it.name.lowercase() }
            .take(limit.coerceAtLeast(1))
    }

    private fun parseModelList(result: JsonObject): List<String> {
        val rawModels = (result["models"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: (result["data"] as? JsonArray)
            ?: JsonArray(emptyList())

        return rawModels.mapNotNull { modelElement ->
            when (modelElement) {
                is JsonPrimitive -> modelElement.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                is JsonObject -> modelElement.string("id", "name", "model")
                else -> null
            }
        }.distinct()
    }

    private fun parseReasoningEfforts(result: JsonObject): List<String> {
        val normalized = linkedSetOf<String>()

        fun addCandidate(value: String?) {
            val candidate = value?.trim()?.lowercase().orEmpty()
            if (candidate.isNotEmpty()) {
                normalized += candidate
            }
        }

        fun addFromArray(array: JsonArray?) {
            array?.forEach { element ->
                when (element) {
                    is JsonPrimitive -> addCandidate(element.contentOrNull)
                    is JsonObject -> addCandidate(
                        element.string(
                            "reasoningEffort",
                            "reasoning_effort",
                            "effort",
                            "id",
                            "name"
                        )
                    )
                    else -> Unit
                }
            }
        }

        val modelItems = (result["models"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: (result["data"] as? JsonArray)
            ?: JsonArray(emptyList())
        modelItems.forEach { modelElement ->
            val objectValue = modelElement as? JsonObject ?: return@forEach
            addFromArray(objectValue["supportedReasoningEfforts"] as? JsonArray)
            addFromArray(objectValue["supported_reasoning_efforts"] as? JsonArray)
            addFromArray(objectValue["reasoningEfforts"] as? JsonArray)
            addFromArray(objectValue["reasoning_efforts"] as? JsonArray)
        }
        addFromArray(result["reasoningEfforts"] as? JsonArray)
        addFromArray(result["reasoning_efforts"] as? JsonArray)
        addFromArray(result["efforts"] as? JsonArray)

        return if (normalized.isEmpty()) {
            listOf("low", "medium", "high")
        } else {
            normalized.toList()
        }
    }

    private fun parseSelectedReasoningEffort(result: JsonObject): String? {
        return result.string(
            "selectedReasoningEffort",
            "selected_reasoning_effort",
            "reasoningEffort",
            "reasoning_effort",
            "effort"
        )?.trim()?.lowercase()
    }

    private fun parsePendingPermissions(result: JsonObject): List<PendingPermissionRequest> {
        val rawPermissions = (result["permissions"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: (result["pending"] as? JsonArray)
            ?: JsonArray(emptyList())

        return rawPermissions.mapNotNull { permissionElement ->
            when (permissionElement) {
                is JsonPrimitive -> {
                    val raw = permissionElement.contentOrNull?.trim().orEmpty()
                    if (raw.isEmpty()) {
                        null
                    } else {
                        PendingPermissionRequest(
                            id = raw,
                            title = raw,
                            summary = null
                        )
                    }
                }
                is JsonObject -> {
                    val id = permissionElement.string("id", "permissionId", "requestId", "token")
                        ?: return@mapNotNull null
                    PendingPermissionRequest(
                        id = id,
                        title = permissionElement.string("title", "action", "tool", "name") ?: "Permission $id",
                        summary = permissionElement.string("summary", "reason", "command", "details")
                    )
                }
                else -> null
            }
        }
    }

    private fun parseRateLimitInfo(result: JsonObject): Pair<String, Int?> {
        val summaryRows = parseRateLimitSummaryRows(result)
        if (summaryRows.isNotEmpty()) {
            val sortedRows = summaryRows.sortedWith(
                compareBy<RateLimitSummaryRow>({ it.windowDurationMins ?: Int.MAX_VALUE }, { it.label.lowercase() })
            )
            val summary = sortedRows.take(2).joinToString(" · ") { row ->
                "${row.remainingPercent}% ${row.label}"
            }
            return "Rate limit: $summary" to sortedRows.minOfOrNull { it.remainingPercent }
        }

        val rateObject = (result["rateLimit"] as? JsonObject)
            ?: (result["rate_limit"] as? JsonObject)
            ?: result

        val limit = rateObject.int("limit", "max", "windowLimit", "window_limit")
        val remaining = rateObject.int("remaining", "remainingRequests", "remaining_requests")
        val resetEpochSeconds = rateObject.long("resetAt", "resetAtEpoch", "resetEpochSeconds", "reset_epoch_seconds")
        val retryAfterSeconds = rateObject.int("retryAfterSeconds", "retry_after_seconds")

        val normalizedResetEpoch = when {
            resetEpochSeconds != null -> {
                if (resetEpochSeconds > 10_000_000_000L) {
                    resetEpochSeconds / 1_000L
                } else {
                    resetEpochSeconds
                }
            }
            retryAfterSeconds != null -> (System.currentTimeMillis() / 1_000L) + retryAfterSeconds
            else -> null
        }

        val summary = if (limit != null || remaining != null || normalizedResetEpoch != null) {
            val resetText = normalizedResetEpoch?.let { "reset @$it" } ?: "reset unknown"
            "Rate limit: ${remaining ?: "?"}/${limit ?: "?"} · $resetText"
        } else {
            "Rate limit response available but fields were empty."
        }

        return summary to remaining
    }

    private fun parseRateLimitSummaryRows(result: JsonObject): List<RateLimitSummaryRow> {
        val payload = (result["result"] as? JsonObject) ?: result

        val keyedBuckets = (payload["rateLimitsByLimitId"] as? JsonObject)
            ?: (payload["rate_limits_by_limit_id"] as? JsonObject)
        if (keyedBuckets != null) {
            return keyedBuckets.entries.flatMap { (limitId, element) ->
                parseRateLimitBucketRows(limitId, element as? JsonObject)
            }
        }

        val nestedBuckets = (payload["rateLimits"] as? JsonObject)
            ?: (payload["rate_limits"] as? JsonObject)
        if (nestedBuckets != null) {
            val directRows = parseDirectRateLimitRows(
                defaultLabel = payload.string("limitName", "limit_name", "name") ?: "Primary",
                bucketObject = nestedBuckets
            )
            if (directRows.isNotEmpty()) {
                return directRows
            }
            return parseRateLimitBucketRows(null, nestedBuckets)
        }

        return parseDirectRateLimitRows(
            defaultLabel = payload.string("limitName", "limit_name", "name") ?: "Primary",
            bucketObject = payload
        )
    }

    private fun parseRateLimitBucketRows(
        explicitLimitId: String?,
        bucketObject: JsonObject?
    ): List<RateLimitSummaryRow> {
        if (bucketObject == null) {
            return emptyList()
        }

        val fallbackLabel = bucketObject.string("limitName", "limit_name", "name")
            ?: explicitLimitId
            ?: "Rate limit"

        return buildList {
            parseRateLimitWindow(
                label = fallbackLabel,
                value = bucketObject["primary"] as? JsonObject ?: bucketObject["primary_window"] as? JsonObject
            )?.let { add(it) }
            parseRateLimitWindow(
                label = bucketObject.string("secondaryName", "secondary_name") ?: fallbackLabel,
                value = bucketObject["secondary"] as? JsonObject ?: bucketObject["secondary_window"] as? JsonObject
            )?.let { add(it) }
        }
    }

    private fun parseDirectRateLimitRows(
        defaultLabel: String,
        bucketObject: JsonObject
    ): List<RateLimitSummaryRow> {
        val primary = parseRateLimitWindow(
            label = defaultLabel,
            value = bucketObject["primary"] as? JsonObject ?: bucketObject["primary_window"] as? JsonObject
        )
        val secondary = parseRateLimitWindow(
            label = bucketObject.string("secondaryName", "secondary_name") ?: "Secondary",
            value = bucketObject["secondary"] as? JsonObject ?: bucketObject["secondary_window"] as? JsonObject
        )
        return listOfNotNull(primary, secondary)
    }

    private fun parseRateLimitWindow(label: String, value: JsonObject?): RateLimitSummaryRow? {
        if (value == null) {
            return null
        }

        val usedPercent = value.int("usedPercent", "used_percent") ?: return null
        val windowDurationMins = value.int(
            "windowDurationMins",
            "window_duration_mins",
            "windowMinutes",
            "window_minutes"
        )
        val resetsAtEpochSeconds = value.long("resetsAt", "resets_at", "resetAt", "reset_epoch_seconds")
            ?.let { if (it > 10_000_000_000L) it / 1_000L else it }

        return RateLimitSummaryRow(
            label = formatRateLimitLabel(label, windowDurationMins),
            remainingPercent = (100 - usedPercent).coerceIn(0, 100),
            windowDurationMins = windowDurationMins,
            resetsAtEpochSeconds = resetsAtEpochSeconds
        )
    }

    private fun formatRateLimitLabel(fallbackLabel: String, windowDurationMins: Int?): String {
        val minutes = windowDurationMins ?: return fallbackLabel
        val weekMinutes = 7 * 24 * 60
        val dayMinutes = 24 * 60
        return when {
            minutes > 0 && minutes % weekMinutes == 0 -> if (minutes == weekMinutes) "Weekly" else "${minutes / weekMinutes}w"
            minutes > 0 && minutes % dayMinutes == 0 -> "${minutes / dayMinutes}d"
            minutes > 0 && minutes % 60 == 0 -> "${minutes / 60}h"
            minutes > 0 -> "${minutes}m"
            else -> fallbackLabel
        }
    }

    private data class RateLimitSummaryRow(
        val label: String,
        val remainingPercent: Int,
        val windowDurationMins: Int?,
        val resetsAtEpochSeconds: Long?
    )

    private fun JsonObject.string(vararg keys: String): String? {
        for (key in keys) {
            val candidate = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!candidate.isNullOrEmpty()) {
                return candidate
            }
        }
        return null
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            primitive.intOrNull?.let { return it }
            primitive.contentOrNull?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.long(vararg keys: String): Long? {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            primitive.longOrNull?.let { return it }
            primitive.contentOrNull?.toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.bool(vararg keys: String): Boolean? {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            primitive.booleanOrNull?.let { return it }
            primitive.contentOrNull?.toBooleanStrictOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.arraySize(vararg keys: String): Int? {
        for (key in keys) {
            val value = this[key] as? JsonArray ?: continue
            return value.size
        }
        return null
    }

    private fun ensureConnected() {
        if (_connectionState.value != ConnectionState.Connected) {
            AppLogger.warn(LOG_TAG, "Operation blocked because connection state is ${_connectionState.value}.")
            throw IllegalStateException("Connect first.")
        }
    }

    private suspend fun requestFirstAvailable(
        methods: List<String>,
        params: JsonObject = JsonObject(emptyMap())
    ): Pair<String, RpcMessage>? {
        if (methods.isEmpty()) {
            return null
        }

        val cacheKey = methods.joinToString("|")
        if (unavailableMethodKeyCache.contains(cacheKey)) {
            return null
        }
        val cachedMethod = rpcMethodAliasCache[cacheKey]
        val orderedMethods = buildList {
            if (!cachedMethod.isNullOrBlank() && methods.contains(cachedMethod)) {
                add(cachedMethod)
            }
            methods.forEach { method ->
                if (method != cachedMethod) {
                    add(method)
                }
            }
        }

        for (method in orderedMethods) {
            val response = requestRpc(method = method, params = params)
            val rpcError = response.error
            if (rpcError != null && isMethodUnavailableError(rpcError)) {
                if (rpcMethodAliasCache[cacheKey] == method) {
                    rpcMethodAliasCache.remove(cacheKey)
                }
                AppLogger.debug(LOG_TAG, "RPC method not found on relay: $method")
                continue
            }
            throwIfRpcError(response, method)
            rpcMethodAliasCache[cacheKey] = method
            unavailableMethodKeyCache.remove(cacheKey)
            AppLogger.debug(LOG_TAG, "RPC method succeeded: $method")
            return method to response
        }
        rpcMethodAliasCache.remove(cacheKey)
        unavailableMethodKeyCache.add(cacheKey)
        AppLogger.info(LOG_TAG, "No candidate methods were available: ${methods.joinToString(",")}")
        return null
    }

    private fun isMethodUnavailableError(rpcError: RpcError): Boolean {
        if (rpcError.code == -32601) {
            return true
        }
        if (rpcError.code != -32600) {
            return false
        }
        val message = rpcError.message.lowercase()
        return message.contains("unknown variant")
            || message.contains("unknown method")
            || message.contains("method not found")
            || message.contains("unsupported method")
    }

    private fun throwIfRpcError(response: RpcMessage, method: String) {
        val rpcError = response.error ?: return
        AppLogger.warn(
            LOG_TAG,
            "RPC error for method=$method code=${rpcError.code} message=${rpcError.message}"
        )
        throw IllegalStateException("$method failed (${rpcError.code}): ${rpcError.message}")
    }

    private fun shouldRetryInitializeWithoutCapabilities(rpcError: RpcError): Boolean {
        if (rpcError.code != -32600 && rpcError.code != -32602) {
            return false
        }
        val message = rpcError.message.lowercase()
        if (!message.contains("capabilities") && !message.contains("experimentalapi")) {
            return false
        }
        return message.contains("unknown")
            || message.contains("unexpected")
            || message.contains("unrecognized")
            || message.contains("invalid")
            || message.contains("unsupported")
            || message.contains("field")
    }

    private fun isAlreadyInitializedError(rpcError: RpcError): Boolean {
        return rpcError.message.lowercase().contains("already initialized")
    }

    private fun setStatus(
        message: String,
        notify: Boolean = false,
        eventKind: ServiceEventKind = ServiceEventKind.WORK_STATUS_CHANGED
    ) {
        _status.value = message
        AppLogger.info(LOG_TAG, "status[$eventKind]: $message")
        if (notify) {
            publishEvent(kind = eventKind, title = eventTitleFor(eventKind), message = message)
        }
    }

    private fun publishEvent(kind: ServiceEventKind, title: String, message: String) {
        _events.tryEmit(
            ServiceEvent(
                kind = kind,
                title = title,
                message = message
            )
        )
    }

    private fun eventTitleFor(kind: ServiceEventKind): String {
        return when (kind) {
            ServiceEventKind.WORK_STATUS_CHANGED -> "Working Status"
            ServiceEventKind.PERMISSION_REQUIRED -> "Permission Required"
            ServiceEventKind.RATE_LIMIT_HIT -> "Rate Limit"
            ServiceEventKind.GIT_ACTION -> "Git Action"
            ServiceEventKind.CI_CD_DONE -> "CI/CD"
        }
    }
}
