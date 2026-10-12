# 构建 pis'input method

发行许可与对应源码资料见 [LICENSING.md](LICENSING.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)、[CHANGES.md](CHANGES.md) 和 [BUILD.md](BUILD.md)。语音 AAR 内各组件的许可证单独保留。

当前正式构建为 2.0.3（ARM64 versionCode 20032），包名 `io.pis.inputmethod`。调试版包名为 `io.pis.inputmethod.debug`，可以共存。当前包信息见 [2.0.3 构建记录](docs/releases/pis-2.0.3.md)；[2.0.2](docs/releases/pis-2.0.2.md)、[2.0.1](docs/releases/pis-2.0.1.md)、[此前 2.0.0](docs/releases/pis-2.0.0.md)及旧 preview 文档保留作历史。

本次正式 ARM64 包已包含[候选上滑英文与玻璃词义弹窗](docs/features/pis-candidate-english-and-glass.md)，保留候选项动态宽度与横向滑动。Release 编译、52 个接口方法检查、原签名、模型/词典哈希与 16KB 对齐检查通过。旧 v2.0.2 APK 不包含上述两项新功能；旧版记录作为历史交付保留，新包使用单独目录和校验记录。

正式版使用专用签名、Release 构建和 R8 代码/资源裁剪。设置 `JAVA_HOME`、`ANDROID_HOME`、`ECM_DIR` 及以下模型/源码环境后，执行：

```powershell
./tools/build_release.ps1 -Version '2.0.3' -Python 'python' -OutputDirectory '你的本地交付目录'
```

签名位于忽略目录 `.pis-local/signing/`：`pis-release.jks` 与 `credentials.xml`。后者使用 Windows DPAPI 加密，仅当前 Windows 用户/机器可解密。必须单独安全备份原密钥和密码，不能放进公开源码、交付目录或 APK；后续更新必须复用同一密钥。换电脑时恢复同一 JKS，并用原密码重新生成本机 PSCredential 文件。脚本不会自动生成或替换签名密钥。版本递增时同时更新 `Versions.baseVersionCode`；ARM64 实际 code 为 base × 10 + 2。

脚本只编译、复制和检查安装包完整性，不安装、操作模拟器或上传。默认输出 `.pis-local/releases/<版本>/`；输出包括 APK、SHA256SUMS 和包结构检查报告。正式版无输入验证页和探针，WordNet 仅调试版包含。

基于 Fcitx5 Android 0.1.3（`048f581c652367567b8ee5c28c5163b805288895`），保留其 LGPL-2.1-or-later 许可与版权。pis 修改以源码及补丁记录。

需要JDK21、Android SDK36与Build Tools36.1.0、NDK28.0.13004108、CMake3.31.6；Windows需ECM和gettext。Gradle wrapper9.6.1，依赖版本沿用上游。初始化以下子模块及各自嵌套依赖：lib/fcitx5、lib/libime、lib/fcitx5-chinese-addons、lib/fcitx5-lua各自src/main/cpp目录内的git子模块，及fcitx5的prebuilt。插件子模块不是首版功能依赖。

设置JAVA_HOME、Android SDK local.properties和ECM_DIR，gettext的msgfmt加入PATH。SDK路径与机器工具路径仅放在被忽略的本地配置，不提交密钥/构建物。

Windows gettext-tools 1.0需要同版本gettext-runtime、gettext-libtextstyle与libiconv运行库；只有msgfmt.exe还不够。若退出码-1073741515，先核对其DLL导入并补齐官方MSYS2运行包。本地补充放在.pis-local/host-tools，PATH加入该bin目录；配置生成使用原生工具链。

```powershell
./gradlew.bat --no-daemon '-PbuildABI=x86_64' '-PbuildVersionName=2.0.3' :app:assembleDebug
./gradlew.bat --no-daemon '-PbuildABI=arm64-v8a' '-PbuildVersionName=2.0.3' :app:assembleDebug
```

调试版包名 `io.pis.inputmethod.debug`，应用名 `pis'input method`。手机使用 ARM64，模拟器使用 x86_64。

只检查 Kotlin、不生成 APK 的目标和 PowerShell 参数引号说明见 [BUILD.md](BUILD.md#先编译可自行安装的调试版)。最新 APK 支持顶部候选栏左右滑动：首批16项之后按32项加载，保留点击/长按和固定点阵。

候选词和释义共同决定候选项宽度，达到可用宽度后行尾省略，保持原字号。“候选显示 / 候选项最大宽度”默认 240dp，可调 80–480dp，上限包含横向内边距；展开网格受单元格宽度限制。

当前自定义功能：独立 QWERTY 和纯数字九宫格、主题/高度/底部上移/按键音与震动、全拼/双拼（首道）及声韵母角标、模糊音与学习、词频管理、顶部候选短译文与词库长按释义、长按句号离线翻译、长按空格离线语音、长按逗号剪贴板、圆角微玻璃栏与 24 灯反馈。隐私模式开关、满宽点阵背景和复制全部入口已移除；密码等编辑器的学习限制保留。最新跟手改动为多指连续输入时提前结算前普通键、删除按下立即首删，以及候选差量刷新/提示预留宽度。真机输入时延和语音识别准确率列入后续验证项目。

模型/JNI由tools/prepare_translation.py准备。设置ANDROID_HOME、可选PIS_LLAMA_SOURCE（仅接受固定公开commit）、PIS_MODEL_FILE（仅接受固定Q4 SHA256）以及pythonExecutable构建参数。默认从官方上游下载，权重和上游检出位于忽略目录；JNI源码是我们新增的C++接口，重新编译支持ARM64和模拟器x86_64，模型资产不参与Fcitx数据复制器，避免额外重复复制。

本地诊断日志与依赖临时下载位于.gitignore范围，不能公开提交。公开分发时须保留上游及各个依赖的源码、许可与变更说明。

词频管理需要对固定的 fcitx5-chinese-addons 子模块打 patches/pis-word-frequency.patch。lib/fcitx5-chinese-addons 的 preBuild 会运行 tools/apply_pis_pinyin_patch.py：已应用则验证并跳过，未应用则先检查再应用，遇到其他不兼容改动停止并报告。补丁通过现有 get/setAddonSubConfig 接口读写真实运行时词库/历史，避免拆解或覆盖正在使用的二进制学习文件。保留子模块来源、许可与本项目补丁。

语音由 tools/prepare_voice.py 获取并校验官方 sherpa-onnx 1.13.8 AAR、Zipformer 中英文模型和标点模型，支持 ARM64 与 x86_64。调试构建的 tools/prepare_wordnet.py 从 Princeton 固定 WordNet 3.1 压缩包生成只读 SQLite 并保留原许可；正式构建不准备或打入该资源。源码检出后的首次构建需要联网下载，应用运行不需要联网。
