package com.remodex.mobile.ui.parity

import com.remodex.mobile.model.ThreadSummary

enum class ThreadProjectGroupKind {
    PROJECT,
    ARCHIVED
}

data class ThreadProjectGroup(
    val id: String,
    val label: String,
    val kind: ThreadProjectGroupKind,
    val projectPath: String?,
    val threads: List<ThreadSummary>,
    val latestUpdatedAtMillis: Long
)

fun groupThreadsByProject(
    threads: List<ThreadSummary>,
    query: String
): List<ThreadProjectGroup> {
    val normalizedQuery = query.trim().lowercase()
    val filteredThreads = if (normalizedQuery.isEmpty()) {
        threads
    } else {
        threads.filter { thread ->
            thread.displayTitle.lowercase().contains(normalizedQuery)
                || thread.preview?.lowercase()?.contains(normalizedQuery) == true
                || thread.cwd?.lowercase()?.contains(normalizedQuery) == true
        }
    }

    val archivedThreads = filteredThreads.filter { it.isArchived }
    val liveThreads = filteredThreads.filterNot { it.isArchived }

    val grouped = liveThreads.groupBy { thread ->
        normalizeProjectPath(thread.cwd) ?: "cloud"
    }

    val projectGroups = grouped.map { (projectKey, projectThreads) ->
        val sortedThreads = projectThreads.sortedWith(
            compareByDescending<ThreadSummary> { it.updatedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.id }
        )
        val latest = sortedThreads.firstOrNull()?.updatedAtMillis ?: Long.MIN_VALUE
        val projectPath = if (projectKey == "cloud") null else projectKey
        ThreadProjectGroup(
            id = "project:$projectKey",
            label = projectLabelForPath(projectPath),
            kind = ThreadProjectGroupKind.PROJECT,
            projectPath = projectPath,
            threads = sortedThreads,
            latestUpdatedAtMillis = latest
        )
    }.sortedWith(
        compareByDescending<ThreadProjectGroup> { it.latestUpdatedAtMillis }
            .thenBy { it.label.lowercase() }
            .thenBy { it.id }
    )

    if (archivedThreads.isEmpty()) {
        return projectGroups
    }
    val sortedArchived = archivedThreads.sortedWith(
        compareByDescending<ThreadSummary> { it.updatedAtMillis ?: Long.MIN_VALUE }
            .thenBy { it.id }
    )
    val archivedGroup = ThreadProjectGroup(
        id = "archived",
        label = "Archived (${sortedArchived.size})",
        kind = ThreadProjectGroupKind.ARCHIVED,
        projectPath = null,
        threads = sortedArchived,
        latestUpdatedAtMillis = sortedArchived.firstOrNull()?.updatedAtMillis ?: Long.MIN_VALUE
    )
    return projectGroups + archivedGroup
}

data class ComposerCommand(
    val token: String,
    val title: String,
    val detail: String
)

sealed interface ComposerAutocompleteToken {
    val query: String
    val startIndex: Int
    val endIndexExclusive: Int

    data class File(
        override val query: String,
        override val startIndex: Int,
        override val endIndexExclusive: Int
    ) : ComposerAutocompleteToken

    data class Skill(
        override val query: String,
        override val startIndex: Int,
        override val endIndexExclusive: Int
    ) : ComposerAutocompleteToken

    data class Command(
        override val query: String,
        override val startIndex: Int,
        override val endIndexExclusive: Int
    ) : ComposerAutocompleteToken
}

val DefaultComposerCommands = listOf(
    ComposerCommand("/status", "Status", "Refresh thread/git/rate-limit status."),
    ComposerCommand("/new", "New Chat", "Create a new thread in current project."),
    ComposerCommand("/refresh", "Refresh", "Force workspace refresh."),
    ComposerCommand("/help", "Help", "Show command and mention hints.")
)

fun detectComposerAutocompleteToken(input: String): ComposerAutocompleteToken? {
    if (input.isBlank()) {
        return null
    }
    val end = input.length
    var start = end - 1
    while (start >= 0 && !input[start].isWhitespace()) {
        start -= 1
    }
    val tokenStart = start + 1
    if (tokenStart >= end) {
        return null
    }
    val token = input.substring(tokenStart, end)
    if (token.length < 2) {
        return null
    }

    val prefix = token.first()
    val query = token.substring(1).trim()
    if (query.isEmpty()) {
        return null
    }

    return when (prefix) {
        '@' -> ComposerAutocompleteToken.File(query, tokenStart, end)
        '$' -> {
            if (query.all(Char::isDigit)) {
                null
            } else {
                ComposerAutocompleteToken.Skill(query, tokenStart, end)
            }
        }
        '/' -> ComposerAutocompleteToken.Command(query, tokenStart, end)
        else -> null
    }
}

fun applyComposerAutocompleteSelection(
    originalInput: String,
    token: ComposerAutocompleteToken,
    replacement: String
): String {
    val safeStart = token.startIndex.coerceIn(0, originalInput.length)
    val safeEnd = token.endIndexExclusive.coerceIn(safeStart, originalInput.length)
    val head = originalInput.substring(0, safeStart)
    val tail = originalInput.substring(safeEnd)
    val normalizedReplacement = replacement.trim()
    val spacer = if (tail.startsWith(" ") || tail.startsWith("\n") || tail.isEmpty()) "" else " "
    return (head + normalizedReplacement + spacer + tail).trimEnd() + " "
}

fun filterComposerCommands(query: String): List<ComposerCommand> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) {
        return DefaultComposerCommands
    }
    return DefaultComposerCommands.filter { command ->
        command.token.lowercase().contains(normalizedQuery)
            || command.title.lowercase().contains(normalizedQuery)
            || command.detail.lowercase().contains(normalizedQuery)
    }
}

private fun normalizeProjectPath(path: String?): String? {
    val trimmed = path?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    if (trimmed == "/") {
        return trimmed
    }
    var normalized = trimmed
    while (normalized.endsWith("/")) {
        normalized = normalized.dropLast(1)
    }
    return normalized.ifEmpty { "/" }
}

private fun projectLabelForPath(path: String?): String {
    val normalized = normalizeProjectPath(path) ?: return "Cloud"
    if (normalized == "/") {
        return "/"
    }
    return normalized.substringAfterLast('/').ifBlank { normalized }
}
