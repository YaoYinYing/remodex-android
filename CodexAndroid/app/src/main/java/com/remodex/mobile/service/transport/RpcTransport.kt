package com.remodex.mobile.service.transport

import com.remodex.mobile.model.RpcMessage
import kotlinx.serialization.json.JsonObject

interface RpcTransport {
    fun setServerMessageListener(listener: ((RpcMessage) -> Unit)?)
    suspend fun open()
    suspend fun close()
    suspend fun request(method: String, params: JsonObject = JsonObject(emptyMap())): RpcMessage
    suspend fun notify(method: String, params: JsonObject = JsonObject(emptyMap()))
}
