# Android iOS/Web Parity Program Board (Canonical)

Source of truth:
- iOS app source: `CodexMobile/CodexMobile`
- Web claims: `https://www.phodex.app/`

## Program Board Schema
- `Program ID`: `P-001...P-125`
- `Milestone`: `M1...M12`
- `Target`: concise implementation target
- `Owner`: `shell/sidebar/timeline/composer/service/settings/ci`
- `Acceptance evidence`: test/screenshot/log artifact path
- `Status`: `TODO | IN_PROGRESS | DONE | BLOCKED`
- `Depends on`: prerequisite IDs
- `Regression guard`: invariant ID from `android-dev-notes.md`

## Execution Rules
1. No milestone closes without: `compile + unit + targeted instrumentation + live ADB evidence`.
2. No target flips to `DONE` without artifact references in `Acceptance evidence`.
3. Any runtime/reconnect/send bug fix must add or update an invariant in `android-dev-notes.md`.
4. Local-first is mandatory; no hosted-domain default behavior may be introduced.
5. Tracker completion means **all `P-001..P-125` are `DONE`**.

## Milestone Target Definitions

### M1 (P-001..P-010) Foundation Replacement
- Feature contracts for shell/sidebar/timeline/composer/settings.
- Extend action-contract pattern beyond workspace.
- Split `CodexService` internals into delegate files.
- Retire superseded helper paths.
- Gate: compile + unit green, no behavior regression.

### M2 (P-011..P-020) Root Flow and App Shell
- Normalize root gates to iOS ordering.
- Keep settings reachable while connected.
- Rebuild shell ownership of sidebar/content/modals.
- Prevent scanner/manual pairing race with auto-reconnect.
- Safe-insets + system bar theme parity.

### M3 (P-021..P-032) Sidebar Parity
- Sidebar composition, grouping, row hierarchy, badges.
- Project-aware new chat + metadata search parity.
- Repo isolation thread-scoped; no global repo filter.
- Gate: sidebar instrumentation + screenshot parity.

### M4 (P-033..P-042) Typed Timeline Domain
- Complete typed model kinds and metadata payloads.
- Stable ordering and delivery states.
- Backward-compatible parsing aliases.

### M5 (P-043..P-054) Incoming Reducer
- Item-aware id+kind merge semantics.
- In-place reconciliation for command/tool/file/plan/reasoning/subagent/input.
- Preserve turn fallback when turn id is missing.

### M6 (P-055..P-067) Timeline Rendering
- Render by typed kind only.
- iOS parity for bubble/prose/thinking/command/tool/file/plan/input/subagent rows.
- Long-content compact mode + active-turn anchor behavior.

### M7 (P-068..P-080) Composer and Runtime Controls
- Split host/view/panel state.
- Full autocomplete parity (`@files/$skills//commands`).
- Queue/send-stop semantics parity.
- Runtime injection into `turn/start` and send dedupe.

### M8 (P-081..P-088) Thread/Turn Lifecycle
- Align start/resume/fork and start/steer/interrupt behavior.
- Continuation fallback for missing stale thread.
- Reconnect running-state hydration parity.

### M9 (P-089..P-096) Git/Review/Worktree/CI
- Toolbar action parity.
- Repo-bound git and repo-bound CI visibility only.
- Hide CI row when no repo binding.

### M10 (P-097..P-102) Desktop Sync and Bridge
- Mobile send must progress on desktop thread.
- No route dancing.
- Runtime context in outbound payloads.

### M11 (P-103..P-111) Settings/Notifications/Diagnostics
- iOS settings hierarchy parity.
- Dark mode contrast parity.
- Notification category toggles + pinned status policy.
- Logger hierarchy + redaction/SQLite bounds.

### M12 (P-112..P-125) CI/Test Hardening
- Expand unit + instrumentation coverage for parity-critical flows.
- Deterministic mocked transport in emulator tests.
- Stabilize emulator boot and artifact policy.

## Program Ledger

