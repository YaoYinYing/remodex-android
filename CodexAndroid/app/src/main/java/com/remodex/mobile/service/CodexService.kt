package com.remodex.mobile.service

import com.remodex.mobile.model.RpcMessage
import com.remodex.mobile.model.RpcError
import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.service.logging.AppLogger
import com.remodex.mobile.service.push.PushRegistrationPayload
import com.remodex.mobile.service.secure.CodexSecureTransport
import com.remodex.mobile.service.transport.FixtureRpcTransport
import com.remodex.mobile.service.transport.RealSecureRelayRpcTransport
import com.remodex.mobile.service.transport.RpcTransport
import com.remodex.mobile.service.transport.RpcTransportParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

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

class CodexService(
    private val secureTransport: CodexSecureTransport = CodexSecureTransport(),
    private val parser: RpcTransportParser = RpcTransportParser(),
    private val pairingStore: PairingStateStore? = null
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

    private val _pendingPermissions = MutableStateFlow<List<PendingPermissionRequest>>(emptyList())
    val pendingPermissions: StateFlow<List<PendingPermissionRequest>> = _pendingPermissions

    private val _rateLimitInfo = MutableStateFlow("Rate limit info not loaded.")
    val rateLimitInfo: StateFlow<String> = _rateLimitInfo

    private val _ciStatus = MutableStateFlow("CI status not loaded.")
    val ciStatus: StateFlow<String> = _ciStatus

    private val _events = MutableSharedFlow<ServiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServiceEvent> = _events

    private val activeTurnIdByThread = mutableMapOf<String, String>()
    private var rpcTransport: RpcTransport = FixtureRpcTransport()
    private var activeTransportLabel: String = "none"
    private var activeConnectionMode: ConnectionMode = ConnectionMode.NONE

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

    suspend fun connectWithFixture() {
        connect(
            transport = FixtureRpcTransport(),
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
            transport = RealSecureRelayRpcTransport(pairing = pairing, secureTransport = secureTransport),
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
            rpcTransport.open()
            initializeSession()
            activeTransportLabel = modeLabel
            activeConnectionMode = connectionMode
            _connectionState.value = ConnectionState.Connected

            refreshThreads(silentStatus = true)
            refreshGitStatus(silentStatus = true)
            refreshGitBranches(silentStatus = true)
            refreshRateLimitInfo(silentStatus = true)
            refreshPendingPermissions(silentStatus = true)
            refreshModels(silentStatus = true)
            refreshCiStatus(silentStatus = true)

            AppLogger.info(LOG_TAG, "connect($modeLabel) completed successfully.")
            setStatus("Connected via $modeLabel.", notify = true)
        } catch (error: Throwable) {
            activeConnectionMode = ConnectionMode.NONE
            AppLogger.error(LOG_TAG, "connect($modeLabel) failed.", error)
            _connectionState.value = ConnectionState.Failed(error.message ?: "Unknown connection failure.")
            setStatus("Connect failed: ${error.message ?: "unknown error"}", notify = true)
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
        AppLogger.info(LOG_TAG, "initialize completed for active relay session.")

        runCatching {
            rpcTransport.notify(
                method = "initialized",
                params = JsonObject(emptyMap())
            )
        }.onFailure { error ->
            AppLogger.warn(
                LOG_TAG,
                "initialized notification failed non-fatally; continuing session startup.",
                error
            )
        }
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
        runCatching { rpcTransport.close() }
        activeConnectionMode = ConnectionMode.NONE
        _connectionState.value = ConnectionState.Paired
        _timeline.value = emptyList()
        activeTurnIdByThread.clear()
        AppLogger.info(LOG_TAG, "disconnect completed; connection state set back to PAIRED.")
        setStatus("Disconnected.", notify = true)
    }

    suspend fun startThread() {
        ensureConnected()
        val response = rpcTransport.request(
            method = "thread/start",
            params = JsonObject(emptyMap())
        )
        throwIfRpcError(response, "thread/start")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("thread/start result is missing.")
        val createdThread = resultObject["thread"] as? JsonObject
        val createdThreadId = createdThread?.string("id")
        refreshThreads(silentStatus = true)
        if (!createdThreadId.isNullOrBlank()) {
            openThread(createdThreadId, silentStatus = true)
            setStatus("Created thread $createdThreadId via $activeTransportLabel.", notify = true)
        } else {
            setStatus("Created thread via $activeTransportLabel.", notify = true)
        }
    }

    suspend fun refreshThreads(silentStatus: Boolean = false) {
        ensureConnected()
        val response = rpcTransport.request(
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
        val parsedThreads = parser.parseThreadList(resultObject)
        _threads.value = parsedThreads

        val selectedThreadId = _selectedThreadId.value
        val resolvedSelection = when {
            parsedThreads.isEmpty() -> null
            selectedThreadId != null && parsedThreads.any { it.id == selectedThreadId } -> selectedThreadId
            else -> parsedThreads.first().id
        }

        _selectedThreadId.value = resolvedSelection
        if (resolvedSelection != null) {
            val selectedThread = parsedThreads.firstOrNull { it.id == resolvedSelection }
            val selectedThreadCwd = selectedThread?.cwd?.trim().orEmpty()
            if (selectedThreadCwd.isNotEmpty()) {
                _currentProjectPath.value = selectedThreadCwd
            }
            openThread(resolvedSelection, silentStatus = true)
        } else {
            _timeline.value = emptyList()
        }

        if (!silentStatus) {
            setStatus("Loaded ${parsedThreads.size} thread(s) via $activeTransportLabel.")
        }
    }

    suspend fun openThread(threadId: String, silentStatus: Boolean = false) {
        ensureConnected()
        _selectedThreadId.value = threadId
        val response = rpcTransport.request(
            method = "thread/read",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "includeTurns" to JsonPrimitive(true)
                )
            )
        )
        throwIfRpcError(response, "thread/read")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("thread/read result is missing.")
        val parsedTimeline = parser.parseThreadTimeline(resultObject)
        _timeline.value = parsedTimeline
        val activeTurnId = parseActiveTurnId(resultObject)
        if (activeTurnId != null) {
            activeTurnIdByThread[threadId] = activeTurnId
        } else {
            activeTurnIdByThread.remove(threadId)
        }

        val selectedThreadCwd = _threads.value.firstOrNull { it.id == threadId }?.cwd?.trim().orEmpty()
        if (selectedThreadCwd.isNotEmpty()) {
            _currentProjectPath.value = selectedThreadCwd
        }

        if (!silentStatus) {
            setStatus("Loaded ${parsedTimeline.size} timeline item(s) for $threadId via $activeTransportLabel.")
        }
    }

    suspend fun sendTurnStart(inputText: String) {
        ensureConnected()
        val normalizedInput = inputText.trim()
        if (normalizedInput.isEmpty()) {
            return
        }

        val threadId = selectedThreadId.value
            ?: threads.value.firstOrNull()?.id
            ?: throw IllegalStateException("No thread is selected.")

        val response = rpcTransport.request(
            method = "turn/start",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "input" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("input_text"),
                                    "text" to JsonPrimitive(normalizedInput)
                                )
                            )
                        )
                    )
                )
            )
        )
        throwIfRpcError(response, "turn/start")
        val turnId = (response.result as? JsonObject)?.string("turnId", "turn_id")
        if (!turnId.isNullOrBlank()) {
            activeTurnIdByThread[threadId] = turnId
        }
        openThread(threadId, silentStatus = true)
        setStatus("Turn started on $threadId via $activeTransportLabel.", notify = true)
    }

    suspend fun interruptActiveTurn() {
        ensureConnected()
        val threadId = selectedThreadId.value
            ?: threads.value.firstOrNull()?.id
            ?: throw IllegalStateException("No thread is selected.")
        val resolvedTurnId = activeTurnIdByThread[threadId] ?: resolveInterruptTurnId(threadId)
        if (resolvedTurnId.isNullOrBlank()) {
            throw IllegalStateException("No active turn found for $threadId.")
        }
        val response = rpcTransport.request(
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

    suspend fun registerPushToken(payload: PushRegistrationPayload) {
        ensureConnected()
        val normalizedDeviceToken = payload.deviceToken.trim()
        if (normalizedDeviceToken.isEmpty()) {
            throw IllegalArgumentException("Push token is required.")
        }

        val response = rpcTransport.request(
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
        val response = rpcTransport.request(
            method = "git/status",
            params = JsonObject(emptyMap())
        )
        throwIfRpcError(response, "git/status")
        val resultObject = response.result as? JsonObject
            ?: throw IllegalStateException("git/status result is missing.")
        _gitStatusSummary.value = parseGitStatusSummary(resultObject)

        val repoRoot = parseGitRepoRoot(resultObject)
        if (!repoRoot.isNullOrBlank()) {
            _currentProjectPath.value = repoRoot
        }

        if (!silentStatus) {
            setStatus("Git status loaded via $activeTransportLabel.")
        }
    }

    suspend fun refreshGitBranches(silentStatus: Boolean = false) {
        ensureConnected()
        val response = rpcTransport.request(
            method = "git/branches",
            params = JsonObject(emptyMap())
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

        val response = rpcTransport.request(
            method = "git/checkout",
            params = JsonObject(
                mapOf(
                    "branch" to JsonPrimitive(normalizedBranch)
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

    suspend fun gitPull() {
        ensureConnected()
        val response = rpcTransport.request(
            method = "git/pull",
            params = JsonObject(emptyMap())
        )
        throwIfRpcError(response, "git/pull")
        refreshGitStatus(silentStatus = true)
        setStatus(
            "Pulled latest changes via $activeTransportLabel.",
            notify = true,
            eventKind = ServiceEventKind.GIT_ACTION
        )
    }

    suspend fun gitPush() {
        ensureConnected()
        val response = rpcTransport.request(
            method = "git/push",
            params = JsonObject(emptyMap())
        )
        throwIfRpcError(response, "git/push")
        refreshGitStatus(silentStatus = true)
        setStatus(
            "Pushed changes via $activeTransportLabel.",
            notify = true,
            eventKind = ServiceEventKind.GIT_ACTION
        )
    }

    suspend fun gitCommit(message: String?) {
        ensureConnected()
        val normalizedMessage = message?.trim().orEmpty()
        val params = if (normalizedMessage.isEmpty()) {
            JsonObject(emptyMap())
        } else {
            JsonObject(mapOf("message" to JsonPrimitive(normalizedMessage)))
        }
        val response = rpcTransport.request(
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

    suspend fun refreshRateLimitInfo(silentStatus: Boolean = false) {
        ensureConnected()
        val responsePair = requestFirstAvailable(
            methods = listOf("rate_limit/status", "ratelimit/status", "limits/read")
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

        val selected = result.string("selectedModel", "activeModel", "currentModel", "model")
        if (!selected.isNullOrBlank()) {
            _selectedModel.value = selected
        } else if (_availableModels.value.isNotEmpty() && _selectedModel.value.isBlank()) {
            _selectedModel.value = _availableModels.value.first()
        }

        if (!silentStatus) {
            setStatus("Model list refreshed via $activeTransportLabel.")
        }
    }

    suspend fun switchModel(model: String) {
        ensureConnected()
        val normalizedModel = model.trim()
        if (normalizedModel.isEmpty()) {
            throw IllegalArgumentException("Model is required.")
        }

        val responsePair = requestFirstAvailable(
            methods = listOf("models/select", "model/set"),
            params = JsonObject(mapOf("model" to JsonPrimitive(normalizedModel)))
        )
        if (responsePair == null) {
            throw IllegalStateException("Relay does not support model switching.")
        }

        _selectedModel.value = normalizedModel
        refreshModels(silentStatus = true)
        setStatus("Switched model to $normalizedModel via $activeTransportLabel.", notify = true)
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
        val responsePair = requestFirstAvailable(
            methods = listOf("ci/status", "cicd/status", "pipeline/status")
        )
        if (responsePair == null) {
            _ciStatus.value = "CI/CD status unavailable from relay."
            if (!silentStatus) {
                setStatus("CI/CD status unavailable from relay.")
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

        runCatching { refreshThreads(silentStatus = true) }.onFailure { failedSections += "threads" }
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
        val response = rpcTransport.request(
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

    private fun parseGitRepoRoot(result: JsonObject): String? {
        val statusObject = (result["status"] as? JsonObject) ?: result
        return statusObject.string("repoRoot", "repo_root", "cwd", "working_directory")
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
            "Rate limit: ${remaining ?: "?"}/${limit ?: "?"} remaining, $resetText"
        } else {
            "Rate limit response available but fields were empty."
        }

        return summary to remaining
    }

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
        for (method in methods) {
            val response = rpcTransport.request(method = method, params = params)
            val rpcError = response.error
            if (rpcError != null && rpcError.code == -32601) {
                AppLogger.debug(LOG_TAG, "RPC method not found on relay: $method")
                continue
            }
            throwIfRpcError(response, method)
            AppLogger.debug(LOG_TAG, "RPC method succeeded: $method")
            return method to response
        }
        AppLogger.info(LOG_TAG, "No candidate methods were available: ${methods.joinToString(",")}")
        return null
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
