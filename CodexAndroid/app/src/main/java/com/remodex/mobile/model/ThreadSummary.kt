package com.remodex.mobile.model

data class ThreadSummary(
    val id: String,
    val title: String?,
    val name: String?,
    val preview: String?,
    val cwd: String?,
    val updatedAtMillis: Long?,
    val isArchived: Boolean = false
) {
    val displayTitle: String
        get() {
            val trimmedName = name?.trim().orEmpty()
            if (trimmedName.isNotEmpty()) {
                return trimmedName
            }

            val trimmedTitle = title?.trim().orEmpty()
            if (trimmedTitle.isNotEmpty()) {
                return trimmedTitle
            }

            val trimmedPreview = preview?.trim().orEmpty()
            if (trimmedPreview.isNotEmpty()) {
                return trimmedPreview
            }

            return "New Thread"
        }
}
