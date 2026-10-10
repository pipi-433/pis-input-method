# 构建与对应源码

当前目标：`pis'input method` 2.0.1 / 20012，`io.pis.inputmethod`，ARM64，Android 8.0 起。许可资料见 [LICENSING.md](LICENSING.md)，依赖和固定源码见 [docs/compliance/](docs/compliance/)。

最新 ARM64 Release 构建为 2.0.1，已包含[候选栏横向滑动](docs/releases/pis-horizontal-candidates.md)。最新 APK 的校验值见[构建记录](docs/releases/pis-2.0.1.md)；此前 APK 和源码 ZIP 保留为历史交付，旧 ZIP 不包含新滑动功能。

## 源码快照的内容

已保存的 2.0.0 源码包包含归档时的应用源码、构建脚本、pis 新增文件、固定 Fcitx/KenLM/Yoga 源码及词频修改，以及语音依赖源码归档和 SHA256 清单。各子模块的版本单独记录，归档后新增的候选栏滑动不在旧包内。

编译 APK 所用的大模型和预编译库不重复打入源码快照；构建脚本从固定来源获取并核对哈希。预编译目录要恢复到 `86ce2c95d42f1132746fbf60c278193aa1f4b758`，其对应 prebuilder 为 `eb156443de3b387089e51f9bd19df4e3ddce1732`。资料中保留该 prebuilder 配方，注意它的输入词典/语言模型版本与 libime 当前 CMake 默认下载版本可能不同，不能混用。

源码快照排除 Git 私有配置/历史、签名密钥、密码、个人词频、录音和构建缓存。

## 工具与环境

- JDK 21、Python 3.12、Git；Gradle Wrapper 9.6.1。
- Android SDK Platform 36、Build Tools 36.1.0。
- NDK 28.0.13004108、CMake 3.31.6 和 Ninja。
- KDE Extra CMake Modules（`ECM_DIR`），GNU gettext（`msgfmt`）。Windows 可使用 MSYS2 UCRT64 的完整运行库；不能只复制一个 EXE。
- 设置 `JAVA_HOME`、`ANDROID_HOME`、`ECM_DIR`，把 JDK、SDK CMake 与 gettext 的 bin 加到 PATH。`local.properties` 的 `sdk.dir` 填自己的 SDK 路径，不提交这个文件。

版本的实际定义见 `gradle/libs.versions.toml`、`Versions.kt`、`gradle.properties`。翻译源可设 `PIS_LLAMA_SOURCE`，但必须是固定 `50a6c5cf7c09ea5ea9c1937e4895ce88331c2869` 的 Git 检出；权重可设 `PIS_MODEL_FILE`，必须满足 prepare_translation.py 的 Q4 大小及 SHA256。

## 从 Git 仓库准备

```powershell
git clone https://github.com/pipi-433/pis-input-method.git
cd pis-input-method
```

初始化以下子模块即可构建本版应用，其他插件不必构建：

```powershell
git submodule update --init --recursive lib/fcitx5/src/main/cpp/fcitx5 lib/fcitx5/src/main/cpp/prebuilt lib/libime/src/main/cpp/libime lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons lib/fcitx5-lua/src/main/cpp/fcitx5-lua
```

构建时 `tools/apply_pis_pinyin_patch.py` 会检查并应用词频补丁。固定版本不匹配或有不兼容修改时应检查来源，不自动重置工作区。

## 从本地源码 ZIP 准备

解压后应用/Fcitx/KenLM/Yoga 的源码已经在原目录，不需要从 Git 自动重建它们。ZIP 不包含 `.git`，所以不要直接在 ZIP 内运行 `git submodule update`。先恢复缺少的预编译目录：

```powershell
python tools/restore_source_inputs.py
```

脚本只向不存在/空的目标取固定 Git 输入；已有不匹配的非空目录会报告，不删除、不重置。llama.cpp 由 prepare_translation.py 固定获取，或自行设置固定 Git 检出的 `PIS_LLAMA_SOURCE`。ZIP 中的额外源码归档供读取、源码分发和原生依赖重建参考；实际 app 构建默认使用已校验的官方 sherpa AAR。

## 先编译可自行安装的调试版

```powershell
./gradlew.bat --no-daemon '-PpythonExecutable=python' '-PbuildABI=arm64-v8a' '-PbuildVersionName=2.0.1' :app:assembleDebug
```

包名为 `io.pis.inputmethod.debug`，可以和正式版共存。模拟器改用 `x86_64`。首次需要联网获取固定源码/模型和 Maven 依赖；安装后的输入、翻译和语音不需要联网权限。

只检查 Kotlin 编译而不生成 APK 时使用：

```powershell
./gradlew.bat --no-daemon '-PpythonExecutable=python' '-PbuildABI=arm64-v8a' '-PbuildVersionName=2.0.1' :app:compileDebugKotlin
```

PowerShell 下将整个 `-P...` 参数加引号，避免版本号 `2.0.0` 被拆成额外任务名。本轮候选栏滑动已通过该编译目标及 `:app:assembleRelease`，最新包检查通过；安装及 UI 测试待完成。

## 自行签名的 Release

使用自己的密钥构建修改版。通过环境变量 `SIGN_KEY_FILE`、`SIGN_KEY_ALIAS`、`SIGN_KEY_PWD` 传入自己的 keystore 信息，然后执行：

```powershell
./gradlew.bat --no-daemon '-PpythonExecutable=python' '-PbuildABI=arm64-v8a' '-PbuildVersionName=2.0.1' :app:assembleRelease
```

签名密码通过本机环境变量或交互输入传入。使用 JDK keytool 创建自己的 keystore，以专用证书签名正式版。未提供签名时 Gradle 可能产生 unsigned APK，需要自行签名后才能安装。

自行签名版本无法直接覆盖原签名版本。可使用 debug 包或修改 applicationId 与原版并存；安装同包名的自行签名版本时，先备份个人数据，再卸载原版并安装新包。设备或企业管理员策略可能限制安装。

维护者本机的正式脚本与备份规则见 [PIS_BUILD.md](PIS_BUILD.md)。该脚本依赖本机 `.pis-local/signing/`，这些文件不会随源码分发。

## 检查与复现范围

源码包记录发行 APK、依赖、模型和源码文件的 SHA256。重建时的签名、时间戳和构建元数据单独记录。已验证源码包文件清单、来源记录和敏感文件排除；独立解压重建列入后续验证项目。各版本的验证结果见对应发行记录。

旧 2.0.0 APK/ZIP 的校验值和检查报告对应原文件。本次带新滑动功能的 APK 已在单独交付目录生成，并附新的哈希和包检查报告；原发行文件及源码 ZIP 保留。最新源码通过项目 GitHub 仓库提供。

正式打包使用 `tools/build_release.ps1`：完整编译 Kotlin，并运行 `tools/check_fcitx_api.py` 检查代理接口，防止增量编译残留旧类。发布时可通过 `-CommitHash` 指定对应源码提交号。
