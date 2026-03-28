package com.remodex.mobile.ui.parity

enum class TodoState {
    TODO,
    IN_PROGRESS,
    DONE
}

data class WebsiteTodo(
    val id: String,
    val title: String,
    val detail: String,
    val iosReference: String,
    val websiteClaim: String,
    val defaultState: TodoState
)

data class ParityAcceptanceItem(
    val todoId: String,
    val verification: String,
    val evidence: String,
    val defaultState: TodoState = TodoState.DONE
)

data class ComponentParityItem(
    val component: String,
    val iosReference: String,
    val status: TodoState
)

val WebsiteFeatureTodos = listOf(
    WebsiteTodo(
        id = "TODO-01",
        title = "Root flow parity",
        detail = "Mirror the iOS gate order: onboarding, silent paywall, pairing, empty home, then the working shell.",
        iosReference = "ContentView root routing + HomeEmptyStateView",
        websiteClaim = "Remote control flow starts from a secure paired shell.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-02",
        title = "Onboarding parity",
        detail = "Keep the five-page dark onboarding with the same copy hierarchy and CTA progression.",
        iosReference = "OnboardingView + welcome/features/step pages",
        websiteClaim = "Install the bridge and pair from the onboarding flow.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-03",
        title = "Silent paywall gate",
        detail = "Preserve the dev-only paywall shell without public pricing or release-copy noise.",
        iosReference = "SubscriptionGateView + RevenueCatPaywallView",
        websiteClaim = "Product gate remains present while public monetization stays deferred.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-04",
        title = "QR pairing scanner",
        detail = "Match the iOS scanner UX, bridge-update handling, and scan-success banner/close behavior.",
        iosReference = "QRScannerView + QRScannerPairingValidator",
        websiteClaim = "Secure pairing starts by scanning the bridge QR code.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-05",
        title = "Home empty state",
        detail = "Show the branded empty shell with trusted pair summary, connection state capsule, and reconnect CTA.",
        iosReference = "HomeEmptyStateView",
        websiteClaim = "One-tap reconnect from the empty state.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-06",
        title = "Sidebar thread browser",
        detail = "Keep project grouping, archived separation, thread actions, run badges, and selection behavior aligned to iOS.",
        iosReference = "SidebarView + SidebarThreadListView + SidebarThreadRowView",
        websiteClaim = "Browse chats by project and act on them from the side drawer.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-07",
        title = "Timeline parity",
        detail = "Render assistant, user, command, reasoning, plan, and status items with the same item-aware structure as iOS.",
        iosReference = "TurnTimelineView + TurnMessageComponents",
        websiteClaim = "Live turn history mirrors the Mac session.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-08",
        title = "Composer runtime parity",
        detail = "Match the iOS composer controls, queued drafts, runtime menus, and turn send/stop affordances.",
        iosReference = "TurnComposerView + TurnComposerHostView",
        websiteClaim = "Compose with the same controls used on iPhone.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-09",
        title = "@files, \$skills, /commands",
        detail = "Keep mention chips, autocomplete panels, and slash-command routing consistent with the iOS composer.",
        iosReference = "FileAutocompletePanel + SkillAutocompletePanel + SlashCommandAutocompletePanel",
        websiteClaim = "Mention files, invoke skills, and trigger commands inline.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-10",
        title = "Git actions parity",
        detail = "Expose status, branches, checkout, commit, pull, and push with the same project-bound behavior as iOS.",
        iosReference = "TurnGitActionsToolbar + TurnGitBranchSelector",
        websiteClaim = "Handle git commands from the phone.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-11",
        title = "Thread lifecycle parity",
        detail = "Preserve thread start, resume, fork, interrupt, archive, unarchive, and delete behavior across reconnects.",
        iosReference = "TurnThreadForkCoordinator + Thread resume/start flows",
        websiteClaim = "Keep shared thread history synced with the Mac.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-12",
        title = "Task steering and plan/review",
        detail = "Keep plan-mode, review-start, and steer flows aligned with the iOS task control workflow.",
        iosReference = "TurnPlanModeComponents + plan/review orchestration",
        websiteClaim = "Steer active work without restarting the turn.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-13",
        title = "Voice and media parity",
        detail = "Match camera/gallery upload and voice transcription affordances from the iOS turn screen.",
        iosReference = "CameraImagePicker + VoiceRecordingCapsule",
        websiteClaim = "Send photos and voice notes in the same workflow.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-14",
        title = "Notifications parity",
        detail = "Keep workflow notifications for work status, permissions, rate limits, git actions, and CI/CD updates.",
        iosReference = "ThreadCompletionBannerView + notification flow",
        websiteClaim = "Get alerts when work changes or needs attention.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-15",
        title = "Settings parity",
        detail = "Match the iOS settings cards for fonts, tone mode, runtime defaults, connection state, and usage views.",
        iosReference = "SettingsView",
        websiteClaim = "Configure the app from a dedicated settings surface.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-16",
        title = "Branding and visual polish",
        detail = "Keep the original author icon, Remodex naming, fonts, spacing, chips, and motion rhythm aligned to iOS.",
        iosReference = "AppLogo asset + iOS font/style system",
        websiteClaim = "Match the product's polished iPhone presentation.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-17",
        title = "Transport recovery",
        detail = "Stabilize relay reconnect, socket reopen, and retry boundaries so send failures do not wedge the session.",
        iosReference = "ContentViewModel reconnect recovery + CodexService connection flow",
        websiteClaim = "Reconnect automatically to the paired Mac.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-18",
        title = "Event-stream ingestion",
        detail = "Handle server notifications and deltas so the Android timeline stays in sync with live iOS behavior.",
        iosReference = "CodexService incoming event handlers",
        websiteClaim = "Live control and streaming updates stay visible on mobile.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-19",
        title = "Live ADB acceptance",
        detail = "Build, install, pair, and run the end-to-end test flow on a physical device with captured evidence.",
        iosReference = "Android parity instrumentation + live relay runbook",
        websiteClaim = "Device-level validation proves the app really works.",
        defaultState = TodoState.DONE
    ),
    WebsiteTodo(
        id = "TODO-20",
        title = "Final gate closure",
        detail = "Close the parity checklist only after all feature, UI, and live acceptance checks are complete.",
        iosReference = "Parity gate artifact + final release commit",
        websiteClaim = "The iteration is only done when all parity tasks are complete.",
        defaultState = TodoState.DONE
    )
)

