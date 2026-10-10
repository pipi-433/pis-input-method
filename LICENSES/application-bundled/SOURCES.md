# pis'input method sources and notices

This build derives its input framework, key components and candidates from Fcitx5 Android 0.1.3, commit 048f581c652367567b8ee5c28c5163b805288895 (LGPL-2.1-or-later).

Public upstreams and pinned native submodules:

- https://github.com/fcitx5-android/fcitx5-android
- Fcitx5: https://github.com/fcitx/fcitx5 — 16465b04f675105da9fde14e7087984bdc07a146, LGPL-2.1-or-later; Unicode data notices are retained.
- Chinese addons: https://github.com/fcitx/fcitx5-chinese-addons — 9614900b591d180315144b14c529be756fa0e4da, includes LGPL-2.1-or-later and GPL-2.0-or-later components.
- libime: https://github.com/fcitx/libime — 7b638a433815ed7a29d9bcb8d59aed7366bd3b28, and its KenLM submodule; retain their license and source requirements.
- Fcitx5 Lua: https://github.com/fcitx/fcitx5-lua — 05db9ee519d448a64ccbe216044e8e0342e8c536.
- Public prebuilt/data: https://github.com/fcitx5-android/prebuilt — 86ce2c95d42f1132746fbf60c278193aa1f4b758. Native dependencies and input data retain their upstream licenses; source/build checkout is recorded in the project.
- llama.cpp: https://github.com/ggml-org/llama.cpp — 50a6c5cf7c09ea5ea9c1937e4895ce88331c2869, MIT; dependent notices included beside this file.
- Hy-MT2-1.8B-Q4_K_M: https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF — a0c709d9fac510f2c807aa3af52872340dc37a4a, Apache-2.0; SHA256 dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699.

pis modifications: independent visual layout and minimal settings, color palette, height/offset controls, dedicated candidate/translation area, local translation binder/JNI integration and editor-result guards. Release 2.0.0 uses package io.pis.inputmethod and a dedicated pis signing certificate. The interactive input test activity and migration probes are debug-only.

The pis word-frequency extension is recorded in patches/pis-word-frequency.patch against the pinned Fcitx Chinese addons source. It adds an engine-thread config endpoint for actual user-dictionary/history counters and custom-phrase priorities; the LGPL source and change record are retained. The management UI, reference numeric layout and interactive testing page are pis additions.

Models and additional components:

Experimental English definitions (debug builds only): WordNet 3.1, copyright 2011 Princeton University. Downloaded from https://wordnetcode.princeton.edu/wn3.1.dict.tar.gz, archive SHA256 3f7d8be8ef6ecc7167d39b10d66954ec734280b5bdcd57f7d9eafe429d11c22a. tools/prepare_wordnet.py converts official lexical data into an indexed read-only SQLite database with definitions, synonyms, examples and morphology exceptions. The complete original license from the data header is preserved in generated pis-licenses/WordNet-3.1.txt alongside the debug asset. Database SHA256 0e00d480d330af22638e89bb752d62addb6fda6c2d7a8738ae847dd7480194ae; 117791 synsets, 207235 lexical senses. Release 2.0.0 omits this unused database: candidate hints and long-press meanings use gloss.db.

- sherpa-onnx v1.13.8, https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8; public Android AAR SHA256 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96. Apache-2.0; retain sherpa-onnx copyright and dependent ONNX Runtime MIT notice.
- Supplementary source audit (2026-10-10): this AAR contains eSpeak NG / Piper TTS implementations even though pis exposes recognition/punctuation only. eSpeak NG fork ed530aa113046142eb5115cf2fc9157854d0ffe1 carries GPLv3 and file-level exceptions; Piper Phonemize f3ff95afc03640bc1399e113e83361192a2fafb4 is MIT with dependent notices. ONNX Runtime is 1.28.2, with its original ThirdPartyNotices. Corresponding source and component notices are retained. See the repository's LICENSING.md, THIRD_PARTY_NOTICES.md and LICENSES/. Release documentation and APK checksums are recorded in CHANGES.md.
- Streaming bilingual Zipformer small, zh/en 2023-02-16: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2. Encoder int8, decoder, joiner and tokens are downloaded from the public archive and verified by tools/prepare_voice.py.
- CT Transformer zh/en punctuation int8, 2024-04-12: https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8.tar.bz2. Public sherpa-onnx punctuation asset; see the upstream model documentation and Apache-2.0 text. Model checksums are pinned in tools/prepare_voice.py.
- local-pinyin.tsv is generated from pypinyin data (MIT, mozillazg and contributors); notice included. common-hotwords.tsv is our manually selected 240-word list.
- pis voice control, candidate policy and personal vocabulary storage are our authored additions ported to this new application. No recording files are saved; only user-entered vocabulary, correction rules and accepted personal-word counts are stored. Voice personal vocabulary is excluded from Android cloud backup.

Applicable license texts, copyright notices, native source and build/change information are provided in the repository and release materials.
