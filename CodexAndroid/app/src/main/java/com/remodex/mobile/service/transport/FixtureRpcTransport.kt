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
    private var serverMessageListener: ((RpcMessage) -> Unit)? = null
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
    private val archivedThreadIds = mutableSetOf<String>()

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

    override fun setServerMessageListener(listener: ((RpcMessage) -> Unit)?) {
        serverMessageListener = listener
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
            "thread/list" -> fixtureThreadList(params)
            "thread/read" -> fixtureThreadRead(params)
            "thread/start" -> fixtureThreadStart(params)
            "thread/resume" -> fixtureThreadResume(params)
            "thread/fork" -> fixtureThreadFork(params)
            "thread/name/set" -> fixtureThreadNameSet(params)
            "thread/archive" -> fixtureThreadArchive(params)
            "thread/unarchive" -> fixtureThreadUnarchive(params)
            "turn/start" -> fixtureTurnStart(params)
            "turn/steer" -> fixtureTurnSteer(params)
            "turn/interrupt" -> fixtureTurnInterrupt(params)
            "review/start" -> fixtureReviewStart(params)
            "notifications/push/register" -> fixturePushRegister(params)
            "git/status" -> fixtureGitStatus()
            "git/branches" -> fixtureGitBranches()
            "git/checkout" -> fixtureGitCheckout(params)
            "git/pull" -> fixtureGitPull()
            "git/push" -> fixtureGitPush()
            "git/commit" -> fixtureGitCommit(params)
            "fuzzyFileSearch" -> fixtureFuzzyFileSearch(params)
            "fuzzy_file_search" -> fixtureFuzzyFileSearch(params)
            "skills/list" -> fixtureSkillsList(params)
            "skill/list" -> fixtureSkillsList(params)
            "skills.list" -> fixtureSkillsList(params)
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

    private fun fixtureThreadList(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val archivedOnly = (params["archived"] as? JsonPrimitive)?.contentOrNull?.trim() == "true"
        val filtered = threadSummaries.filter { thread ->
            val id = (thread["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val isArchived = archivedThreadIds.contains(id)
            if (archivedOnly) isArchived else !isArchived
        }
        val threads = JsonArray(filtered)
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

    private fun fixtureThreadStart(params: JsonObject): RpcMessage {
        consumeQuota(2)
        val nextIndex = threadSummaries.size + 1 + turnsByThread.size
        val threadId = "thread-$nextIndex"
        val preferredCwd = (params["cwd"] as? JsonPrimitive)?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val thread = JsonObject(
            mapOf(
                "id" to JsonPrimitive(threadId),
                "title" to JsonPrimitive("New Android thread $nextIndex"),
                "preview" to JsonPrimitive("Fresh thread started on Android"),
                "cwd" to JsonPrimitive(preferredCwd ?: "/Users/yyy/Documents/protein_design/remodex"),
                "updated_at" to JsonPrimitive(System.currentTimeMillis())
            )
        )
        threadSummaries.add(0, thread)
        turnsByThread[threadId] = mutableListOf()
        archivedThreadIds.remove(threadId)
        emitServerNotification(
            method = "thread/started",
            params = JsonObject(
                mapOf(
                    "thread" to thread
                )
            )
        )

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

    private fun fixtureThreadResume(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: (threadSummaries.firstOrNull()?.get("id") as? JsonPrimitive)?.contentOrNull
            ?: "thread-alpha"
        val summary = threadSummaries.firstOrNull { summary ->
            (summary["id"] as? JsonPrimitive)?.contentOrNull == threadId
        }
        val resultThread = summary ?: JsonObject(
            mapOf(
                "id" to JsonPrimitive(threadId),
                "title" to JsonPrimitive("Resumed $threadId"),
                "updated_at" to JsonPrimitive(System.currentTimeMillis())
            )
        )
        emitServerNotification(
            method = "thread/status/changed",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "status" to JsonPrimitive("resumed")
                )
            )
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-resume-$threadId"),
            result = JsonObject(
                mapOf(
                    "thread" to resultThread,
                    "threadId" to JsonPrimitive(threadId)
                )
            )
        )
    }

    private fun fixtureThreadFork(params: JsonObject): RpcMessage {
        consumeQuota(2)
        val sourceThreadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "thread-alpha"
        val sourceSummary = threadSummaries.firstOrNull { summary ->
            (summary["id"] as? JsonPrimitive)?.contentOrNull == sourceThreadId
        }
        val nextIndex = threadSummaries.size + 1 + turnsByThread.size
        val threadId = "thread-fork-$nextIndex"
        val title = sourceSummary
            ?.let { (it["title"] as? JsonPrimitive)?.contentOrNull ?: (it["name"] as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotBlank() }
            ?.let { "Fork of $it" }
            ?: "Forked thread $nextIndex"
        val cwd = sourceSummary
            ?.let { (it["cwd"] as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotBlank() }
            ?: "/Users/yyy/Documents/protein_design/remodex"
        val thread = JsonObject(
            mapOf(
                "id" to JsonPrimitive(threadId),
                "title" to JsonPrimitive(title),
                "preview" to JsonPrimitive("Forked from $sourceThreadId"),
                "cwd" to JsonPrimitive(cwd),
                "updated_at" to JsonPrimitive(System.currentTimeMillis())
            )
        )
        threadSummaries.add(0, thread)
        turnsByThread[threadId] = mutableListOf()
        emitServerNotification(
            method = "thread/started",
            params = JsonObject(mapOf("thread" to thread))
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-fork-$threadId"),
            result = JsonObject(
                mapOf(
                    "thread" to thread,
                    "sourceThreadId" to JsonPrimitive(sourceThreadId)
                )
            )
        )
    }

    private fun fixtureFuzzyFileSearch(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val query = (params["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().lowercase()
        val roots = (params["roots"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            ?: emptyList()
        val root = roots.firstOrNull() ?: "/Users/yyy/Documents/protein_design/remodex"
        val candidates = listOf(
            "$root/README.md",
            "$root/AGENTS.md",
            "$root/CodexAndroid/app/src/main/java/com/remodex/mobile/service/CodexService.kt",
            "$root/CodexAndroid/app/src/main/java/com/remodex/mobile/ui/parity/WorkspaceScreen.kt",
            "$root/phodex-bridge/src/git-handler.js"
        )
        val filtered = if (query.isEmpty()) {
            candidates
        } else {
            candidates.filter { it.lowercase().contains(query) }
        }
        val files = filtered.map { path ->
            JsonObject(
                mapOf(
                    "path" to JsonPrimitive(path),
                    "fileName" to JsonPrimitive(path.substringAfterLast('/'))
                )
            )
        }
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-fuzzy-file-search"),
            result = JsonObject(
                mapOf(
                    "files" to JsonArray(files)
                )
            )
        )
    }

    private fun fixtureSkillsList(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val forceReload = (params["forceReload"] as? JsonPrimitive)?.contentOrNull == "true"
        val skills = listOf(
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("openai-docs"),
                    "name" to JsonPrimitive("openai-docs"),
                    "description" to JsonPrimitive("Look up official OpenAI docs."),
                    "path" to JsonPrimitive("/Users/yyy/.codex/skills/.system/openai-docs/SKILL.md"),
                    "enabled" to JsonPrimitive(true)
                )
            ),
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("figma"),
                    "name" to JsonPrimitive("figma"),
                    "description" to JsonPrimitive("Use Figma MCP workflows."),
                    "path" to JsonPrimitive("/Users/yyy/.codex/skills/figma/SKILL.md"),
                    "enabled" to JsonPrimitive(true)
                )
            ),
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("security-best-practices"),
                    "name" to JsonPrimitive("security-best-practices"),
                    "description" to JsonPrimitive("Security review helper."),
                    "path" to JsonPrimitive("/Users/yyy/.codex/skills/security-best-practices/SKILL.md"),
                    "enabled" to JsonPrimitive(!forceReload)
                )
            )
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-skills-list"),
            result = JsonObject(
                mapOf(
                    "data" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "skills" to JsonArray(skills)
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun fixtureThreadRead(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull ?: "thread-alpha"
        val turns = JsonArray(turnsByThread[threadId].orEmpty())
        val summary = threadSummaries.firstOrNull { summary ->
            (summary["id"] as? JsonPrimitive)?.contentOrNull == threadId
        }
        val cwd = summary
            ?.let { (it["cwd"] as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotBlank() }
            ?: summary
                ?.let { (it["current_working_directory"] as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotBlank() }
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-read-$threadId"),
            result = JsonObject(
                mapOf(
                    "thread" to JsonObject(
                        buildMap {
                            put("id", JsonPrimitive(threadId))
                            put("turns", turns)
                            if (!cwd.isNullOrBlank()) {
                                put("cwd", JsonPrimitive(cwd))
                            }
                        }
                    )
                )
            )
        )
    }

    private fun fixtureThreadNameSet(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (threadId.isEmpty() || name.isEmpty()) {
            return RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-thread-name-set"),
                error = RpcError(code = -32602, message = "threadId and name are required.")
            )
        }
        val index = threadSummaries.indexOfFirst { summary ->
            (summary["id"] as? JsonPrimitive)?.contentOrNull == threadId
        }
        if (index >= 0) {
            val mutable = threadSummaries[index].toMutableMap()
            mutable["name"] = JsonPrimitive(name)
            mutable["title"] = JsonPrimitive(name)
            mutable["updated_at"] = JsonPrimitive(System.currentTimeMillis())
            threadSummaries[index] = JsonObject(mutable)
        }
        emitServerNotification(
            method = "thread/name/updated",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "name" to JsonPrimitive(name)
                )
            )
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-name-set-$threadId"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "threadId" to JsonPrimitive(threadId),
                    "name" to JsonPrimitive(name)
                )
            )
        )
    }

    private fun fixtureThreadArchive(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val unarchive = (params["unarchive"] as? JsonPrimitive)?.contentOrNull?.trim() == "true"
        if (threadId.isEmpty()) {
            return RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("fixture-thread-archive"),
                error = RpcError(code = -32602, message = "threadId is required.")
            )
        }
        if (unarchive) {
            archivedThreadIds.remove(threadId)
        } else {
            archivedThreadIds.add(threadId)
        }
        val index = threadSummaries.indexOfFirst { summary ->
            (summary["id"] as? JsonPrimitive)?.contentOrNull == threadId
        }
        if (index >= 0) {
            val mutable = threadSummaries[index].toMutableMap()
            mutable["updated_at"] = JsonPrimitive(System.currentTimeMillis())
            threadSummaries[index] = JsonObject(mutable)
        }
        emitServerNotification(
            method = "thread/status/changed",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "status" to JsonPrimitive(if (unarchive) "unarchived" else "archived"),
                    "archived" to JsonPrimitive(!unarchive)
                )
            )
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-thread-archive-$threadId"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "threadId" to JsonPrimitive(threadId),
                    "archived" to JsonPrimitive(!unarchive)
                )
            )
        )
    }

    private fun fixtureThreadUnarchive(params: JsonObject): RpcMessage {
        val adjusted = JsonObject(
            params.toMutableMap().also { it["unarchive"] = JsonPrimitive(true) }
        )
        return fixtureThreadArchive(adjusted)
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
                emitServerNotification(
                    method = "turn/failed",
                    params = JsonObject(
                        mapOf(
                            "turnId" to JsonPrimitive(turnId),
                            "status" to JsonPrimitive("interrupted"),
                            "message" to JsonPrimitive("Turn interrupted in fixture runtime.")
                        )
                    )
                )
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
        val turnId = "turn-$nextTurnIndex"
        emitServerNotification(
            method = "turn/started",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId)
                )
            )
        )
        emitServerNotification(
            method = "codex/event/user_message",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId),
                    "itemId" to JsonPrimitive("item-u$nextTurnIndex"),
                    "text" to JsonPrimitive(text)
                )
            )
        )
        emitServerNotification(
            method = "item/agentMessage/delta",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId),
                    "itemId" to JsonPrimitive("item-a$nextTurnIndex"),
                    "text" to JsonPrimitive("Fixture reply: received your turn/start input.")
                )
            )
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-turn-start-$threadId-$nextTurnIndex"),
            result = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId)
                )
            )
        )
    }

    private fun fixtureTurnSteer(params: JsonObject): RpcMessage {
        consumeQuota(2)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull
            ?: (threadSummaries.firstOrNull()?.get("id") as? JsonPrimitive)?.contentOrNull
            ?: "thread-alpha"
        val turnId = (params["turnId"] as? JsonPrimitive)?.contentOrNull
            ?: (turnsByThread[threadId]?.lastOrNull()?.get("id") as? JsonPrimitive)?.contentOrNull
            ?: "turn-1"
        val input = (params["input"] as? JsonPrimitive)?.contentOrNull
            ?: (params["text"] as? JsonPrimitive)?.contentOrNull
            ?: "Steer current turn."
        emitServerNotification(
            method = "codex/event/user_message",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId),
                    "itemId" to JsonPrimitive("item-steer-${System.currentTimeMillis()}"),
                    "text" to JsonPrimitive(input)
                )
            )
        )
        emitServerNotification(
            method = "item/agentMessage/delta",
            params = JsonObject(
                mapOf(
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId),
                    "itemId" to JsonPrimitive("item-a-steer-${System.currentTimeMillis()}"),
                    "text" to JsonPrimitive("Fixture steer applied.")
                )
            )
        )
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-turn-steer-$turnId"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "threadId" to JsonPrimitive(threadId),
                    "turnId" to JsonPrimitive(turnId)
                )
            )
        )
    }

    private fun fixtureReviewStart(params: JsonObject): RpcMessage {
        consumeQuota(1)
        val threadId = (params["threadId"] as? JsonPrimitive)?.contentOrNull
            ?: "thread-alpha"
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("fixture-review-start-$threadId"),
            result = JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "threadId" to JsonPrimitive(threadId),
                    "reviewId" to JsonPrimitive("review-${System.currentTimeMillis()}")
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

    private fun emitServerNotification(method: String, params: JsonObject) {
        serverMessageListener?.invoke(
            RpcMessage(
                jsonrpc = "2.0",
                method = method,
                params = params
            )
        )
    }
}