| Program ID | Milestone | Target | Owner | Acceptance evidence | Status | Depends on | Regression guard |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P-001 | M1 | Seeded parity target #001 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-002 | M1 | Seeded parity target #002 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-003 | M1 | Seeded parity target #003 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-004 | M1 | Seeded parity target #004 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-005 | M1 | Seeded parity target #005 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-006 | M1 | Seeded parity target #006 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-007 | M1 | Seeded parity target #007 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-008 | M1 | Seeded parity target #008 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-009 | M1 | Seeded parity target #009 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-010 | M1 | Seeded parity target #010 (see milestone definition) | service | _TBD_ | TODO | - | I-001 |
| P-011 | M2 | Seeded parity target #011 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-012 | M2 | Seeded parity target #012 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-013 | M2 | Seeded parity target #013 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-014 | M2 | Seeded parity target #014 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-015 | M2 | Seeded parity target #015 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-016 | M2 | Seeded parity target #016 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-017 | M2 | Seeded parity target #017 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-018 | M2 | Seeded parity target #018 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-019 | M2 | Seeded parity target #019 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-020 | M2 | Seeded parity target #020 (see milestone definition) | shell | _TBD_ | TODO | P-001 | I-006 |
| P-021 | M3 | Seeded parity target #021 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-022 | M3 | Seeded parity target #022 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-023 | M3 | Seeded parity target #023 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-024 | M3 | Seeded parity target #024 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-025 | M3 | Seeded parity target #025 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-026 | M3 | Seeded parity target #026 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-027 | M3 | Seeded parity target #027 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-028 | M3 | Seeded parity target #028 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-029 | M3 | Seeded parity target #029 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-030 | M3 | Seeded parity target #030 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-031 | M3 | Seeded parity target #031 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-032 | M3 | Seeded parity target #032 (see milestone definition) | sidebar | _TBD_ | TODO | P-011 | I-005 |
| P-033 | M4 | Seeded parity target #033 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-034 | M4 | Seeded parity target #034 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-035 | M4 | Seeded parity target #035 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-036 | M4 | Seeded parity target #036 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-037 | M4 | Seeded parity target #037 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-038 | M4 | Seeded parity target #038 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-039 | M4 | Seeded parity target #039 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-040 | M4 | Seeded parity target #040 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-041 | M4 | Seeded parity target #041 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-042 | M4 | Seeded parity target #042 (see milestone definition) | timeline | _TBD_ | TODO | P-021 | I-003 |
| P-043 | M5 | Seeded parity target #043 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-044 | M5 | Seeded parity target #044 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-045 | M5 | Seeded parity target #045 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-046 | M5 | Seeded parity target #046 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-047 | M5 | Seeded parity target #047 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-048 | M5 | Seeded parity target #048 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-049 | M5 | Seeded parity target #049 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-050 | M5 | Seeded parity target #050 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-051 | M5 | Seeded parity target #051 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-052 | M5 | Seeded parity target #052 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-053 | M5 | Seeded parity target #053 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-054 | M5 | Seeded parity target #054 (see milestone definition) | timeline | _TBD_ | TODO | P-033 | I-003 |
| P-055 | M6 | Seeded parity target #055 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-056 | M6 | Seeded parity target #056 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-057 | M6 | Seeded parity target #057 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-058 | M6 | Seeded parity target #058 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-059 | M6 | Seeded parity target #059 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-060 | M6 | Seeded parity target #060 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-061 | M6 | Seeded parity target #061 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-062 | M6 | Seeded parity target #062 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-063 | M6 | Seeded parity target #063 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-064 | M6 | Seeded parity target #064 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-065 | M6 | Seeded parity target #065 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-066 | M6 | Seeded parity target #066 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-067 | M6 | Seeded parity target #067 (see milestone definition) | timeline | _TBD_ | TODO | P-043 | I-003 |
| P-068 | M7 | Seeded parity target #068 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-069 | M7 | Seeded parity target #069 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-070 | M7 | Seeded parity target #070 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-071 | M7 | Seeded parity target #071 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-072 | M7 | Seeded parity target #072 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-073 | M7 | Seeded parity target #073 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-074 | M7 | Seeded parity target #074 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-075 | M7 | Seeded parity target #075 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-076 | M7 | Seeded parity target #076 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-077 | M7 | Seeded parity target #077 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-078 | M7 | Seeded parity target #078 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-079 | M7 | Seeded parity target #079 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-080 | M7 | Seeded parity target #080 (see milestone definition) | composer | _TBD_ | TODO | P-055 | I-007 |
| P-081 | M8 | Seeded parity target #081 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-082 | M8 | Seeded parity target #082 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-083 | M8 | Seeded parity target #083 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-084 | M8 | Seeded parity target #084 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-085 | M8 | Seeded parity target #085 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-086 | M8 | Seeded parity target #086 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-087 | M8 | Seeded parity target #087 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-088 | M8 | Seeded parity target #088 (see milestone definition) | service | _TBD_ | TODO | P-068 | I-002 |
| P-089 | M9 | Seeded parity target #089 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-090 | M9 | Seeded parity target #090 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-091 | M9 | Seeded parity target #091 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-092 | M9 | Seeded parity target #092 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-093 | M9 | Seeded parity target #093 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-094 | M9 | Seeded parity target #094 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-095 | M9 | Seeded parity target #095 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-096 | M9 | Seeded parity target #096 (see milestone definition) | service | _TBD_ | TODO | P-081 | I-005 |
| P-097 | M10 | Seeded parity target #097 (see milestone definition) | service | _TBD_ | TODO | P-089 | I-004 |
| P-098 | M10 | Seeded parity target #098 (see milestone definition) | service | _TBD_ | TODO | P-089 | I-004 |
| P-099 | M10 | Seeded parity target #099 (see milestone definition) | service | _TBD_ | TODO | P-089 | I-004 |
| P-100 | M10 | Seeded parity target #100 (see milestone definition) | service | _TBD_ | TODO | P-089 | I-004 |
| P-101 | M10 | Seeded parity target #101 (see milestone definition) | service | _TBD_ | TODO | P-089 | I-004 |
| P-102 | M10 | Seeded parity target #102 (see milestone definition) | service | _TBD_ | TODO | P-089 | I-004 |
| P-103 | M11 | Seeded parity target #103 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-104 | M11 | Seeded parity target #104 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-105 | M11 | Seeded parity target #105 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-106 | M11 | Seeded parity target #106 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-107 | M11 | Seeded parity target #107 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-108 | M11 | Seeded parity target #108 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-109 | M11 | Seeded parity target #109 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-110 | M11 | Seeded parity target #110 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-111 | M11 | Seeded parity target #111 (see milestone definition) | settings | _TBD_ | TODO | P-097 | I-006 |
| P-112 | M12 | Seeded parity target #112 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-113 | M12 | Seeded parity target #113 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-114 | M12 | Seeded parity target #114 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-115 | M12 | Seeded parity target #115 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-116 | M12 | Seeded parity target #116 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-117 | M12 | Seeded parity target #117 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-118 | M12 | Seeded parity target #118 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-119 | M12 | Seeded parity target #119 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-120 | M12 | Seeded parity target #120 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-121 | M12 | Seeded parity target #121 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-122 | M12 | Seeded parity target #122 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-123 | M12 | Seeded parity target #123 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-124 | M12 | Seeded parity target #124 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |
| P-125 | M12 | Seeded parity target #125 (see milestone definition) | ci | _TBD_ | TODO | P-103 | I-004 |

## Current Iteration Evidence
- Workspace action-contract boundary landed (`WorkspaceActionHandler`).
- Root shell wiring uses handler in `RemodexApp`/`WorkspaceScreen`.
- Scripted UI harness adapted to action contract.
- Live pairing monitor on `192.168.31.185:42567`: no `route_dance_detected` in acceptance window.

## Validation Commands
- `./gradlew -g /Users/yyy/.gradle :app:compileDebugKotlin`
- `./gradlew -g /Users/yyy/.gradle :app:testDebugUnitTest`
- `./gradlew -g /Users/yyy/.gradle :app:installDebug`
- `bash CodexAndroid/scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9100 --device 192.168.31.185:42567 --skip-build --wait-seconds 90 --monitor-refresh-seconds 20`
