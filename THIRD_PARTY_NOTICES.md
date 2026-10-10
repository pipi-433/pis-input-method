# 第三方声明：pis 2.0.0

这些资料对应 ARM64 Release 2.0.0（APK SHA256 `b711c4c76920669c0dea7d1aba7021687c96a99fd90e7c38710ec40a8e84d56c`），于 2026-10-10 整理。完整原始文本见 [LICENSES/](LICENSES/)，许可范围见 [LICENSING.md](LICENSING.md)。

## 应用及引擎

Fcitx5 Android 0.1.3、Fcitx5 5.1.21、Chinese addons 5.1.13、libime 1.1.15、Fcitx5 Lua 5.0.17 来自各自固定公开仓库。原始版权（Fcitx5 Android / Fcitx contributors）与文件级 SPDX 声明保留；许可包括 LGPL-2.1-or-later、GPL-2.0-or-later 及 Unicode 等个别数据声明。KenLM 以 LGPL 2.1 或后续版为主，但有独立 BSD/其他文件例外；Yoga 的原 MIT 版权（Facebook, Inc. and affiliates）保留。固定提交见 [源码仓库记录](docs/compliance/SOURCE_REPOSITORIES.json)，完整源码与改动随本地快照保存。

静态预编译依赖包括 Boost 1.90.0（BSL-1.0）、libintl-lite edbb306（BSL-1.0）、libuv 1.52.0（MIT 与附加通知）、Lua 5.4.7（MIT）、zstd 1.5.7（BSD/GPL 双许可）、OpenCC 1.2.0（Apache-2.0）以及 marisa-trie（BSD/LGPL 双许可，预编译器固定提交 `3e87d53b78e15f2f43783d5e376561a8c9722051`）。具体条款见各组件原始许可证；NDK libc++/LLVM 的许可证、异常和附加通知见 `LICENSES/components/android-ndk/`。

## 新增翻译与语音

| 组件 | 固定版本/来源 | 许可与保存内容 |
|---|---|---|
| llama.cpp | `50a6c5cf7c09ea5ea9c1937e4895ce88331c2869`，ggml-org | MIT，原始 LICENSE 与所带第三方文本；源检出随快照保存 |
| Hy-MT2-1.8B-Q4_K_M | Tencent 模型提交 `a0c709d9fac510f2c807aa3af52872340dc37a4a` | Apache-2.0，Tencent 原模型 LICENSE |
| sherpa-onnx | 1.13.8，k2-fsa；原文件版权包含 Xiaomi 等贡献者 | Apache-2.0，原 LICENSE、固定源码归档及 Android 构建脚本 |
| ONNX Runtime | 1.28.2，Microsoft；由 sherpa 固定 Android 输入使用，APK 二进制版本字符串亦为 1.28.2 | MIT；原 LICENSE、325 KB ThirdPartyNotices 和源码归档 |
| eSpeak NG fork | `ed530aa113046142eb5115cf2fc9157854d0ffe1`，csukuangfj/espeak-ng | GPLv3 及逐文件例外；原 COPYING、COPYING.APACHE/BSD2/UCD 等与完整固定源码归档 |
| Piper Phonemize | `f3ff95afc03640bc1399e113e83361192a2fafb4`，csukuangfj fork / Michael Hansen | MIT 及 uni-algo 原通知，固定源码归档 |
| kaldi-native-fbank / kaldi-decoder | 1.22.3 / 0.3.0 | Apache-2.0，原 LICENSE 和固定源码归档 |
| OpenFst / hclust-cpp / simple-sentencepiece | sherpa 1.13.8 配方的固定版本 | 各组件原始许可证随源码保存 |
| pypinyin 数据 | 已打包的 local-pinyin.tsv 来源 | MIT，mozillazg 与相关贡献者原版权文本保留 |

pis 提供语音识别和标点。官方 AAR 的 `libsherpa-onnx-c-api.so`、`libsherpa-onnx-jni.so` 包含 eSpeak/Piper 实现，固定 Android 脚本默认启用 TTS；相关 GPLv3/MIT 许可证和对应源码一并保留。许可证适用范围见原源文件和组件声明。

语音 Zipformer、标点模型的上游许可信息与模型哈希见 [MODELS.md](docs/compliance/MODELS.md)。WordNet 3.1 仅调试版使用，其完整许可由 `prepare_wordnet.py` 保留。

## 预编译输入数据

拼音字典、语言模型、表码数据、英语拼写数据、OpenCC 和 pinyinhelper 数据来自固定 prebuilt 仓库，适用各自的许可与版权声明。输入归档名称及校验见 [PREBUILT_DATA_INPUTS.json](docs/compliance/PREBUILT_DATA_INPUTS.json)。构建使用固定 prebuilt 配方记录的词典与语言模型版本。

## Gradle 运行时依赖

