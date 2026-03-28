package com.remodex.mobile.ui.parity

enum class TodoState {
    TODO,
    IN_PROGRESS,
    DONE
}

data class WebsiteTodo(
    val id: String,
    val title: String,
    val detail: String
)

val WebsiteFeatureTodos = listOf(
    WebsiteTodo(
        id = "pairing",
        title = "Secure pairing + local connect",
        detail = "QR/pairing-ready login entry and relay bootstrap."
    ),
    WebsiteTodo(
        id = "workspace",
        title = "Structured working workspace",
        detail = "Current project, chat timeline, code-change controls, and model switching."
    ),
    WebsiteTodo(
        id = "git",
        title = "Git from mobile",
        detail = "Branch checkout plus pull/push/commit from Android."
    ),
    WebsiteTodo(
        id = "refresh-notify",
        title = "Refresh + notifications",
        detail = "Press-and-slide-down refresh gesture and work/rate-limit/git/CI notification events."
    ),
    WebsiteTodo(
        id = "task-steering",
        title = "Interactive task steering",
        detail = "Task list progression and live workspace control loop."
    ),
    WebsiteTodo(
        id = "files-skills",
        title = "@files, \$skills, /commands",
        detail = "Composer enhancements for file mentions, skill invokes, and command shortcuts."
    ),
    WebsiteTodo(
        id = "voice-media",
        title = "Voice + media parity",
        detail = "Camera/gallery upload and voice transcription support."
    ),
    WebsiteTodo(
        id = "monetization",
        title = "Monetization parity",
        detail = "RevenueCat entitlements, restore flows, and paywall parity."
    )
)

data class ComponentParityItem(
    val component: String,
    val iosReference: String,
    val status: TodoState
)

val ComponentParityChecklist = listOf(
    ComponentParityItem("Onboarding", "OnboardingView + step pages", TodoState.DONE),
    ComponentParityItem("Pairing/Connect", "QR scanner + pairing shell", TodoState.DONE),
    ComponentParityItem("Home Empty", "HomeEmptyStateView", TodoState.IN_PROGRESS),
    ComponentParityItem("Sidebar", "SidebarView + grouped thread rows", TodoState.IN_PROGRESS),
    ComponentParityItem("Turn Composer", "TurnComposerView + bottom bar", TodoState.IN_PROGRESS),
    ComponentParityItem("Timeline", "TurnTimelineView", TodoState.IN_PROGRESS),
    ComponentParityItem("Settings", "SettingsView runtime defaults", TodoState.DONE),
    ComponentParityItem("Payments", "SubscriptionGate + RevenueCat paywall", TodoState.IN_PROGRESS)
)

fun advanceNextTodo(todoStates: MutableMap<String, TodoState>) {
    val inProgress = WebsiteFeatureTodos.firstOrNull { todoStates[it.id] == TodoState.IN_PROGRESS }
    if (inProgress != null) {
        todoStates[inProgress.id] = TodoState.DONE
        return
    }
    val nextTodo = WebsiteFeatureTodos.firstOrNull { todoStates[it.id] == TodoState.TODO } ?: return
    todoStates[nextTodo.id] = TodoState.IN_PROGRESS
}
