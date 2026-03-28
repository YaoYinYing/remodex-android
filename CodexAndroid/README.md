# Remodex Android

Native Android client foundation for full-parity Remodex delivery.

## Current scope

- Kotlin + Compose app shell with iOS-style gate flow:
  - Onboarding
  - Silent dev paywall gate
  - Pairing/connect login
  - Home empty state
  - Turn-first workspace shell
- Core JSON-RPC model types
- Secure transport transcript/nonce parity utilities
- Golden-vector unit tests aligned with bridge protocol vectors
- Push registration model including `platform` + `pushProvider`
- Thread list + timeline rendering surface with parser compatibility for `thread/list` and `thread/read`
- Fixture-backed RPC transport for on-device parity UI testing
- Live secure relay transport skeleton (WebSocket + secure handshake + encrypted envelope plumbing)
- Composer `turn/start` and `notifications/push/register` RPC flows wired from UI -> service -> transport
- Persisted pairing state (local SharedPreferences) loaded on app restart
- Git action surface for `git/status`, `git/branches`, and `git/checkout`
- Thread creation (`thread/start`) and active-turn interruption (`turn/interrupt`)
- Reconnect/disconnect controls with retained live/fixture mode selection
- Expanded git controls for `git/pull`, `git/push`, and `git/commit`
- iOS-matched design-token layer:
  - Bundled font families from iOS app (`Geist`, `Geist Mono`, `JetBrains Mono`)
  - Runtime font style selection (System / Geist / Geist Mono / JetBrains Mono)
  - Theme tone modes (System / Light / Dark)
  - Shared color roles for plan/command/status surfaces
- Structured workflow shell:
  - Login page (`pairing -> connect`) with saved local pairing metadata
  - Working page (`current project`, `chat`, `code changes`, `permission granting`, `model switching`, `branch selection`)
  - Side slide drawer (`task list`, `iOS component parity checklist`, `settings`, `git actions`, `disconnect`, `rate-limit` + `CI` info)
- Press-and-slide-down gesture strip to force workspace refresh
- Android notification pipeline for status changes, permission requirements, rate-limit hits, git actions, and CI/CD updates
- Pinned website-derived TODO lane in-app, advanced sequentially (`DONE`/`IN_PROGRESS`/`TODO`)
- Parity gate document: [`docs/parity-acceptance-matrix.md`](docs/parity-acceptance-matrix.md)
- Compose UI instrumentation suite for parity shells and gesture interactions
- Lower-latency fixture transport simulation and periodic background sync loop to reduce perceived transport delay

## Parity gate status

1. `TODO-01` through `TODO-20` are marked `DONE` in parity models and acceptance matrix
2. Live local relay pairing/injection is validated with `CodexAndroid/scripts/live_local_pairing_test.sh`
3. Logger SQLite persistence and sensitive-redaction checks are validated with `CodexAndroid/scripts/logger_db_self_test.sh`
4. Silent dev paywall remains in place without public pricing copy

## Validation commands

```bash
cd CodexAndroid
./gradlew -g /tmp/gradle-home :app:assembleDebug :app:testDebugUnitTest
./gradlew -g /tmp/gradle-home :app:assembleAndroidTest
/Users/yyy/adb/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/yyy/adb/adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/Users/yyy/adb/adb shell am instrument -w com.remodex.mobile.test/androidx.test.runner.AndroidJUnitRunner
bash ./scripts/live_local_pairing_test.sh --hostname 192.168.31.138 --port 9100 --device 192.168.31.185:38563 --wait-seconds 70 --skip-build
bash ./scripts/logger_db_self_test.sh --device 192.168.31.185:38563
```