val ParityAcceptanceMatrix = listOf(
    ParityAcceptanceItem(
        todoId = "TODO-01",
        verification = "Instrumentation",
        evidence = "Launch flow lands on the same root gate order as iOS."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-02",
        verification = "Instrumentation",
        evidence = "Five onboarding pages and CTA progression render with the expected copy."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-03",
        verification = "Instrumentation",
        evidence = "Paywall shell shows no public pricing and preserves dev-only access."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-04",
        verification = "Instrumentation + live ADB",
        evidence = "QR scan opens pairing, shows mismatch handling, then resumes cleanly."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-05",
        verification = "Instrumentation + screenshot",
        evidence = "Empty home shows the branded summary state and reconnect CTA."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-06",
        verification = "Instrumentation + screenshot",
        evidence = "Sidebar groups threads by project and supports archive/rename/delete."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-07",
        verification = "Instrumentation",
        evidence = "Timeline shows the expected item types and ordering."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-08",
        verification = "Instrumentation",
        evidence = "Composer renders runtime controls, queue controls, and send/stop actions."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-09",
        verification = "Instrumentation",
        evidence = "Mention and slash-command panels appear for @files, \$skills, and /commands."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-10",
        verification = "Unit + live ADB",
        evidence = "Git status/branches/checkout/commit/pull/push succeed in a repo-bound thread."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-11",
        verification = "Unit + live ADB",
        evidence = "Start/resume/fork/interrupt and archive/unarchive flows preserve active state."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-12",
        verification = "Unit + instrumentation",
        evidence = "Plan, review, and steering controls resolve to the same workflow semantics as iOS."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-13",
        verification = "Instrumentation + live ADB",
        evidence = "Camera/gallery uploads and voice transcription complete end-to-end."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-14",
        verification = "Unit + live ADB",
        evidence = "Notifications fire for work status, permissions, rate limits, git, and CI/CD events."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-15",
        verification = "Instrumentation",
        evidence = "Settings cards expose font, tone, runtime defaults, and connection controls."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-16",
        verification = "Screenshot comparison",
        evidence = "Typography, spacing, and iconography match the shipped iOS presentation."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-17",
        verification = "Live ADB soak",
        evidence = "Reconnect and resume recover without socket send failure loops."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-18",
        verification = "Unit + live ADB",
        evidence = "Incoming notifications and deltas update the active turn timeline."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-19",
        verification = "Live ADB",
        evidence = "APK installs, pairs, and runs the living test matrix on a physical device."
    ),
    ParityAcceptanceItem(
        todoId = "TODO-20",
        verification = "Release gate",
        evidence = "All TODOs are DONE and the final commit/push is complete."
    )
)

val ComponentParityChecklist = listOf(
    ComponentParityItem("Onboarding", "OnboardingView + step pages", TodoState.DONE),
    ComponentParityItem("Pairing/Connect", "QR scanner + pairing shell", TodoState.DONE),
    ComponentParityItem("Home Empty", "HomeEmptyStateView", TodoState.DONE),
    ComponentParityItem("Sidebar", "SidebarView + grouped thread rows", TodoState.DONE),
    ComponentParityItem("Turn Composer", "TurnComposerView + bottom bar", TodoState.DONE),
    ComponentParityItem("Timeline", "TurnTimelineView", TodoState.DONE),
    ComponentParityItem("Settings", "SettingsView runtime defaults", TodoState.DONE),
    ComponentParityItem("Payments", "SubscriptionGate + RevenueCat paywall", TodoState.DONE)
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
