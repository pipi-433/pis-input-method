# Voice editing and operation vocabulary

2026-10-09. ARM64 and x86_64 builds are available.

Current recognition: sherpa-onnx 1.13.8, offline bilingual zh/en Zipformer streaming transducer (16kHz mono), modified_beam_search with four active paths and hotword bias; local punctuation plus candidate correction. No cloud recognition or second audio pass. Model weights are unchanged.

Editing root cause: draft text was inserted as one Android composing region. When cursor/text moved, EditorSnapshot no longer matched; cancel(false) cleaned up the session without finishComposingText. The stale whole-sentence region could then be replaced by the next pinyin composition. Fix: when a voice draft still belongs to the current InputConnection, always finish its composition on cancel; restore original selection only for an explicit erase with a matching snapshot. Accepted voice commits explicitly finish composition. Selection updates cancel voice ownership when the real editor snapshot changes, preserving text and selection and rejecting late callbacks/candidate timeouts.

Accuracy changes: add conservative computer-operation hotwords right-click/left-click/double-click/delete/copy/paste/cut/undo. When common words and correction candidates are enabled, narrowly correct 又见/右见/又键 only when immediately followed by an unambiguous operation verb such as 删除/复制/菜单. Prefer that contextual candidate and retain raw recognition beside it. Ordinary 又见到/又见面 is unchanged. Personal aliases, number protection, punctuation guards and explicit settings remain. Overall ASR accuracy measurements are pending.

Version pis-v2-voice-edit-fix. x86_64 build passed in 48s and emulator install succeeded. ARM64 build passed in 37s. Phone package D:\py\to1\voice_work\dist\pis-input-method-v2-voice-edit-fix-arm64.apk; bytes 1351168647; SHA256 557695df773bd8dee85f3fef8ba02d2459c17a4b9eea3ba8051595ccd1abdba5. Signature, ABI, model/dictionary/voice resources and 16KB package checks passed. Latest phone package has been rebuilt from current source and copied to dist.

Verification covered builds, emulator installation and package checks; UI, phone runtime and audio tests remain pending.
