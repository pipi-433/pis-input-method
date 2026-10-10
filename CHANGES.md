# pis 修改记录

## 2.0.1

- 修复候选分页 `getCandidateListCached()` 崩溃：代理显式实现新访问器，正式打包完整编译 Kotlin，并检查编译后代理覆盖所有接口方法。
- 首道双拼关闭 QWERTY 错键纠正，避免 `UI` 混入“一、吃”等其它音节；全拼保留原 QWERTY 纠正。零声母 `UI → ei`、`UE → e`、`UF → eng`。
- 26 个字母键的角标快捷输入改为上滑，长按输入保留；预览提示同步使用上滑方向。
- 新增接口代理与 26 字母快捷输入回归检查，现有 7 项单元测试通过；模拟器实际输入引擎验证候选分页、12 组零声母编码、`BM`、`GV` 与 `NIHD`。

整理日期：2026-10-10。上游基线为 Fcitx5 Android 0.1.3，提交 `048f581c652367567b8ee5c28c5163b805288895`。此前 2.0.0 的代码与资源记录随原源码快照保存；下方另列最新重建包含的源码改动。

## 最新构建更新：候选栏横向滑动

- 顶部候选改为单行横向列表，左滑查看后面的文字/单词，右滑返回；保留点击选词、长按词义和固定右侧点阵。
- 原生首批 16 项之后按 32 项分批加载，只通知插入；新输入取消旧页并回到开头，以原生事件快照和 UI 版本避免旧页混入。
- 更改 `HorizontalCandidateComponent.kt`、`HorizontalCandidateViewAdapter.kt`、`CandidateUi.kt`、`FcitxAPI.kt` 和 `Fcitx.kt`，未改原生 JNI 或模型。
- ARM64 Release 编译成功（1 分 28 秒），签名、模型哈希和 16KB 对齐检查通过；UI/真机验证待完成。模拟器及此前源码 ZIP 保持原版本。
- 修正上游主题序列化测试中 2.0 格式的迁移断言，验证迁移后的候选颜色和再次序列化；现有 5 项单元测试全部通过。

详细实现见 [候选栏横向滑动](docs/releases/pis-horizontal-candidates.md)，新 APK 的校验值见 [最新构建记录](docs/releases/pis-github-20261010.md)。此节不属于下方此前交付的 2.0.0 APK 内容。

## 2.0.0 相对上游的修改

| 范围 | 修改 | 主要源码 |
|---|---|---|
| 应用与入口 | 独立包名、图标与名称；精简设置、词频管理；Release/Debug 验证入口分离 | `app/build.gradle.kts`、Manifest、`pis/PisSettingsActivity.kt` |
| 键盘与外观 | QWERTY/纯数字九宫格、首道声韵母角标、主题/高度/底部上移、冷灰键帽与微玻璃栏 | `input/keyboard/`、`pis/PisNumberKeyboard.kt`、`pis/PisAppearance.kt` |
| 顶部输出 | 候选短译文、词库释义、语音/译文显示；点阵点击候选展开/键盘收起 | `input/bar/`、`input/candidates/`、`pis/` |
| 输入路径 | 拼音 composing 写入应用编辑框；候选差量刷新、缓存提示及宽度预留；多指提前结算前普通键；删除按下首删 | `FcitxInputMethodService.kt`、`PreeditComponent.kt`、`BaseKeyboard.kt`、`CustomGestureView.kt` |
| 按键手势 | 逗号长按剪贴板、句号长按翻译、空格长按语音 | `KeyAction.kt`、`KeyDefPreset.kt`、`CommonKeyActionListener.kt` |
| 本地翻译 | 固定公开 llama.cpp / Hy-MT2、JNI、独立进程 Binder、预热、取消/编辑器结果守卫 | `native-translation/`、`pis/translation/`、`tools/prepare_translation.py` |
| 本地语音 | 固定 sherpa-onnx/Zipformer/标点模型；草稿与局部编辑、候选修正、热词管理 | `pis/voice/`、`assets/voice/`、`tools/prepare_voice.py` |
| 词频 | 原生词库/历史计数与自定义词管理 | `pis/PisWordFrequencyActivity.kt`、`patches/pis-word-frequency.patch` |
| 灯效 | 24 灯与三色状态灯的 Canvas/ValueAnimator 反馈；原生绘制实现 | `pis/PisFeedbackView.kt`、相关 Glass/Keycap Drawable |
| 构建 | Android SDK/NDK/CMake 固定版本，模型哈希、ARM64、R8/JNI 保留、16KB 对齐检查、正式签名脚本 | `build-logic/`、`gradle.properties`、`tools/build_release.ps1`、`tools/audit_pis_apk.py` |

中文引擎扩展对 `fcitx5-chinese-addons` 固定提交 `9614900b591d180315144b14c529be756fa0e4da` 应用 `patches/pis-word-frequency.patch`。补丁使用已有 get/setAddonSubConfig 通道管理真实运行词库；构建时检查是否已应用，不覆盖其他不兼容修改。

## 保留与来源

输入引擎、候选/按键基础组件、部分内部维护页面来自上游，保留其原始版权和许可声明。第三方组件与模型信息见 `LICENSING.md`。

## 发行与本轮资料

现有 APK 为 `io.pis.inputmethod` / 2.0.0 / 20002，ARM64：

`b711c4c76920669c0dea7d1aba7021687c96a99fd90e7c38710ec40a8e84d56c`

发行资料包括 LICENSES、NOTICE、依赖/模型记录、源码快照和构建指南。文档在 APK 构建后补充，随发行资料单独提供。APK 校验值对应上述构建。

验证范围：已完成对应版本的编译和包检查，真机输入与录音测试待完成。
