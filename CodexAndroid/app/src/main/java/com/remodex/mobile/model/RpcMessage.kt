package com.remodex.mobile.model

import kotlinx.serialization.Serializable

@Serializable
data class RpcMessage(
    val jsonrpc: String? = null,
    val id: JsonValue? = null,
    val method: String? = null,
    val params: JsonValue? = null,
    val result: JsonValue? = null,
    val error: RpcError? = null
)

@Serializable
data class RpcError(
    val code: Int,
    val message: String,
    val data: JsonValue? = null
)
