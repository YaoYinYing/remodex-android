package com.remodex.mobile.service

import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal enum class CiProvider {
    GITHUB,
    GITLAB
}

internal data class GitRemoteDescriptor(
    val url: String,
    val ownerRepo: String,
    val provider: CiProvider,
    val host: String
)

internal data class SupplementalCiStatus(
    val summary: String,
    val isTerminal: Boolean
)

internal fun parseGitRemoteDescriptor(rawUrl: String?, ownerRepoHint: String? = null): GitRemoteDescriptor? {
    val normalizedUrl = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val host = parseGitRemoteHost(normalizedUrl) ?: return null
    val ownerRepo = ownerRepoHint
        ?.trim()
        ?.removeSuffix(".git")
        ?.takeIf { it.contains('/') }
        ?: parseOwnerRepoFromRemoteUrl(normalizedUrl)
        ?: return null
    val provider = when {
        host.contains("github.com", ignoreCase = true) -> CiProvider.GITHUB
        host.contains("gitlab.com", ignoreCase = true) -> CiProvider.GITLAB
        else -> return null
    }
    return GitRemoteDescriptor(
        url = normalizedUrl,
        ownerRepo = ownerRepo,
        provider = provider,
        host = host
    )
}

internal fun buildGitHubActionsUrl(ownerRepo: String): String {
    return "https://api.github.com/repos/$ownerRepo/actions/runs?per_page=1"
}

internal fun buildGitLabPipelinesUrl(ownerRepo: String): String {
    val encodedProject = URLEncoder.encode(ownerRepo, Charsets.UTF_8.name())
    return "https://gitlab.com/api/v4/projects/$encodedProject/pipelines?per_page=1"
}

internal fun formatGitHubActionsStatus(result: JsonObject): SupplementalCiStatus? {
    val runs = result["workflow_runs"] as? JsonArray ?: return null
    val latestRun = runs.firstOrNull() as? JsonObject ?: return null
    val status = latestRun.string("conclusion", "status")?.lowercase().orEmpty()
    val buildName = latestRun.string("name", "display_title", "workflow_name")
    val normalized = status.ifBlank { "unknown" }
    return SupplementalCiStatus(
        summary = if (buildName.isNullOrBlank()) {
            "CI status: $normalized (GitHub)"
        } else {
            "CI status: $normalized ($buildName · GitHub)"
        },
        isTerminal = normalized in setOf("success", "failure", "cancelled", "timed_out", "neutral", "skipped", "action_required")
    )
}

internal fun formatGitLabPipelineStatus(result: JsonArray): SupplementalCiStatus? {
    val latestPipeline = result.firstOrNull() as? JsonObject ?: return null
    val status = latestPipeline.string("status")?.lowercase().orEmpty().ifBlank { "unknown" }
    val buildName = latestPipeline.string("name", "ref")
    return SupplementalCiStatus(
        summary = if (buildName.isNullOrBlank()) {
            "CI status: $status (GitLab)"
        } else {
            "CI status: $status ($buildName · GitLab)"
        },
        isTerminal = status in setOf("success", "failed", "canceled", "skipped", "manual")
    )
}

private fun parseGitRemoteHost(rawUrl: String): String? {
    val httpsMatch = Regex("^https?://([^/]+)/").find(rawUrl)
    if (httpsMatch != null) {
        return httpsMatch.groupValues[1]
    }
    val sshMatch = Regex("^[^@]+@([^:]+):").find(rawUrl)
    if (sshMatch != null) {
        return sshMatch.groupValues[1]
    }
    return null
}

private fun parseOwnerRepoFromRemoteUrl(rawUrl: String): String? {
    val match = Regex("[:/]([^/]+/[^/]+?)(?:\\.git)?$").find(rawUrl)
    return match?.groupValues?.getOrNull(1)
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
