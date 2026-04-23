package com.remodex.mobile.model

import java.util.concurrent.atomic.AtomicLong

enum class TimelineRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class TimelineEntryKind {
    CHAT,
    THINKING,
    TOOL_ACTIVITY,
    FILE_CHANGE,
    COMMAND_EXECUTION,
    SUBAGENT_ACTION,
    PLAN,
    USER_INPUT_PROMPT
}

enum class TimelineDeliveryState {
    PENDING,
    CONFIRMED,
    FAILED
}

data class TimelinePlanStep(
    val id: String,
    val step: String,
    val status: String
)

data class TimelinePlanState(
    val explanation: String?,
    val steps: List<TimelinePlanStep>
)

data class TimelineCommandExecutionDetails(
    val command: String,
    val cwd: String?,
    val status: String?,
    val output: String?,
    val exitCode: Int?,
    val durationMs: Int?
)

data class TimelineStructuredInputOption(
    val id: String,
    val label: String,
    val description: String
)

data class TimelineStructuredInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val isOther: Boolean,
    val isSecret: Boolean,
    val options: List<TimelineStructuredInputOption>
)

data class TimelineStructuredUserInputRequest(
    val requestId: String,
    val questions: List<TimelineStructuredInputQuestion>
)

data class TimelineSubagentThreadPresentation(
    val threadId: String,
    val agentId: String?,
    val nickname: String?,
    val role: String?,
    val model: String?,
    val fallbackStatus: String?,
    val fallbackMessage: String?
)

data class TimelineSubagentAction(
    val tool: String,
    val status: String,
    val prompt: String?,
    val model: String?,
    val agents: List<TimelineSubagentThreadPresentation>
)

fun TimelineSubagentAction.summaryText(): String {
    val normalizedTool = tool.trim().lowercase().replace("_", "").replace("-", "")
    val count = agents.size.coerceAtLeast(1)
    val noun = if (count == 1) "agent" else "agents"
    return when (normalizedTool) {
        "spawnagent" -> "Spawning $count $noun"
        "waitagent", "wait" -> "Waiting on $count $noun"
        "resumeagent" -> "Resuming $count $noun"
        "closeagent" -> "Closing $count $noun"
        "sendinput" -> "Updating $count $noun"
        else -> if (prompt.isNullOrBlank()) "Agent activity ($count)" else prompt
    }
}

data class TimelineEntry(
    val id: String,
    val threadId: String,
    val turnId: String?,
    val type: String,
    val role: TimelineRole,
    val text: String,
    val kind: TimelineEntryKind = TimelineEntryKind.CHAT,
    val deliveryState: TimelineDeliveryState = TimelineDeliveryState.CONFIRMED,
    val orderIndex: Long = nextTimelineOrderIndex(),
    val commandExecution: TimelineCommandExecutionDetails? = null,
    val planState: TimelinePlanState? = null,
    val subagentAction: TimelineSubagentAction? = null,
    val structuredUserInputRequest: TimelineStructuredUserInputRequest? = null
)

private val timelineOrderCounter = AtomicLong(1L)

private fun nextTimelineOrderIndex(): Long = timelineOrderCounter.getAndIncrement()
