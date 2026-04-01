# Android iOS/Web Parity TODO (Canonical)

Source-of-truth:
- iOS app source under `CodexMobile/CodexMobile`
- website claims from `https://www.phodex.app/` (live control, git, secure pairing, `@files/$skills//commands`, steering, desktop sync)

Completion rule:
- This iteration is complete only when all `TODO-01` to `TODO-43` are `DONE`.

Gate status:
- `TODO-01` to `TODO-43`: `DONE` (revalidated on 2026-04-02 after the beyond-parity refresh for supplemental CI, scrubber, foldable dock, and settings polish)

| TODO | Scope | iOS / Website basis | Verification | Evidence | Status |
| --- | --- | --- | --- | --- | --- |
| TODO-01 | Root flow parity | `ContentView.swift` route order | Instrumentation | onboarding/paywall/pairing/home gate sequence | DONE |
| TODO-02 | Onboarding parity | `OnboardingView.swift`, `OnboardingStepPage.swift` | Instrumentation | 5-step CTA progression + copy hierarchy | DONE |
| TODO-03 | Silent paywall gate | `SubscriptionGateView.swift` + dev constraint | Instrumentation | no public pricing, silent dev gate present | DONE |
| TODO-04 | QR pairing scanner | iOS pairing flow + website setup | Instrumentation + live ADB | scanner parses QR and hands off to pairing/connect | DONE |
| TODO-05 | Empty home shell parity | `HomeEmptyStateView.swift` | Screenshot + instrumentation | logo, connection capsule, trusted pair actions | DONE |
| TODO-06 | Sidebar thread browser | `SidebarThreadListView.swift` | Screenshot + instrumentation | project grouping + archived/chat actions | DONE |
| TODO-07 | Timeline parity | iOS turn/timeline rendering | Unit + screenshot | typed rows (user/assistant/command/reasoning/plan/system) | DONE |
| TODO-08 | Composer runtime parity | iOS composer behavior | Instrumentation | send/stop/queue semantics + accessory flow | DONE |
| TODO-09 | `@files/$skills//commands` parity | website + `TurnComposerCommandState.swift` | Instrumentation | mention chips + autocomplete + slash commands | DONE |
| TODO-10 | Git actions parity | iOS git controls | Unit + live ADB | status/branches/checkout/commit/pull/push | DONE |
| TODO-11 | Thread lifecycle parity | iOS thread lifecycle | Unit + live ADB | start/resume/fork/interrupt/archive/unarchive/delete | DONE |
| TODO-12 | `/steer` + review/fork semantics | website + iOS slash workflow | Unit + instrumentation | review target/fork destination + steer dispatch | DONE |
| TODO-13 | Voice + media parity | iOS attachment workflow | Instrumentation + live ADB | camera/gallery + voice draft pipeline | DONE |
| TODO-14 | Notifications parity | iOS local status signaling | Unit + live ADB | status/permission/rate/git/ci event notifications | DONE |
| TODO-15 | Settings parity | `SettingsView.swift` | Instrumentation | dedicated settings route + iOS section order | DONE |
| TODO-16 | Branding + visual parity baseline | shipped app assets | Screenshot comparison | Remodex name/logo + iOS-aligned shell styling | DONE |
| TODO-17 | Transport reconnect stability | iOS connection lifecycle intent | Live ADB soak | no reconnect wedge on send/timeout loop | DONE |
| TODO-18 | Event-stream ingestion parity | `CodexService+Incoming.swift` | Unit + live ADB | inbound deltas/notifications reconcile state | DONE |
| TODO-19 | Live ADB acceptance | local-first runbook | Live ADB | pair/send/interrupt/git/settings/reconnect | DONE |
| TODO-20 | Release gate closure | parity policy | Release checklist | all TODO rows complete + evidence captured | DONE |
| TODO-21 | `/fork` submenu parity | `SlashCommandAutocompletePanel.swift` | Instrumentation + live ADB | local/new-worktree fork destinations work | DONE |
| TODO-22 | Queued drafts panel parity | `QueuedDraftsPanel.swift` | Unit + instrumentation | restore/steer/remove with stable draft IDs | DONE |
| TODO-23 | Settings section card parity | `SettingsView.swift` | Screenshot + instrumentation | iOS card order + local-first content | DONE |
| TODO-24 | `initialize`/`initialized` + resume parity | `CodexService+Connection.swift` | Unit + live ADB | not-initialized recovery + pre-turn resume | DONE |
| TODO-25 | Android-first product copy | Android branding adaptation of `OnboardingView.swift` + `SubscriptionGateView.swift` | Instrumentation + screenshot | Android onboarding/paywall copy no longer says iPhone | DONE |
| TODO-26 | Composer secondary bar parity | `TurnComposerSecondaryBar.swift` + `ComposerBottomBar.swift` | Instrumentation + screenshot | lower runtime/status row remains distinct from main composer card | DONE |
| TODO-27 | Sidebar density parity | `SidebarThreadListView.swift` + `SidebarThreadRowView.swift` | Instrumentation + screenshot | denser thread rows and lighter drawer chrome match iOS scanning rhythm better | DONE |
| TODO-28 | Onboarding hero fidelity | `OnboardingWelcomePage.swift`, `OnboardingFeaturesPage.swift`, `OnboardingStepPage.swift` | Instrumentation + screenshot | hero splash, centered feature page, and centered step cards now match iOS structure | DONE |
| TODO-29 | Paywall feature-strip fidelity | `SubscriptionGateView.swift` | Instrumentation + screenshot | centered hero plus horizontal feature cards mirror the iOS paywall shell | DONE |
| TODO-30 | Filesystem cwd normalization parity | `CodexThread.swift`, `CodexService+ThreadsTurns.swift` | Unit | pseudo cwd buckets are rejected while `~/` and Windows drive roots stay valid | DONE |
| TODO-31 | Bridge-managed account parity | `CodexService+Account.swift` | Unit + settings UI | ChatGPT/account state and bridge package versions mirror from the paired Mac | DONE |
| TODO-32 | Voice recovery guidance parity | `ConnectionRecoveryCard.swift`, `GPTVoiceSetupSheet.swift`, `TurnView.swift` | Instrumentation + runtime UI | reconnect/sign-in/sync-needed voice states render guided recovery instead of generic failure text | DONE |
| TODO-33 | Codex install step reminder parity | `OnboardingView.swift` | Instrumentation | onboarding warns before leaving the Codex CLI install step | DONE |
| TODO-34 | Git progress parity | upstream git action progress states | Unit + workspace UI | pull/push/commit/checkout actions show temporary in-progress state in workspace meta | DONE |
| TODO-35 | Foreground bridge version refresh parity | `SettingsView.swift`, `CodexService+Account.swift` | Foreground/settings refresh | bridge version state refreshes independently on foreground return and settings refresh | DONE |
| TODO-36 | Composer input sizing parity | `TurnComposerInputTextView.swift` | Instrumentation + screenshot | composer now uses a tighter 1-4 line growth range and body-sized text closer to iOS | DONE |
| TODO-37 | Recovery empty-state/help parity | `TurnConversationContainerView.swift`, `GPTVoiceSetupSheet.swift` | Instrumentation + runtime UI | recovery accessories replace the generic empty-state card and voice help opens setup guidance | DONE |
| TODO-38 | Composer card structure parity | `TurnComposerView.swift`, `TurnComposerHostView.swift` | Instrumentation + screenshot | accessory chips, input field, and runtime controls now live in one composer card in the same high-level order as iOS | DONE |
| TODO-39 | Composer bottom-bar parity | `ComposerBottomBar.swift`, `TurnComposerSecondaryBar.swift` | Instrumentation + screenshot | dock now uses compact runtime menus on the left, voice/stop/send controls on the right, and a lighter status row below | DONE |
| TODO-40 | Supplemental thread CI parity | `GitActionsService.swift` remote URL flow + public provider fallback | Unit + runtime refresh | bridge CI remains primary, but repo-bound threads can fall back to public GitHub/GitLab pipeline APIs when the bridge has no CI result | DONE |
| TODO-41 | Conversation scrubber and title marquee | iOS turn-header treatment + dense timeline navigation intent | Instrumentation + live ADB | long titles scroll in place and the side scrubber jumps across user-message anchors | DONE |
| TODO-42 | Foldable composer dock | iOS composer shell adapted with Android-specific fold/minimize behavior | Instrumentation + screenshot | unfocused composer collapses to a side handle, keeps one-button send/stop behavior, and respects dock-side preference | DONE |
| TODO-43 | Settings and diagnostics polish | `SettingsView.swift` visual system | Instrumentation + screenshot | dark-mode settings contrast, font previews, and icon-style logger controls all use the shared theme tokens | DONE |

## Evidence Pack
- `./gradlew -g /tmp/gradle-home :app:testDebugUnitTest :app:assembleDebug`
- `./gradlew -g /tmp/gradle-home :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.remodex.mobile.ui.ParityUiInstrumentationTest`
- `bash CodexAndroid/scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9000 --device 192.168.31.185:43277 --wait-seconds 80 --skip-build` (optional/manual)
- `bash CodexAndroid/scripts/logger_db_self_test.sh --device 192.168.31.185:43277`

## Backlog (Non-iOS Custom Ideas, Deferred)
- Genie-style minimized composer animation refinement beyond the current Compose-safe fold/collapse.
- Full-text conversation indexing/search (repo path, title, content).
- Public CI coverage beyond GitHub/GitLab.
