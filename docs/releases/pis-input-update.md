# Input update

2026-10-09. Final combined version: pis-v2-input-update.

Includes the fixes recorded in pis-fast-input.md: correct Composing pinyin enum value and embedded-pinyin-v2 signature; 64dp top bar; candidates 18sp Chinese/19sp English, hints 11sp, full translation/voice 18sp; input queued before decorative feedback; no idle voice/translation bar rebuilds; release haptic behind click; gloss.db short hint queries. Letter key layout and all gestures remain unchanged.

Repeated delete feedback previously clamped the position toward column zero and drew a destination marker throughout the animation. Repeated events now wrap at the visible left half-column back to column eleven. The endpoint marker is only shown when a sweep has completed. This keeps repeated delete moving right-to-left across successive laps instead of retaining a permanently lit left point. A single animator still handles each event; no animation queue or background loop is added.

ARM64 assembleDebug passed in 39s. Phone APK: D:\py\to1\voice_work\dist\pis-input-method-v2-input-update-arm64.apk. Signature, asset hashes and 16KB package checks passed. SHA256: 33bb6ce680d1f8d8c8ecfecfc2ac4b825cfa6a632b987b1294a2b628b65a8052. Follow-up items for this stage: emulator sync, UI, phone runtime and latency measurements.
