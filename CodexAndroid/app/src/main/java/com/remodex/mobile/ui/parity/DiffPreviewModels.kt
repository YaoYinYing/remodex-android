package com.remodex.mobile.ui.parity

data class DiffPreviewEntry(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val action: String,
    val diff: String
) {
    val compactPath: String
        get() = path.substringAfterLast('/').substringAfterLast('\\')

    val directoryPath: String?
        get() = path.substringBeforeLast('/', missingDelimiterValue = "")
            .ifBlank { null }
}

object DiffPreviewParser {
    fun parse(rawPatch: String): List<DiffPreviewEntry> {
        val lines = rawPatch.split('\n')
        if (lines.isEmpty()) {
            return emptyList()
        }

        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                chunks += current.toList()
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) {
            chunks += current.toList()
        }

        return chunks.mapNotNull { chunk ->
            val path = extractPath(chunk) ?: return@mapNotNull null
            val diff = chunk.joinToString("\n").trim().ifEmpty { return@mapNotNull null }
            DiffPreviewEntry(
                path = path,
                additions = chunk.count { it.startsWith("+") && !it.startsWith("+++") },
                deletions = chunk.count { it.startsWith("-") && !it.startsWith("---") },
                action = detectAction(chunk),
                diff = diff
            )
        }
    }

    private fun extractPath(chunk: List<String>): String? {
        chunk.firstOrNull { it.startsWith("+++ ") }
            ?.removePrefix("+++ ")
            ?.normalizeDiffPath()
            ?.takeIf { it.isNotBlank() && it != "/dev/null" }
            ?.let { return it }
        chunk.firstOrNull { it.startsWith("--- ") }
            ?.removePrefix("--- ")
            ?.normalizeDiffPath()
            ?.takeIf { it.isNotBlank() && it != "/dev/null" }
            ?.let { return it }
        chunk.firstOrNull { it.startsWith("diff --git ") }
            ?.split(" ")
            ?.getOrNull(3)
            ?.normalizeDiffPath()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    private fun detectAction(chunk: List<String>): String {
        return when {
            chunk.any { it.startsWith("rename from ") || it.startsWith("rename to ") } -> "Renamed"
            chunk.any { it.startsWith("new file mode ") || it == "--- /dev/null" } -> "Added"
            chunk.any { it.startsWith("deleted file mode ") || it == "+++ /dev/null" } -> "Deleted"
            else -> "Edited"
        }
    }

    private fun String.normalizeDiffPath(): String {
        val trimmed = trim()
        return when {
            trimmed.startsWith("a/") || trimmed.startsWith("b/") -> trimmed.drop(2)
            else -> trimmed
        }
    }
}
