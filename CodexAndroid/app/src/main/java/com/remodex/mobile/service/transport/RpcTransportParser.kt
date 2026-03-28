package com.remodex.mobile.service.transport

import com.remodex.mobile.model.ThreadSummary
import com.remodex.mobile.model.TimelineEntry
import com.remodex.mobile.model.TimelineRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class RpcTransportParser {
    fun parseThreadList(result: JsonObject, forceArchived: Boolean? = null): List<ThreadSummary> {
        val page = (result["data"] as? JsonArray)
            ?: (result["items"] as? JsonArray)
            ?: (result["threads"] as? JsonArray)
            ?: JsonArray(emptyList())

        return page.mapNotNull { element ->
            val threadObject = element as? JsonObject ?: return@mapNotNull null
            parseThreadSummary(threadObject, forceArchived = forceArchived)
        }
    }

    fun parseThreadTimeline(result: JsonObject): List<TimelineEntry> {
        val threadObject = result["thread"] as? JsonObject ?: return emptyList()
        val threadId = threadObject.string("id").orEmpty()
        if (threadId.isEmpty()) {
            return emptyList()
        }

        val turns = threadObject["turns"] as? JsonArray ?: return emptyList()
        val output = mutableListOf<TimelineEntry>()

        for ((turnIndex, turnElement) in turns.withIndex()) {
            val turnObject = turnElement as? JsonObject ?: continue
            val turnId = turnObject.string("id")
            val items = turnObject["items"] as? JsonArray ?: continue

            for ((itemIndex, itemElement) in items.withIndex()) {
                val itemObject = itemElement as? JsonObject ?: continue
                val normalizedType = normalizeItemType(itemObject.string("type"))
                if (normalizedType.isEmpty()) {
                    continue
                }

                val text = parseItemText(itemObject)
                if (text.isBlank()) {
                    continue
                }

                val role = when (normalizedType) {
                    "usermessage" -> TimelineRole.USER
                    "agentmessage", "assistantmessage" -> TimelineRole.ASSISTANT
                    "message" -> {
                        val messageRole = itemObject.string("role").orEmpty().lowercase()
                        if (messageRole.contains("user")) TimelineRole.USER else TimelineRole.ASSISTANT
                    }
                    else -> TimelineRole.SYSTEM
                }

                val fallbackId = "item-${turnIndex + 1}-${itemIndex + 1}"
                val itemId = itemObject.string("id") ?: fallbackId
                output += TimelineEntry(
                    id = itemId,
                    threadId = threadId,
                    turnId = turnId,
                    type = normalizedType,
                    role = role,
                    text = text
                )
            }
        }

        return output
    }

    fun parseThreadSummaryObject(threadObject: JsonObject, forceArchived: Boolean? = null): ThreadSummary? {
        return parseThreadSummary(threadObject, forceArchived = forceArchived)
    }

    fun parseTimelineEntry(
        threadId: String,
        turnId: String?,
        itemObject: JsonObject,
        fallbackId: String = "notification-item"
    ): TimelineEntry? {
        val normalizedType = normalizeItemType(itemObject.string("type"))
        if (normalizedType.isEmpty()) {
            return null
        }

        val text = parseItemText(itemObject)
        if (text.isBlank()) {
            return null
        }

        val role = when (normalizedType) {
            "usermessage" -> TimelineRole.USER
            "agentmessage", "assistantmessage" -> TimelineRole.ASSISTANT
            "message" -> {
                val messageRole = itemObject.string("role").orEmpty().lowercase()
                if (messageRole.contains("user")) TimelineRole.USER else TimelineRole.ASSISTANT
            }
            else -> TimelineRole.SYSTEM
        }

        return TimelineEntry(
            id = itemObject.string("id") ?: fallbackId,
            threadId = threadId,
            turnId = turnId,
            type = normalizedType,
            role = role,
            text = text
        )
    }

    private fun parseThreadSummary(threadObject: JsonObject, forceArchived: Boolean? = null): ThreadSummary? {
        val id = threadObject.string("id") ?: return null
        val metadata = threadObject["metadata"] as? JsonObject
        val archivedState = forceArchived
            ?: threadObject.bool("archived", "isArchived", "is_archived")
            ?: when (threadObject.string("syncState", "sync_state")?.lowercase()) {
                "archived", "archived_local" -> true
                else -> false
            }
        return ThreadSummary(
            id = id,
            title = threadObject.string("title"),
            name = threadObject.string("name"),
            preview = threadObject.string("preview"),
            cwd = threadObject.string("cwd", "current_working_directory", "working_directory"),
            updatedAtMillis = threadObject.timestampMillis("updatedAt", "updated_at"),
            isArchived = archivedState,
            parentThreadId = normalizedIdentifier(
                threadObject.string("parentThreadId", "parent_thread_id")
                    ?: metadata?.string("parentThreadId", "parent_thread_id")
            ),
            forkedFromThreadId = normalizedIdentifier(
                threadObject.string("forkedFromThreadId", "forked_from_thread_id", "forkedFromId", "forked_from_id")
                    ?: metadata?.string("forkedFromThreadId", "forked_from_thread_id", "forkedFromId", "forked_from_id")
            ),
            agentId = normalizedIdentifier(
                threadObject.string("agentId", "agent_id")
                    ?: metadata?.string("agentId", "agent_id")
            ),
            agentNickname = normalizedIdentifier(
                threadObject.string("agentNickname", "agent_nickname")
                    ?: metadata?.string("agentNickname", "agent_nickname", "nickname")
            ),
            agentRole = normalizedIdentifier(
                threadObject.string("agentRole", "agent_role")
                    ?: metadata?.string("agentRole", "agent_role", "agentType", "agent_type")
            ),
            model = normalizedIdentifier(
                threadObject.string("model", "modelName", "model_name")
                    ?: metadata?.string("model", "modelName", "model_name")
            ),
            modelProvider = normalizedIdentifier(
                threadObject.string("modelProvider", "model_provider")
                    ?: metadata?.string("modelProvider", "model_provider", "modelProviderId", "model_provider_id")
            )
        )
    }

    private fun parseItemText(itemObject: JsonObject): String {
        val contentItems = itemObject["content"] as? JsonArray ?: JsonArray(emptyList())
        val fromContent = contentItems.mapNotNull { part ->
            val partObject = part as? JsonObject ?: return@mapNotNull null
            val normalizedType = normalizeItemType(partObject.string("type"))
            val text = when (normalizedType) {
                "text", "inputtext", "outputtext", "message" -> partObject.string("text")
                else -> null
            }
            text?.trim()?.takeIf { it.isNotEmpty() }
        }.joinToString("\n")

        if (fromContent.isNotBlank()) {
            return fromContent
        }

        val direct = itemObject.string("text", "message")?.trim().orEmpty()
        if (direct.isNotEmpty()) {
            return direct
        }

        val enteredReview = normalizeItemType(itemObject.string("type"))
        if (enteredReview == "enteredreviewmode") {
            return "Reviewing changes..."
        }

        if (enteredReview == "contextcompaction") {
            return "Context compacted"
        }

        if (enteredReview == "plan") {
            return itemObject.string("title", "summary", "status") ?: "Plan updated."
        }

        if (enteredReview == "reasoning") {
            return itemObject.string("summary", "title") ?: "Thinking..."
        }

        if (enteredReview == "toolcall") {
            val tool = itemObject.string("tool", "name", "command")
            return if (tool.isNullOrBlank()) "Tool call updated." else "Tool call: $tool"
        }

        if (enteredReview == "filechange") {
            val path = itemObject.string("path", "file", "relativePath", "relative_path")
            return if (path.isNullOrBlank()) "File changes updated." else "File change: $path"
        }

        if (enteredReview == "commandexecution") {
            val command = itemObject.string("command", "title", "summary")
            return if (command.isNullOrBlank()) "Command execution updated." else "Command: $command"
        }

        if (enteredReview == "diff") {
            return "Diff updated."
        }

        return ""
    }

    private fun normalizeItemType(value: String?): String {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            .orEmpty()
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

    private fun normalizedIdentifier(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.timestampMillis(vararg keys: String): Long? {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            val numericValue = primitive.longOrNull
                ?: primitive.doubleOrNull?.toLong()
                ?: primitive.contentOrNull?.toDoubleOrNull()?.toLong()
                ?: continue
            return if (numericValue > 10_000_000_000L) numericValue else numericValue * 1_000L
        }
        return null
    }

    private fun JsonObject.bool(vararg keys: String): Boolean? {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            primitive.contentOrNull?.trim()?.lowercase()?.let { value ->
                when (value) {
                    "true", "1", "yes", "y" -> return true
                    "false", "0", "no", "n" -> return false
                    else -> Unit
                }
            }
        }
        return null
    }
}
