# Android iOS/Web Parity TODO (Canonical)

Source-of-truth:
- iOS app source under `CodexMobile/CodexMobile`
- website claims from `https://www.phodex.app/` (live control, git, secure pairing, `@files/$skills//commands`, steering, desktop sync)

Completion rule:
- This iteration is complete only when all `TODO-01` to `TODO-24` are `DONE`.

Gate status:
- `TODO-01` to `TODO-24`: `DONE` (revalidated on 2026-03-29)

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

## Evidence Pack
- `./gradlew -g /tmp/gradle-home :app:testDebugUnitTest :app:assembleDebug`
- `./gradlew -g /tmp/gradle-home :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.remodex.mobile.ui.ParityUiInstrumentationTest`
- `bash CodexAndroid/scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9000 --device 192.168.31.185:40927 --wait-seconds 80 --skip-build` (optional/manual)
- `bash CodexAndroid/scripts/logger_db_self_test.sh --device 192.168.31.185:40927`

## Backlog (Non-iOS Custom Ideas, Deferred)
- Genie-style minimized composer with dock-side pinning.
- Marquee/scrolling workspace title behavior.
- One-button compact composer mode variant.
- Full-text conversation indexing/search (repo path, title, content).
- Remote CI status ingestion from public GitHub/GitLab APIs.
