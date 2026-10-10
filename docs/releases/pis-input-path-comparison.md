# Candidate updates and input dispatch

2026-10-09. Implementation notes for `pis-v2-stable-candidates`.

## Candidate rendering

- Skip identical updates. When the count stays the same, notify only changed items.
- Read synchronized LruCache hints before starting an asynchronous query; reuse cached text immediately.
- Reserve hint width from the main word (48dp minimum, 144dp maximum). Long hints ellipsize within the reserved width.
- Skip redundant setText calls and reject stale query results with generation checks.

These changes reduce blank hint redraws and repeated layout passes.

## Input dispatch

Chinese input runs through the FcitxDispatcher/event-loop queue, native sendKey and preedit/candidate events. `JOB_WAITING_LIMIT=2000` is a diagnostic threshold.

Subsequent pointer ordering and delete dispatch changes are recorded in [pis-follow-input.md](pis-follow-input.md).

## Verification

The x86_64 build passed in 76 seconds, was installed on emulator-5556 and opened the input activity. Verified: build and installation. Follow-up verification items: same-device phone latency and UI regression.

## Source pointers

`CustomGestureView.kt`, `BaseKeyboard.kt`, `FcitxInputMethodService.kt`, `FcitxDispatcher.kt`, `HorizontalCandidateViewAdapter.kt`, `CandidateItemUi.kt` and `PisGlossary.kt`.
