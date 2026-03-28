package com.remodex.mobile.model

enum class TimelineRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class TimelineEntry(
    val id: String,
    val threadId: String,
    val turnId: String?,
    val type: String,
    val role: TimelineRole,
    val text: String
)
