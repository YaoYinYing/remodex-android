package com.remodex.mobile.service.transport

import com.remodex.mobile.model.RpcError
import com.remodex.mobile.model.RpcMessage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class FixtureRpcTransport : RpcTransport {
    private var isInitialized: Boolean = false
    private var currentBranch: String = "remodex/android-parity"
    private var commitCounter: Int = 0
    private var rateLimitLimit: Int = 200
    private var rateLimitRemaining: Int = 177
    private var rateLimitResetEpochSeconds: Long = (System.currentTimeMillis() / 1_000L) + 3_600L
    private var selectedModel: String = "gpt-5.4"
    private var ciPollCounter: Int = 0

    private val modelIds = mutableListOf(
        "gpt-5.4",
        "gpt-5.4-mini",
        "gpt-5.3-codex"
    )

    private val pendingPermissions = mutableListOf(
        JsonObject(
            mapOf(
                "id" to JsonPrimitive("perm-shell"),
                "title" to JsonPrimitive("Run shell command"),
                "summary" to JsonPrimitive("Allow execution of `git status` in current project.")
            )
        ),
        JsonObject(
            mapOf(
                "id" to JsonPrimitive("perm-write"),
                "title" to JsonPrimitive("Write workspace file"),
                "summary" to JsonPrimitive("Allow patching `/Users/yyy/Documents/protein_design/remodex`.")
            )
        )
    )

    private val branchNames = mutableListOf(
        "main",
        "remodex/android-parity",
        "remodex/push-parity"
    )

    private val threadSummaries = mutableListOf(
        JsonObject(
            mapOf(
                "id" to JsonPrimitive("thread-alpha"),
                "title" to JsonPrimitive("Android parity rollout"),
                "preview" to JsonPrimitive("Bridge/relay compatibility checks"),
                "cwd" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                "updated_at" to JsonPrimitive(1_710_000_111_000L)
            )
        ),
        JsonObject(
            mapOf(
                "id" to JsonPrimitive("thread-beta"),
                "name" to JsonPrimitive("Push registration parity"),
                "preview" to JsonPrimitive("FCM/APNs provider abstraction"),
                "current_working_directory" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex/relay"),
                "updatedAt" to JsonPrimitive(1_710_000_222L)
            )
        ),
        JsonObject(
            mapOf(
                "id" to JsonPrimitive("thread-gamma"),
                "title" to JsonPrimitive("Secure vectors"),
                "preview" to JsonPrimitive("Golden transcript + nonce checks")
            )
        )
    )

    private val turnsByThread = mutableMapOf(
        "thread-alpha" to mutableListOf(
            turn(
                id = "turn-1",
                userMessageItem(
                    id = "item-u1",
                    text = "Ship Android thread/timeline parity next."
                ),
                assistantMessageItem(
                    id = "item-a1",
                    text = "Implemented fixture-backed thread list and timeline rendering."
                ),
                systemReasoningItem(
                    id = "item-r1",
                    text = "Next: replace fixture transport with secure relay websocket."
                )
            ),
            turn(
                id = "turn-2",
                userMessageItem(
                    id = "item-u2",
                    text = "Run tests and verify on the ADB device."
                ),
                assistantMessageItem(
                    id = "item-a2",
                    text = "Unit tests pass and APK launches on device."
                )
            )
        ),
        "thread-beta" to mutableListOf(
            turn(
                id = "turn-1",
                userMessageItem(
                    id = "item-u1",
                    text = "How are Android push registrations handled?"
                ),
                assistantMessageItem(
                    id = "item-a1",
                    text = "Bridge and relay now store platform/provider metadata for APNs and FCM."
                )
            )
        ),
        "thread-gamma" to mutableListOf(
            turn(
                id = "turn-1",
                userMessageItem(
                    id = "item-u1",
                    text = "Keep secure vectors aligned across clients."
                ),
                assistantMessageItem(
                    id = "item-a1",
                    text = "Transcript and nonce vectors pass for mac/iphone/android senders."
                )
            )
        )
    )

    override suspend fun open() {
        // No-op for fixture mode.
    }

    override suspend fun close() {
        // No-op for fixture mode.
    }

    override suspend fun notify(method: String, params: JsonObject) {
        delay(20)
        if (method == "initialized") {
            isInitialized = true
        }
    }

    override suspend fun request(method: String, params: JsonObject): RpcMessage {
        delay(60)
        if (method == "initialize") {
            isInitialized = true
            return fixtureInitialize()
        }
        if (method != "initialized" && !isInitialized) {
            return fixtureNotInitialized(method)
        }
        return when (method) {
            "initialized" -> fixtureInitialized()
            "thread/list" -> fixtureThreadList()
            "thread/read" -> fixtureThreadRead(params)
            "thread/start" -> fixtureThreadStart()
            "turn/start" -> fixtureTurnStart(params)
            "turn/interrupt" -> fixtureTurnInterrupt(params)
            "notifications/push/register" -> fixturePushRegister(params)
            "git/status" -> fixtureGitStatus()
            "git/branches" -> fixtureGitBranches()
            "git/checkout" -> fixtureGitCheckout(params)
            "git/pull" -> fixtureGitPull()
            "git/push" -> fixtureGitPush()
            "git/commit" -> fixtureGitCommit(params)
            "rate_limit/status" -> fixtureRateLimitStatus()
            "models/list" -> fixtureModelList()
            "models/select" -> fixtureModelSelect(params)
            "permissions/pending" -> fixturePermissionList()
            "permissions/grant" -> fixturePermissionGrant(params)
            "ci/status" -> fixtureCiStatus()
            else -> RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-$method"),
                error = RpcError(
                    code = -32601,
                    message = "Method not implemented in fixture transport."
                )
            )
        }
    }

    private fun fixtureInitialize(): RpcMessage {
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-initialize"),
            result = JsonObject(
                mapOf(
                    "name" to JsonPrimitive("remodex-fixture-runtime"),
                    "version" to JsonPrimitive("0.1.0")
                )
            )
        )
    }

    private fun fixtureInitialized(): RpcMessage {
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-initialized"),
            result = JsonObject(emptyMap())
        )
    }

    private fun fixtureNotInitialized(method: String): RpcMessage {
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-$method"),
            error = RpcError(
                code = -32600,
                message = "Not initialized"
            )
        )
    }

    private fun fixtureThreadList(): RpcMessage {
        consumeQuota(1)
        val threads = JsonArray(threadSummaries)
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-list"),
            result = JsonObject(
                mapOf(
                    "data" to threads,
                    "nextCursor" to JsonNull
                )
            )
        )
    }

    private fun fixtureGitStatus(): RpcMessage {
        consumeQuota(1)
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-git-status"),
            result = JsonObject(
                mapOf(
                    "status" to JsonObject(
                        mapOf(
                            "branch" to JsonPrimitive(currentBranch),
                            "isClean" to JsonPrimitive(commitCounter % 2 == 0),
                            "ahead" to JsonPrimitive(if (commitCounter % 2 == 0) 0 else 1),
                            "behind" to JsonPrimitive(0),
                            "stagedCount" to JsonPrimitive(if (commitCounter % 2 == 0) 0 else 2),
                            "unstagedCount" to JsonPrimitive(if (commitCounter % 2 == 0) 0 else 1),
                            "untrackedCount" to JsonPrimitive(if (commitCounter % 2 == 0) 0 else 1),
                            "repoRoot" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex")
                        )
                    )
                )
            )
        )
    }

    private fun fixtureGitBranches(): RpcMessage {
        consumeQuota(1)
        val branchEntries = branchNames.map { name ->
            JsonObject(
                mapOf(
                    "name" to JsonPrimitive(name),
                    "current" to JsonPrimitive(name == currentBranch)
                )
            )
        }
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-git-branches"),
            result = JsonObject(
                mapOf(
                    "branches" to JsonArray(branchEntries)
                )
            )
        )
    }

    private fun fixtureGitCheckout(params: JsonObject): RpcMessage {
        consumeQuota(2)
        val branch = (params["branch"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (branch.isEmpty()) {
            return RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-git-checkout"),
                error = RpcError(
                    code = -32602,
                    message = "Branch is required."
                )
            )
        }
        if (!branchNames.contains(branch)) {
            branchNames += branch
        }
        currentBranch = branch

        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-git-checkout-$branch"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "branch" to JsonPrimitive(branch)
                )
            )
        )
    }

    private fun fixturePushRegister(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val platform = (params["platform"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            .ifEmpty { "android" }
        val pushProvider = (params["pushProvider"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            .ifEmpty { "fcm" }
        val pushEnvironment = (params["pushEnvironment"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            .ifEmpty { "production" }

        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-notifications-push-register"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "platform" to JsonPrimitive(platform),
                    "pushProvider" to JsonPrimitive(pushProvider),
                    "pushEnvironment" to JsonPrimitive(pushEnvironment)
                )
            )
        )
    }

    private fun fixtureThreadStart(): RpcMessage {
        consumeQuota(2)
        val nextIndex = threadSummaries.size + 1 + turnsByThread.size
        val threadId = "thread-$nextIndex"
        val thread = JsonObject(
            mapOf(
                "id" to JsonPrimitive(threadId),
                "title" to JsonPrimitive("New Android thread $nextIndex"),
                "preview" to JsonPrimitive("Fresh thread started on Android"),
                "cwd" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                "updated_at" to JsonPrimitive(System.currentTimeMillis())
            )
        )
        threadSummaries.add(0, thread)
        turnsByThread[threadId] = mutableListOf()

        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-start-$threadId"),
            result = JsonObject(
                mapOf(
                    "thread" to thread
                )
            )
        )
    }

    private fun fixtureThreadRead(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull ?: "thread-alpha"
        val turns = JsonArray(turnsByThread[threadId].orEmpty())
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-read-$threadId"),
            result = JsonObject(
                mapOf(
                    "thread" to JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(threadId),
                            "turns" to turns
                        )
                    )
                )
            )
        )
    }

    private fun fixtureTurnInterrupt(params: JsonObject): RpcMessage {
        consumeQuota(2)
        val turnId = (params["turnId"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (turnId.isEmpty()) {
            return RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-turn-interrupt"),
                error = RpcError(
                    code = -32602,
                    message = "turnId is required."
                )
            )
        }
        for ((_, turns) in turnsByThread) {
            val index = turns.indexOfFirst { turn ->
                (turn["id"] as? JsonPrimitive)?.contentOrNull == turnId
            }
            if (index >= 0) {
                turns[index] = withTurnStatus(turns[index], "interrupted")
                break
            }
        }
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-turn-interrupt-$turnId"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "turnId" to JsonPrimitive(turnId)
                )
            )
        )
    }

    private fun fixtureTurnStart(params: JsonObject): RpcMessage {
        consumeQuota(4)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull ?: "thread-alpha"
        val turns = turnsByThread.getOrPut(threadId) { mutableListOf() }
        val nextTurnIndex = turns.size + 1
        val inputArray = params["input"] as? JsonArray ?: JsonArray(emptyList())
        val text = inputArray
            .firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("text")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Sent from Android composer."

        val newTurn = turnWithStatus(
            id = "turn-$nextTurnIndex",
            status = "running",
            userMessageItem(
                id = "item-u$nextTurnIndex",
                text = text
            ),
            assistantMessageItem(
                id = "item-a$nextTurnIndex",
                text = "Fixture reply: received your turn/start input."
            )
        )
        turns += newTurn

        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-turn-start-$threadId-$nextTurnIndex"),
            result = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive("turn-$nextTurnIndex")
                )
            )
        )
    }

    private fun fixtureGitPull(): RpcMessage {
        consumeQuota(2)
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-git-pull"),
            result = JsonObject(
                mapOf(
                    "success" to JsonPrimitive(true)
                )
            )
        )
    }

    private fun fixtureGitPush(): RpcMessage {
        consumeQuota(3)
        ciPollCounter = 0
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-git-push"),
            result = JsonObject(
                mapOf(
                    "branch" to JsonPrimitive(currentBranch),
                    "remote" to JsonPrimitive("origin")
                )
            )
        )
    }

    private fun fixtureGitCommit(params: JsonObject): RpcMessage {
        consumeQuota(2)
        commitCounter += 1
        val message = (params["message"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            .ifEmpty { "Changes from Android" }
        val hash = "deadbee$commitCounter"
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-git-commit-$commitCounter"),
            result = JsonObject(
                mapOf(
                    "hash" to JsonPrimitive(hash),
                    "branch" to JsonPrimitive(currentBranch),
                    "summary" to JsonPrimitive("1 file changed: $message")
                )
            )
        )
    }

    private fun fixtureRateLimitStatus(): RpcMessage {
        consumeQuota(0)
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-rate-limit"),
            result = JsonObject(
                mapOf(
                    "rateLimit" to JsonObject(
                        mapOf(
                            "limit" to JsonPrimitive(rateLimitLimit),
                            "remaining" to JsonPrimitive(rateLimitRemaining),
                            "resetEpochSeconds" to JsonPrimitive(rateLimitResetEpochSeconds)
                        )
                    )
                )
            )
        )
    }

    private fun fixtureModelList(): RpcMessage {
        consumeQuota(0)
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-model-list"),
            result = JsonObject(
                mapOf(
                    "models" to JsonArray(
                        modelIds.map { modelId ->
                            JsonObject(
                                mapOf(
                                    "id" to JsonPrimitive(modelId),
                                    "name" to JsonPrimitive(modelId)
                                )
                            )
                        }
                    ),
                    "selectedModel" to JsonPrimitive(selectedModel)
                )
            )
        )
    }

    private fun fixtureModelSelect(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val model = (params["model"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (model.isEmpty()) {
            return RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-model-select"),
                error = RpcError(
                    code = -32602,
                    message = "model is required."
                )
            )
        }
        if (!modelIds.contains(model)) {
            modelIds += model
        }
        selectedModel = model
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-model-select-${model.replace('/', '_')}"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "selectedModel" to JsonPrimitive(model)
                )
            )
        )
    }

    private fun fixturePermissionList(): RpcMessage {
        consumeQuota(0)
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-permissions-pending"),
            result = JsonObject(
                mapOf(
                    "permissions" to JsonArray(pendingPermissions.toList())
                )
            )
        )
    }

    private fun fixturePermissionGrant(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val permissionId = (params["permissionId"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (permissionId.isEmpty()) {
            return RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-permission-grant"),
                error = RpcError(
                    code = -32602,
                    message = "permissionId is required."
                )
            )
        }

        pendingPermissions.removeAll { permission ->
            (permission["id"] as? JsonPrimitive)?.contentOrNull == permissionId
        }

        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-permission-grant-$permissionId"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "permissionId" to JsonPrimitive(permissionId)
                )
            )
        )
    }

    private fun fixtureCiStatus(): RpcMessage {
        consumeQuota(0)
        ciPollCounter += 1
        val state = when {
            ciPollCounter % 4 == 0 -> "passed"
            else -> "running"
        }
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-ci-status-$ciPollCounter"),
            result = JsonObject(
                mapOf(
                    "status" to JsonPrimitive(state),
                    "workflow" to JsonPrimitive("android-parity-ci"),
                    "branch" to JsonPrimitive(currentBranch)
                )
            )
        )
    }

    private fun consumeQuota(amount: Int) {
        if (amount <= 0) {
            return
        }
        rateLimitRemaining = (rateLimitRemaining - amount).coerceAtLeast(0)
        if (rateLimitRemaining == 0) {
            rateLimitResetEpochSeconds = (System.currentTimeMillis() / 1_000L) + 900L
        }
    }

    private fun turn(id: String, vararg items: JsonObject): JsonObject {
        return turnWithStatus(id = id, status = "completed", *items)
    }

    private fun turnWithStatus(id: String, status: String, vararg items: JsonObject): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "status" to JsonPrimitive(status),
                "items" to JsonArray(items.toList())
            )
        )
    }

    private fun withTurnStatus(turn: JsonObject, status: String): JsonObject {
        val mutable = turn.toMutableMap()
        mutable["status"] = JsonPrimitive(status)
        return JsonObject(mutable)
    }

    private fun userMessageItem(id: String, text: String): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "type" to JsonPrimitive("user_message"),
                "content" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("text"),
                                "text" to JsonPrimitive(text)
                            )
                        )
                    )
                )
            )
        )
    }

    private fun assistantMessageItem(id: String, text: String): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "type" to JsonPrimitive("assistant_message"),
                "content" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("output_text"),
                                "text" to JsonPrimitive(text)
                            )
                        )
                    )
                )
            )
        )
    }

    private fun systemReasoningItem(id: String, text: String): JsonObject {
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "type" to JsonPrimitive("reasoning"),
                "text" to JsonPrimitive(text)
            )
        )
    }
}
