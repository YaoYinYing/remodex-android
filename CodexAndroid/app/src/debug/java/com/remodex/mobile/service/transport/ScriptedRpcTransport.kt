package com.remodex.mobile.service.transport

import com.remodex.mobile.model.RpcError
import com.remodex.mobile.model.RpcMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ScriptedTransportStep(
    val response: RpcMessage,
    val serverMessages: List<RpcMessage> = emptyList()
)

/**
 * Debug/test transport that replays deterministic RPC responses and optional inbound server events.
 */
class ScriptedRpcTransport(
    private val fallback: ((method: String, params: JsonObject) -> RpcMessage)? = null
) : RpcTransport {
    private val lock = Any()
    private val scriptedResponses = mutableMapOf<String, ArrayDeque<ScriptedTransportStep>>()
    private var listener: ((RpcMessage) -> Unit)? = null

    fun enqueue(
        method: String,
        response: RpcMessage,
        serverMessages: List<RpcMessage> = emptyList()
    ) {
        synchronized(lock) {
            val queue = scriptedResponses.getOrPut(method) { ArrayDeque() }
            queue.addLast(ScriptedTransportStep(response = response, serverMessages = serverMessages))
        }
    }

    override fun setServerMessageListener(listener: ((RpcMessage) -> Unit)?) {
        this.listener = listener
    }

    override suspend fun open() = Unit

    override suspend fun close() = Unit

    override suspend fun request(method: String, params: JsonObject): RpcMessage {
        val step = synchronized(lock) {
            val queue = scriptedResponses[method]
            if (queue != null && queue.isNotEmpty()) queue.removeFirst() else null
        }
        if (step != null) {
            step.serverMessages.forEach { message ->
                listener?.invoke(message)
            }
            return step.response
        }
        val fallbackResponse = fallback?.invoke(method, params)
        if (fallbackResponse != null) {
            return fallbackResponse
        }
        return RpcMessage(
            jsonrpc = "2.0",
            id = JsonPrimitive("scripted-$method"),
            error = RpcError(
                code = -32601,
                message = "No scripted response for method '$method'."
            )
        )
    }

    override suspend fun notify(method: String, params: JsonObject) = Unit
}
