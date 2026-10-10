# Touch ordering and delete dispatch

2026-10-10. Version pis-v2-follow-input.

1. BaseKeyboard tracks active key views in press order. Before a second valid keyboard pointer is dispatched, finish earlier eligible ordinary keys. The gesture layer marks early release, cancels pending long/repeat timers, dismisses previews and invokes the click once. Later physical release/moves are consumed. Caps, language/layout/pickers and already-triggered holds/popups/swipes are excluded.
2. BackspaceKey emits its initial click on DOWN after sweep setup. A consumed-down flag prevents duplicate UP deletion. The existing long-press delay and 50ms repeat interval remain. A subsequent pointer ends an older delete-repeat/sweep before new input.
3. Pointer ownership is retained through native split touch events and the optional vivo workaround. Transformed events preserve pointer ID/properties/source/flags and are recycled. Older releases cannot end a new same-key press. Global cancel clears every mapped target, and detach cancels pending timers.

No input on DOWN for ordinary letters, no new click delay, no engine rewrite, no Shift semantics replacement. Previous candidate stability, pinyin, voice editing, appearance and gestures remain.

X86_64 build passed in 80s. ARM64 build passed in 31s. Package signature, ABI, assets and 16KB alignment passed. Follow-up verification items: UI, audio, phone overlap/repeat behavior and input latency.

Phone file: D:\py\to1\voice_work\dist\pis-input-method-v2-follow-input-arm64.apk
Bytes: 1351172531
SHA256: 8221230fe0506d078081793514ed34c7f5eea91f2b26c18606c5b053dfbe5eab
Emulator cold-booted and updated successfully; installed version pis-v2-follow-input confirmed, input activity opened. X86 output was regenerated in 33s after the ABI build replaced the prior output file.
