# Pinyin composing text inside the chat editor

2026-10-09.

Root cause: PisInputOptions wrote PreeditMode=ComposingPinyin, the C++ identifier. The pinned Fcitx option serializer accepts the configured enum name Composing pinyin. The emulator's persisted pinyin.conf still showed the Android default Do not show, so the previous setting was ineffective while the external preedit panel was hidden.

Fix: write Composing pinyin and bump the configuration signature to embedded-pinyin-v2. Keep PreeditCursorPositionAtBeginning=False. ClientPreeditEvent already calls InputConnection.setComposingText with underline spans; selecting a Chinese candidate replaces the composing text through the existing commit path. The composing region remains owned by the editor, and the external preedit view stays hidden.

Targets: ARM64 phone APK and x86_64 emulator build, version pis-v2-chat-pinyin. Other features and appearance remain unchanged. Verification items: build/package results, UI and phone runtime; results are pending for this stage.
