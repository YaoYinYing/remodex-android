# Android Parity Acceptance Matrix

This matrix is the release gate for Android iOS-parity work.  
Every row must be verified before the iteration is considered complete.

## Gate Status

- `TODO-01` to `TODO-20`: `DONE` (validated on 2026-03-28)

| TODO | Scope | Verification | Evidence |
| --- | --- | --- | --- |
| TODO-01 | Root flow parity | Instrumentation | App opens through onboarding/paywall/pairing/home in the iOS order |
| TODO-02 | Onboarding parity | Instrumentation | Five onboarding pages render with the same copy and CTA progression |
| TODO-03 | Silent paywall gate | Instrumentation | Dev gate is present without public pricing or release copy |
| TODO-04 | QR pairing scanner | Instrumentation + live ADB | QR scan, bridge-update handling, and resume behavior work |
| TODO-05 | Home empty state | Screenshot + instrumentation | Empty shell shows logo, connection capsule, and reconnect CTA |
| TODO-06 | Sidebar thread browser | Screenshot + instrumentation | Project grouping, archived section, rename/archive/delete actions work |
| TODO-07 | Timeline parity | Unit + screenshot | User/assistant/command/plan/reasoning items render in the correct form |
| TODO-08 | Composer runtime parity | Instrumentation | Runtime controls, queue controls, and send/stop behavior match iOS intent |
| TODO-09 | @files/$skills//commands | Instrumentation | Mention chips and autocomplete panels appear and resolve correctly |
| TODO-10 | Git actions parity | Unit + live ADB | Status, branches, checkout, commit, pull, and push work in a repo-bound thread |
| TODO-11 | Thread lifecycle parity | Unit + live ADB | Start, resume, fork, interrupt, archive, unarchive, and delete flows persist correctly |
| TODO-12 | Task steering + plan/review | Unit + instrumentation | Plan/review/steer controls route into the same workflow semantics as iOS |
| TODO-13 | Voice + media parity | Instrumentation + live ADB | Camera/gallery uploads and voice transcription complete end-to-end |
| TODO-14 | Notifications parity | Unit + live ADB | Status, permission, rate-limit, git, and CI/CD alerts are emitted |
| TODO-15 | Settings parity | Instrumentation | Font, tone, runtime defaults, and connection controls match iOS |
| TODO-16 | Branding + visual polish | Screenshot comparison | Remodex name/icon, typography, spacing, and motion match shipped iOS assets |
| TODO-17 | Transport recovery | Live ADB soak | Reconnect/retry does not wedge on socket send failure or timeout loops |
| TODO-18 | Event-stream ingestion | Unit + live ADB | Incoming notification/delta events update the active turn state |
| TODO-19 | Live ADB acceptance | Live ADB | APK install, pair, and living workflow matrix succeed on a physical device |
| TODO-20 | Final gate closure | Release gate | All TODOs are `DONE`, evidence is attached, and final commit/push is complete |

## Evidence Pack

- `unit`: `./gradlew -g /tmp/gradle-home :app:testDebugUnitTest` (pass)
- `build`: `./gradlew -g /tmp/gradle-home :app:assembleDebug` (pass)
- `live ADB`: `bash CodexAndroid/scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9100 --device 192.168.31.185:38563 --wait-seconds 70 --skip-build` (pass)
- `logger DB`: `bash CodexAndroid/scripts/logger_db_self_test.sh --device 192.168.31.185:38563` (pass, redaction checks passed)
- `UI evidence`: `/tmp/window_dump.xml` confirms active `com.remodex.mobile` Compose hierarchy in workspace flow
