package com.remodex.mobile.model

fun normalizeFilesystemProjectPath(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    if (trimmed == "Project path not resolved.") {
        return null
    }
    normalizedFilesystemRootPath(trimmed)?.let { return it }
    var normalized = trimmed
    while (normalized.endsWith("/") || normalized.endsWith("\\")) {
        normalized = normalized.dropLast(1)
    }
    if (normalized.isEmpty()) {
        return "/"
    }
    return normalized.takeIf(::isLikelyFilesystemPath)
}

private fun normalizedFilesystemRootPath(value: String): String? {
    if (value == "/") {
        return "/"
    }
    if (value == "~/" || value.firstOrNull() == '~' && value.drop(1).all { it == '/' }) {
        return "~/"
    }
    if (value.length >= 3 && value[1] == ':' && value[2] in listOf('/', '\\') && value[0].isLetter()) {
        if (value.drop(3).all { it == '/' || it == '\\' }) {
            return "${value[0]}:/"
        }
    }
    return null
}

private fun isLikelyFilesystemPath(value: String): Boolean {
    if (value == "/" || value.startsWith("~/") || value.startsWith("/")) {
        return true
    }
    if (value.startsWith("\\\\")) {
        return true
    }
    return value.length >= 3 && value[1] == ':' && value[2] in listOf('/', '\\') && value[0].isLetter()
}