以下表来自 Release 构建生成的 AboutLibraries 元数据，共 118 项（108 个 Maven 项、10 个原生项）。本地 AAR 和模型在前文单独列出。POM 和归档内原始 LICENSE/NOTICE 保存在 `docs/compliance/maven-poms/` 与 `LICENSES/maven/`。表中版本为依赖解析结果；实际打包内容由 R8 裁剪结果决定。

| 坐标/组件 | 版本 | 元数据许可证 | 上游 |
|---|---|---|---|
| `BYVoid/OpenCC` | 1.2.0 | Apache-2.0 | https://opencc.byvoid.com/ |
| `androidx.activity:activity` | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity#1.13.0 |
| `androidx.activity:activity-ktx` | 1.13.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity#1.13.0 |
| `androidx.annotation:annotation-experimental` | 1.4.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/annotation#1.4.1 |
| `androidx.annotation:annotation-jvm` | 1.9.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/annotation#1.9.1 |
| `androidx.appcompat:appcompat` | 1.7.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/appcompat#1.7.1 |
| `androidx.appcompat:appcompat-resources` | 1.7.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/appcompat#1.7.1 |
| `androidx.arch.core:core-common` | 2.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/arch-core#2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/arch-core#2.2.0 |
| `androidx.autofill:autofill` | 1.3.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/autofill#1.3.0 |
| `androidx.cardview:cardview` | 1.0.0 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html |
| `androidx.collection:collection-jvm` | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/collection#1.5.0 |
| `androidx.collection:collection-ktx` | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/collection#1.5.0 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.9.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-runtime#1.9.0 |
| `androidx.concurrent:concurrent-futures` | 1.0.0 | Apache-2.0 | https://developer.android.com/topic/libraries/architecture/index.html |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/constraintlayout#2.2.1 |
| `androidx.constraintlayout:constraintlayout-core` | 1.1.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/constraintlayout#1.1.1 |
| `androidx.coordinatorlayout:coordinatorlayout` | 1.3.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/coordinatorlayout#1.3.0 |
| `androidx.core:core` | 1.18.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core#1.18.0 |
| `androidx.core:core-ktx` | 1.18.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core#1.18.0 |
| `androidx.core:core-viewtree` | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core#1.0.0 |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html |
| `androidx.customview:customview` | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/customview#1.2.0 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/customview#1.0.0 |
| `androidx.databinding:viewbinding` | 9.3.1 | Apache-2.0 |  |
| `androidx.drawerlayout:drawerlayout` | 1.1.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| `androidx.dynamicanimation:dynamicanimation` | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/dynamicanimation#1.1.0 |
| `androidx.emoji2:emoji2` | 1.3.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/emoji2#1.3.0 |
| `androidx.emoji2:emoji2-views-helper` | 1.3.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/emoji2#1.3.0 |
| `androidx.exifinterface:exifinterface` | 1.3.7 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/exifinterface#1.3.7 |
| `androidx.fragment:fragment` | 1.6.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/fragment#1.6.2 |
| `androidx.fragment:fragment-ktx` | 1.6.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/fragment#1.6.2 |
| `androidx.graphics:graphics-shapes-android` | 1.0.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/graphics#1.0.1 |
| `androidx.interpolator:interpolator` | 1.0.0 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html |
| `androidx.lifecycle:lifecycle-common-java8` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-livedata` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-livedata-ktx` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-process` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-service` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0 |
| `androidx.loader:loader` | 1.0.0 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html |
| `androidx.navigation:navigation-common-android` | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.9.8 |
| `androidx.navigation:navigation-fragment` | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.9.8 |
| `androidx.navigation:navigation-fragment-ktx` | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.9.8 |
| `androidx.navigation:navigation-runtime-android` | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.9.8 |
| `androidx.navigation:navigation-ui` | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.9.8 |
| `androidx.navigation:navigation-ui-ktx` | 2.9.8 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigation#2.9.8 |
| `androidx.navigationevent:navigationevent-android` | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/navigationevent#1.0.0 |
| `androidx.paging:paging-common-android` | 3.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.0 |
| `androidx.paging:paging-common-ktx` | 3.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.0 |
| `androidx.paging:paging-runtime` | 3.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.0 |
| `androidx.paging:paging-runtime-ktx` | 3.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/paging#3.5.0 |
| `androidx.preference:preference` | 1.2.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/preference#1.2.1 |
| `androidx.preference:preference-ktx` | 1.2.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/preference#1.2.1 |
| `androidx.recyclerview:recyclerview` | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/recyclerview#1.4.0 |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/resourceinspection#1.0.1 |
| `androidx.room:room-common-jvm` | 2.8.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.8.4 |
| `androidx.room:room-ktx` | 2.8.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.8.4 |
| `androidx.room:room-paging-android` | 2.8.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.8.4 |
| `androidx.room:room-runtime-android` | 2.8.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/room#2.8.4 |
| `androidx.savedstate:savedstate-android` | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.4.0 |
| `androidx.savedstate:savedstate-ktx` | 1.4.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/savedstate#1.4.0 |
| `androidx.slidingpanelayout:slidingpanelayout` | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/slidingpanelayout#1.2.0 |
| `androidx.sqlite:sqlite-android` | 2.6.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/sqlite#2.6.2 |
| `androidx.sqlite:sqlite-framework-android` | 2.6.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/sqlite#2.6.2 |
| `androidx.startup:startup-runtime` | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/startup#1.2.0 |
| `androidx.tracing:tracing` | 1.2.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/tracing#1.2.0 |
| `androidx.transition:transition` | 1.5.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/transition#1.5.0 |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html |
| `androidx.viewpager2:viewpager2` | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/viewpager2#1.1.0 |
| `androidx.viewpager:viewpager` | 1.0.0 | Apache-2.0 | http://developer.android.com/tools/extras/support-library.html |
| `androidx.window:window` | 1.0.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/window#1.0.0 |
| `boostorg/boost` | 1.90.0 | BSL-1.0 | https://www.boost.org/ |
| `com.google.android.flexbox:flexbox` | 3.0.0 | Apache-2.0 |  |
| `com.google.android.material:material` | 1.14.0 | Apache-2.0 | https://github.com/material-components/material-components-android |
| `com.google.errorprone:error_prone_annotations` | 2.15.0 | Apache-2.0 | https://errorprone.info/error_prone_annotations |
| `com.google.guava:listenablefuture` | 1.0 | Apache-2.0 | https://github.com/google/guava/listenablefuture |
| `com.jakewharton.timber:timber` | 5.0.1 | Apache-2.0 | https://github.com/JakeWharton/timber |
| `com.louiscad.splitties:splitties-bitflags-jvm` | 3.0.0 | Apache-2.0 | https://github.com/LouisCAD/Splitties |
| `com.louiscad.splitties:splitties-collections-jvm` | 3.0.0 | Apache-2.0 | https://github.com/LouisCAD/Splitties |
| `com.louiscad.splitties:splitties-experimental-jvm` | 3.0.0 | Apache-2.0 | https://github.com/LouisCAD/Splitties |
| `com.mikepenz:aboutlibraries-core-android` | 14.2.1 | Apache-2.0 | https://github.com/mikepenz/AboutLibraries |
| `com.vanniktech:android-image-cropper` | 4.7.0 | Apache-2.0 | https://github.com/CanHub/Android-Image-Cropper |
| `facebook/zstd` | 1.5.7 | BSD-3-Clause, GPL-2.0-only | https://www.zstd.net |
| `fcitx/fcitx5` | 5.1.21 | LGPL-2.1-or-later | https://github.com/fcitx/fcitx5 |
| `fcitx/fcitx5-chinese-addons` | 5.1.13 | GPL-2.0-or-later, LGPL-2.1-or-later | https://github.com/fcitx/fcitx5-chinese-addons |
| `fcitx/fcitx5-lua` | 5.0.17 | LGPL-2.1-or-later | https://github.com/fcitx/fcitx5-lua |
| `fcitx/libime` | 1.1.15 | LGPL-2.1-or-later | https://github.com/fcitx/libime |
| `io.arrow-kt:arrow-annotations-android` | 2.2.3 | Apache-2.0 | https://arrow-kt.io/ |
| `io.arrow-kt:arrow-atomic-android` | 2.2.3 | Apache-2.0 | https://arrow-kt.io/ |
| `io.arrow-kt:arrow-core-android` | 2.2.3 | Apache-2.0 | https://arrow-kt.io/ |
| `io.arrow-kt:arrow-exception-utils-android` | 2.2.3 | Apache-2.0 | https://arrow-kt.io/ |
| `io.arrow-kt:arrow-functions-android` | 2.2.3 | Apache-2.0 | https://arrow-kt.io/ |
| `j-jorge/libintl-lite` | edbb306 | BSL-1.0 | https://github.com/j-jorge/libintl-lite |
| `libuv/libuv` | 1.52.0 | MIT | https://libuv.org/ |
| `lua/lua` | 5.4.7 | MIT | https://www.lua.org/ |
| `org.jetbrains.kotlin:kotlin-parcelize-runtime` | 2.4.10 | Apache-2.0 | https://kotlinlang.org/ |
| `org.jetbrains.kotlin:kotlin-reflect` | 1.8.22 | Apache-2.0 | https://kotlinlang.org/ |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.10 | Apache-2.0 | https://kotlinlang.org/ |
| `org.jetbrains.kotlin:kotlin-stdlib-common` | 2.4.10 | Apache-2.0 | https://kotlinlang.org/ |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7` | 1.8.0 | Apache-2.0 | https://kotlinlang.org/ |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 1.8.0 | Apache-2.0 | https://kotlinlang.org/ |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 | https://github.com/JetBrains/java-annotations |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 | http://jspecify.org/ |
| `org.mechdancer:dependency` | 0.1.2 | WTFPL | https://github.com/MechDancer/dependency |
