# Fast input, embedded chat pinyin and larger output bar

2026-10-09. Input dispatch and candidate update refinements.

Embedded pinyin fix: PreeditMode must be the configured Fcitx enum name Composing pinyin, not the C++ identifier ComposingPinyin. Signature embedded-pinyin-v2 forces reapplication. ClientPreeditEvent already calls setComposingText with underline; selecting candidates replaces the composing region. The external preedit view stays hidden.

Phone readability: output bar 56dp -> 64dp; Chinese candidates 16sp -> 18sp; English candidates 18sp -> 19sp; hint 10sp -> 11sp; full translation/voice text 16sp -> 18sp. Candidate line slots become 18dp/32dp. Letter key baseline and key height stay unchanged.

Input dispatch: no fixed double-tap wait was found on ACTION_UP. Keep release-to-type and all long-press/swipe/repeat handling. Move release haptic behind click dispatch, queue actual input before decorative feedback, bypass voice bar renders when idle, bypass translation IPC/layout when idle while retaining generation invalidation and matching cancellation tokens. Candidate hints query only gloss.db bilingual data, with no full WordNet definition lookup.

These changes reduce work in the source input path. Phone APK and emulator version are pis-v2-fast-input. Verification items pending for this stage: build/package results, Vivo latency, UI and phone runtime.
