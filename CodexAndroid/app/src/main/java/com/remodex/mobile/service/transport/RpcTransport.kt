package com.remodex.mobile.service.transport

import com.remodex.mobile.model.RpcMessage
import kotlinx.serialization.json.JsonObject

interface RpcTransport {
    suspend fun open()
    suspend fun close()
    suspend fun request(method: String, params: JsonObject = JsonObject(emptyMap())): RpcMessage
}
