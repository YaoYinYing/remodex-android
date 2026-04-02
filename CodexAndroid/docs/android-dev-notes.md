# Android Dev Notes

Purpose: keep Android-specific implementation constraints and known-good behaviors durable across chat resets and context compression.

## Source of truth

- Mirror iOS behavior first. Use the iOS turn/timeline/composer implementation as the product source of truth before inventing Android-specific behavior.
- For current parity scope, the canonical checklist is [ios-web-parity-todo.md](/Users/yyy/Documents/protein_design/remodex/CodexAndroid/docs/ios-web-parity-todo.md).
- Prefer shared/service fixes over view-local workarounds.
- If the user explicitly asks to finish with commit and push, do not stop at implementation or validation; carry the change through commit and `git push`.

## Runtime and transport

- Keep local-first behavior. Prefer saved QR pairing, local bridge, local relay, and explicit user-configured hosts over any hosted default.
- `turn/started` may not contain a usable `turnId`. Keep per-thread running fallbacks on Android just like iOS.
- If Stop is requested and there is no `activeTurnIdByThread`, resolve via `thread/read` before interrupting.
- Rehydrate active turn state on reconnect/background recovery so the composer still shows Stop for running turns.
- Do not drop timeline deltas just because a thread is not currently selected. Background conversations must continue accumulating output.
- Keep item-aware timeline reconciliation. Do not flatten assistant/system items into one text blob.
- Merge late reasoning deltas into existing reasoning rows instead of creating fake extra thinking rows.
- Ignore late turn-less activity events once the turn is already inactive.

## Pairing, reconnect, and local relay

- Saved relay pairing is the source of truth during reconnect. Do not clear saved pairing too early.
- Preserve scanner -> saved pairing -> connect flow. Do not let onboarding or auto-reconnect race manual scan control.
- Keep reconnect behavior across relaunch when the local host session is still valid.
- Do not log live relay `sessionId` values or other bearer-like pairing identifiers in bridge/server logs.
- Local launcher command that should keep working:

```bash
bash ./run-local-remodex.sh --hostname 192.168.31.138 --port 9000
```

- Current local relay defaults that are intentional:
  - refresh enabled for local source checkout
  - quieter desktop refresh timings
  - pairing TTL configurable by env

## Bridge and desktop refresh

- The desktop refresh path is bridge-owned. If Android messages reach Codex but desktop UI does not update, inspect `phodex-bridge/src/codex-desktop-refresher.js` first.
- Do not reopen `codex://threads/new` for phone-originated `turn/start` without a concrete `threadId`.
- Refresh desktop when the concrete thread is known, especially on outbound `turn/started`.
- Keep refresh behavior conservative. Aggressive rollout-growth refresh loops cause desktop bounce/reopen noise.

## Rate limits and account state

- Android should prefer the iOS-compatible `account/rateLimits/read` RPC first.
- The bridge owns compatibility for rate-limit reads. If Android only shows "unavailable", inspect `phodex-bridge/src/bridge.js` before changing UI.
- Render rate limits in human-readable summaries. Do not expose raw timestamps/epochs in the main chip.
- Keep bridge-managed account state and bridge-version refresh separate, as upstream iOS does.

## UI and parity notes

- Conversation UI should follow iOS structure, not generic Android cards.
- Current intended timeline split:
  - user: right-aligned rounded bubble
  - assistant: plain prose block without heavy card chrome
  - thinking/tool activity: subdued inline system rows
  - command/file-change/plan: compact system cards with collapse/disclosure
- Avoid noisy type labels like `assistant • agentmessage` in the main timeline.
- Keep assistant rows breathable with looser spacing similar to iOS `TurnTimelineView`.
- Header rules:
  - thread title is primary
  - subtitle is connection/git summary
  - only marquee real thread titles, not static `Remodex`
  - do not pre-mangle status text with aggressive clipping before Compose ellipsis
- Settings is a dedicated screen reachable even while connected.
- Disconnect should return to the pairing/QR route, not leave the user in the workspace shell.

## Composer and conversation behavior

- Model selection is local runtime state and should be injected into `turn/start`; do not depend on a dedicated model-switch RPC.
- Keep send feedback explicit. Use an in-flight send state so duplicate taps do not create duplicate turns.
- If Android dispatches but Codex desktop does not react, separate the problem:
  - transport/send path
  - thread recovery / `turn/start`
  - bridge refresher / desktop routing
- Leaving a running conversation must not make it appear stopped. Background updates should still land in that thread cache.

## Git and CI

- Git actions are thread/repo scoped, not global app scoped.
- CI status should only appear for a thread with a real repo binding.
- Public GitHub/GitLab CI polling is supplemental only. Local bridge/repo state remains primary.

## Device testing notes

- Preferred validation path:
  - `./gradlew -g /Users/yyy/.gradle :app:assembleDebug`
  - install with ADB
  - verify real device behavior on the paired local relay
- The test device can have multiple displays. `adb exec-out screencap` is unreliable there.
- Safer screenshot path:

```bash
/Users/yyy/adb/adb -s <device> shell 'screencap -p /sdcard/DCIM/remodex.png'
/Users/yyy/adb/adb pull /sdcard/DCIM/remodex.png /tmp/remodex.png
```

- Some captured PNGs may have malformed chunk tables even though the header is valid. If needed, reopen and resave locally before inspection.
- The device IME may default to Gboard Chinese pinyin. Do not assume Enter sends; use the explicit send button path when validating composer behavior.

## Known anti-patterns

- Do not patch over Android parity issues with extra placeholder cards or duplicate status labels.
- Do not move protocol decisions into Compose views.
- Do not hardcode production/hosted endpoints.
- Do not regress local relay recovery just to simplify reconnect logic.
