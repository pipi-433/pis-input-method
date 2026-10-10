# 2.0.0 许可与对应源码资料

本页的依赖清单、源码仓库记录和归档校验值对应已保存的 2.0.0 发行资料。当前工作区后来加入的[候选栏横向滑动](../releases/pis-horizontal-candidates.md)尚未重新归档，不能把这些记录当作最新工作区的完整文件清单。整体状态见 [实现状态](../../PIS_MIGRATION_STATUS.md)。

| 资料 | 路径 | 内容 |
|---|---|---|
| 许可范围 | [LICENSING.md](../../LICENSING.md) | 源码许可范围与组件许可 |
| 原始文本 | [LICENSES/manifest.json](../../LICENSES/manifest.json) | 133 份许可证/NOTICE 文件的来源与 SHA256；LICENSES/maven/ 保存归档内声明 |
| 第三方声明 | [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)、[NOTICE](../../NOTICE) | 118 项原构建元数据及后来新增的原生/模型组件 |
| 修改说明 | [CHANGES.md](../../CHANGES.md) | pis 修改与固定词频补丁 |
| 构建指南 | [BUILD.md](../../BUILD.md) | 环境、Git/ZIP 源码恢复、编译及自行签名安装 |
| 解析依赖 | [MAVEN_AND_NATIVE_METADATA.json](MAVEN_AND_NATIVE_METADATA.json)、`maven-poms/` | 118 项元数据与 108 项 Maven 的原 POM |
| 固定源码 | [SOURCE_REPOSITORIES.json](SOURCE_REPOSITORIES.json)、[EXTERNAL_SOURCE_ARCHIVES.json](EXTERNAL_SOURCE_ARCHIVES.json) | 公共仓库提交、源码归档 URL/哈希；含 GPLv3 eSpeak 来源 |
| 模型与输入 | [MODELS.md](MODELS.md)、[PREBUILT_DATA_INPUTS.json](PREBUILT_DATA_INPUTS.json) | 区分软件/模型/数据许可与实际版本 |

依赖的原始版权和许可证声明保存在 `LICENSES/`；组件、模型和数据分别适用各自的许可。

## 本地源码快照

交付在 `voice_work/dist/pis-input-method-2.0.0-source-local.zip`。包含创建该快照时的应用源码、固定 Fcitx/libime/Chinese addons/Lua/KenLM/Yoga 与额外 llama 源码、10 份固定语音/运行时源码归档、许可/修改/构建资料，以及 zip 内的 `SOURCE_SNAPSHOT.json` / `SOURCE_FILES.sha256`。

快照大小为 370,760,850 字节，记录 7,186 个文件，SHA256 `18c13ca13229d94f3e2296a50956729ed42821ea67a33521f3e571d359f48316`。本次仅更新外部文档，ZIP 内源码、文档和清单保持原样；横向滑动功能及归档之后的文档更新均不在其中。
