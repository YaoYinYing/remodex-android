package com.remodex.mobile.service

import com.remodex.mobile.model.RpcError
import com.remodex.mobile.model.RpcMessage
import com.remodex.mobile.model.TimelineRole
import com.remodex.mobile.service.transport.ScriptedRpcTransport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexServiceScriptedTransportTest {
    @Test
    fun scriptedTransportCanDriveConnectAndTurnLifecycle() = runBlocking {
        val scripted = ScriptedRpcTransport()
        scripted.enqueue("initialize", rpcResult("init", JsonObject(emptyMap())))
        scripted.enqueue("thread/list", rpcThreadList(live = true))
        scripted.enqueue("thread/list", rpcThreadList(live = false))
        scripted.enqueue("git/status", rpcGitStatus())
        scripted.enqueue("git/branches", rpcGitBranches())
        scripted.enqueue(
            method = "turn/start",
            response = rpcResult(
                id = "turn-start",
                result = JsonObject(
                    mapOf(
                        "turnId" to JsonPrimitive("turn-scripted-1")
                    )
                )
            ),
            serverMessages = listOf(
                RpcMessage(
                    jsonrpc = "2.0",
                    method = "turn/started",
                    params = JsonObject(
                        mapOf(
                            "threadId" to JsonPrimitive("thread-scripted"),
                            "turnId" to JsonPrimitive("turn-scripted-1")
                        )
                    )
                )
            )
        )
        scripted.enqueue("thread/read", rpcThreadRead())

        val service = CodexService(fixtureTransportFactory = { scripted })
        rememberTestPairing(service)
        service.connectWithFixture()
        service.sendTurnStart("Mock Codex response for instrumentation and CI.")

        assertEquals(ConnectionState.Connected, service.connectionState.value)
        assertTrue(service.timeline.value.any { it.role == TimelineRole.ASSISTANT })
        assertTrue(service.timeline.value.any { it.text.contains("Mocked assistant reply") })
        assertTrue(service.status.value.contains("Turn started"))
    }

    @Test
    fun scriptedTransportKeepsRpcErrorMappingStable() = runBlocking {
        val scripted = ScriptedRpcTransport()
        scripted.enqueue("initialize", rpcResult("init", JsonObject(emptyMap())))
        scripted.enqueue("thread/list", rpcThreadList(live = true))
        scripted.enqueue("thread/list", rpcThreadList(live = false))
        scripted.enqueue("git/status", rpcGitStatus())
        scripted.enqueue(
            "git/status",
            RpcMessage(
                jsonrpc = "2.0",
                id = JsonPrimitive("git-status"),
                error = RpcError(
                    code = -32000,
                    message = "Git index is locked."
                )
            )
        )

        val service = CodexService(fixtureTransportFactory = { scripted })
        rememberTestPairing(service)
        service.connectWithFixture()

        val failure = runCatching { service.refreshGitStatus() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("git/status failed (-32000)"))
    }

    private fun rememberTestPairing(service: CodexService) {
        service.rememberPairing(
            PairingPayload(
                sessionId = "session-scripted",
                relayUrl = "ws://127.0.0.1:9000/relay",
                macDeviceId = "mac-scripted",
                macIdentityPublicKey = "bWFjLWlkZW50aXR5LXB1YmxpYy1rZXk=",
                expiresAt = System.currentTimeMillis() + 300_000L
            )
        )
    }

    private fun rpcResult(id: String, result: JsonObject): RpcMessage {
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive(id),
            result = result
        )
    }

    private fun rpcThreadList(live: Boolean): RpcMessage {
        val data = if (live) {
            JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("thread-scripted"),
                            "title" to JsonPrimitive("Scripted thread"),
                            "preview" to JsonPrimitive("Scripted preview"),
                            "cwd" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                            "updated_at" to JsonPrimitive(1_710_000_444_000L)
                        )
                    )
                )
            )
        } else {
            JsonArray(emptyList())
        }
        return rpcResult(
            id = if (live) "thread-list-live" else "thread-list-archived",
            result = JsonObject(
                mapOf(
                    "data" to data
                )
            )
        )
    }

    private fun rpcThreadRead(): RpcMessage {
        return rpcResult(
            id = "thread-read",
            result = JsonObject(
                mapOf(
                    "thread" to JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("thread-scripted"),
                            "cwd" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex"),
                            "turns" to JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "id" to JsonPrimitive("turn-scripted-1"),
                                            "status" to JsonPrimitive("completed"),
                                            "items" to JsonArray(
                                                listOf(
                                                    JsonObject(
                                                        mapOf(
                                                            "id" to JsonPrimitive("item-user-1"),
                                                            "type" to JsonPrimitive("user_message"),
                                                            "content" to JsonArray(
                                                                listOf(
                                                                    JsonObject(
                                                                        mapOf(
                                                                            "type" to JsonPrimitive("text"),
                                                                            "text" to JsonPrimitive("Mock Codex response for instrumentation and CI.")
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    JsonObject(
                                                        mapOf(
                                                            "id" to JsonPrimitive("item-assistant-1"),
                                                            "type" to JsonPrimitive("agent_message"),
                                                            "content" to JsonArray(
                                                                listOf(
                                                                    JsonObject(
                                                                        mapOf(
                                                                            "type" to JsonPrimitive("text"),
                                                                            "text" to JsonPrimitive("Mocked assistant reply from scripted transport.")
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    private fun rpcGitStatus(): RpcMessage {
        return rpcResult(
            id = "git-status",
            result = JsonObject(
                mapOf(
                    "status" to JsonObject(
                        mapOf(
                            "branch" to JsonPrimitive("android"),
                            "ahead" to JsonPrimitive(0),
                            "behind" to JsonPrimitive(0),
                            "stagedCount" to JsonPrimitive(0),
                            "unstagedCount" to JsonPrimitive(0),
                            "untrackedCount" to JsonPrimitive(0),
                            "repoRoot" to JsonPrimitive("/Users/yyy/Documents/protein_design/remodex")
                        )
                    )
                )
            )
        )
    }

    private fun rpcGitBranches(): RpcMessage {
        return rpcResult(
            id = "git-branches",
            result = JsonObject(
                mapOf(
                    "branches" to JsonArray(
                        listOf(
                            JsonPrimitive("android"),
                            JsonPrimitive("main")
                        )
                    )
                )
            )
        )
    }
}
