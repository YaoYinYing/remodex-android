# Android iOS/Web Parity TODOs (This Iteration)

Source-of-truth inputs:
- iOS app source under `CodexMobile/CodexMobile`
- website feature surface from `https://www.phodex.app/` (live control, git from iPhone, secure pairing, `@files/$skills//commands`, task steering, desktop sync)

Completion rule for this iteration: every item below must be `DONE`.

| ID | TODO | iOS / Website basis | Status |
| --- | --- | --- | --- |
| IW-01 | Rebuild onboarding as a 5-step pager with iOS CTA progression (`Get Started` -> `Set Up` -> `Continue` -> `Scan QR Code`). | `OnboardingView.swift`, website setup flow | DONE |
| IW-02 | Keep onboarding setup commands/copy hierarchy aligned to iOS + website setup text. | `OnboardingStepPage.swift`, website setup section | DONE |
| IW-03 | Keep paywall as silent dev gate (no public pricing) while mirroring iOS feature hierarchy cards. | `SubscriptionGateView.swift`, user constraint | DONE |
| IW-04 | Align paywall feature copy to website claims (live control, git, secure pairing, command/mention workflow). | website interface/features section | DONE |
| IW-05 | Bring empty-home visual shell closer to iOS by adding original app logo + connection status capsule treatment. | `HomeEmptyStateView.swift` | DONE |
| IW-06 | Add slash-command parity for `/review` and `/subagents` from website claim coverage. | website `@files, $skills, /commands` | DONE |
| IW-07 | Add active-turn steering command path (`/steer`) in composer flow. | website task steering claim | DONE |
| IW-08 | Update Android instrumentation expectations to match the new onboarding/pairing UI wording and current `WorkspaceScreen` API. | Android parity test suite | DONE |
| IW-09 | Keep local relay living test green after parity updates. | local-first runbook + ADB validation | DONE |
| IW-10 | Keep logger SQLite/redaction verification green after parity updates. | connection logger acceptance path | DONE |
| IW-11 | Extend thread metadata parity (`parent/fork/subagent/model`) so sidebar rows can mirror iOS hierarchy cues. | `CodexThread.swift`, `SidebarThreadRowView.swift` | DONE |
| IW-12 | Add subagent hierarchy flattening and collapse affordance in grouped thread lists. | `SidebarThreadListView.swift` parent/subagent tree behavior | DONE |
| IW-13 | Add dedicated settings surface (runtime defaults, appearance, connection, diagnostics) accessible from workspace flow. | `SettingsView.swift` card-based navigation target | DONE |
| IW-14 | Expand incoming event-stream coverage for plan/reasoning/tool/file/command/diff/account/rate update paths. | `CodexService+Incoming.swift` method routing parity | DONE |

## Evidence
- `./gradlew -g /tmp/gradle-home :app:testDebugUnitTest :app:assembleDebug`
- `./gradlew -g /tmp/gradle-home :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.remodex.mobile.ui.ParityUiInstrumentationTest`
- `bash CodexAndroid/scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9100 --device 192.168.31.185:38563 --wait-seconds 70`
- `bash CodexAndroid/scripts/logger_db_self_test.sh --device 192.168.31.185:38563`
