# Android iOS/Web Parity TODO (Canonical)

Source of truth:
- iOS app source under `CodexMobile/CodexMobile`
- website claims from `https://www.phodex.app/`

Completion rule:
- This iteration is complete only when every target below is `DONE` with evidence.
- Status values: `TODO`, `IN_PROGRESS`, `DONE`, `BLOCKED`.

## Target Ledger

| ID | Target | Basis | Evidence | Status |
| --- | --- | --- | --- | --- |
| T-01 | Refactor workspace into iOS-aligned modules (shell/sidebar/timeline/composer/settings) | `ContentView.swift`, `TurnView.swift` | module boundaries + compile | IN_PROGRESS |
| T-02 | Split service responsibilities (connection/runtime/incoming/git/account) | iOS `CodexService+*.swift` | subsystem files + unit tests | TODO |
| T-03 | Replace stale parity table and keep this ledger as canonical source | local policy | updated TODO ledger | DONE |
| T-04 | Root gate order parity (onboarding/paywall/pairing/workspace/settings) | `ContentView.swift` | instrumentation + screenshots | TODO |
| T-05 | Sidebar shell/gesture parity | `SidebarView.swift` | instrumentation + live ADB | TODO |
| T-06 | Sidebar grouping and project-aware new chat parity | `SidebarThreadGrouping.swift` | unit + instrumentation | TODO |
| T-07 | Sidebar search parity over loaded metadata | `SidebarSearchField.swift` | unit + instrumentation | TODO |
| T-08 | Thread row visual density parity | `SidebarThreadRowView.swift` | screenshot diff | TODO |
| T-09 | Timeline typed message model parity (`chat/thinking/tool/file/command/subagent/plan/userInputPrompt`) | `CodexMessage.swift` | model + parser tests | IN_PROGRESS |
| T-10 | Stable message ordering semantics for streaming merges | iOS message order index | reducer tests | IN_PROGRESS |
| T-11 | Item-aware delta reconciliation parity | `CodexService+Incoming.swift` | unit tests | TODO |
| T-12 | Command execution details row parity (status/cwd/output/expand) | `CommandExecutionViews.swift` | parser + UI tests | IN_PROGRESS |
| T-13 | Tool activity summarization parity | `CodexService+IncomingSupport.swift` | unit tests | TODO |
| T-14 | File-change and diff row parity | `TurnFileChangeSummaryParser.swift` | unit + screenshot | TODO |
| T-15 | Subagent action model + row parity | `CodexCollaboration.swift`, `SubagentViews.swift` | parser + UI tests | IN_PROGRESS |
| T-16 | Structured user-input prompt card parity (`item/tool/requestUserInput`) | `CodexCollaboration.swift`, `StructuredUserInputCardView.swift` | reducer + instrumentation | IN_PROGRESS |
| T-17 | Plan state card parity (explanation + steps statuses) | `PlanAccessoryCard.swift` | parser + UI tests | IN_PROGRESS |
| T-18 | Thinking disclosure behavior parity | `ThinkingDisclosureParser.swift` | unit + screenshot | TODO |
| T-19 | Composer host/state refactor to iOS structure | `TurnComposerHostView.swift`, `TurnComposerViewState.swift` | module split + tests | TODO |
| T-20 | Composer autocomplete parity (`@files/$skills//commands`) | `FileAutocompletePanel.swift`, `SkillAutocompletePanel.swift`, `SlashCommandAutocompletePanel.swift` | instrumentation | TODO |
| T-21 | Queued drafts parity | `QueuedDraftsPanel.swift` | unit + instrumentation | TODO |
| T-22 | Send/stop runtime semantics parity | `TurnViewModel.swift` | unit + live ADB | TODO |
| T-23 | Runtime controls parity (model/reasoning/access/service tier where supported) | `CodexService+RuntimeConfig.swift` | unit + instrumentation | TODO |
| T-24 | Attachment/camera/gallery/voice pipeline parity | iOS turn attachment pipeline | instrumentation + live ADB | TODO |
| T-25 | Thread lifecycle parity (`thread/start/resume/fork`) | `CodexService+ThreadsTurns.swift`, `CodexService+ThreadFork.swift` | unit + live ADB | TODO |
| T-26 | Turn lifecycle parity (`turn/start/steer/interrupt`) | `CodexService+ThreadsTurns.swift` | unit + live ADB | TODO |
| T-27 | Runtime fallback parity for access mode (`approvalPolicy/sandboxPolicy/legacy`) | `CodexAccessMode.swift`, `CodexService+RuntimeConfig.swift` | unit + live ADB | IN_PROGRESS |
| T-28 | Missing-thread continuation recovery parity | iOS continuation handling | unit tests | TODO |
| T-29 | Active-turn rehydration on reconnect parity | iOS reconnect lifecycle | unit + live ADB soak | TODO |
| T-30 | Review/worktree/git toolbar parity | `TurnToolbarContent.swift`, `TurnGitActionsToolbar.swift` | instrumentation + live ADB | TODO |
| T-31 | Git/CI visibility strictly thread-repo scoped | local-first guardrail | unit + UI assertions | TODO |
| T-32 | Preserve local relay + bridge-managed account/rate-limit as primary truth | local-first guardrail | live ADB + settings checks | TODO |
| T-33 | Desktop sync stability for phone-originated sends | bridge refresher behavior | live ADB + refresh trace | TODO |
| T-34 | No refresh-route dancing regressions | bridge refresher behavior | trace assertions | TODO |
| T-35 | Scanner/workspace/settings status bar theme parity | UX parity requirement | screenshots | TODO |
| T-36 | Settings section order/content parity | `SettingsView.swift` | screenshot + instrumentation | TODO |
| T-37 | Settings dark mode contrast parity | `SettingsView.swift` | screenshot review | TODO |
| T-38 | Notification management parity (pinned status + toggles) | iOS notification behavior | settings + runtime validation | TODO |
| T-39 | Logger UX parity with diagnostics hierarchy | iOS diagnostics intent | screenshot + instrumentation | TODO |
| T-40 | Sensitive logging redaction constraints preserved | local-first security guardrail | unit tests | TODO |
| T-41 | Expand unit tests for parser/reducer/runtime fallback/recovery | test plan | passing test suite | IN_PROGRESS |
| T-42 | Expand emulator instrumentation for root/sidebar/timeline/composer/settings | test plan | passing instrumentation suite | TODO |
| T-43 | Keep mock transport deterministic for CI | CI policy | stable test runs | TODO |
| T-44 | Stabilize emulator boot workflow (no adb offline dead loop) | CI reliability | green CI job logs | TODO |
| T-45 | ABI artifact outputs preserved (`arm64-v8a`, `x86_64`, `<version>+<sha6>`) | release policy | CI artifacts | TODO |
| T-46 | Update Android dev notes with durable refactor constraints | continuity policy | docs update | TODO |
| T-47 | Live ADB acceptance on `192.168.31.185:42567` | manual acceptance | runbook logs/screenshots | TODO |
| T-48 | End-to-end desktop response and actionable code-change flow from Android send | product bugfix gate | live local relay test evidence | TODO |

## Current Iteration Evidence

- `TimelineEntry` refactored to typed kinds + metadata (`commandExecution`, `planState`, `subagentAction`, `structuredUserInputRequest`, `deliveryState`, stable order index).
- `RpcTransportParser` upgraded to emit typed kinds and parse command/plan/subagent metadata.
- `CodexService` notification reducer upgraded for typed entries and structured-user-input row creation.
- Parser unit tests extended for kind decoding and command/plan metadata.

## Validation Commands

- `./gradlew -g /Users/yyy/.gradle :app:compileDebugKotlin`
- `./gradlew -g /Users/yyy/.gradle :app:testDebugUnitTest --tests com.remodex.mobile.service.transport.RpcTransportParserTest`
- `./gradlew -g /Users/yyy/.gradle :app:installDebug`
- `bash CodexAndroid/scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9100 --device 192.168.31.185:42567 --skip-build --wait-seconds 80`
