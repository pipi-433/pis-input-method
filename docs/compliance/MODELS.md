# 模型与数据来源记录

核对日期：2026-10-10。软件框架、模型权重、转换脚本和数据分别记录。以下信息对应当前打包文件。

## 整句翻译

- 文件：`Hy-MT2-1.8B-Q4_K_M.gguf`，1,133,080,448 字节。
- 模型发布者：Tencent；[官方模型仓库](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF)。获取提交 `a0c709d9fac510f2c807aa3af52872340dc37a4a`。
- 许可：官方原模型 LICENSE 明确为 Apache-2.0，Tencent 的版权段和完整文本保留在 `LICENSES/application-bundled/Hy-MT2-Apache-2.0.txt`。
- SHA256：`dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699`。
- `tools/prepare_translation.py` 验证大小和哈希；JNI 调用固定 llama.cpp，不改变模型来源/所有权。

## 语音识别

- 实际转换包：`sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16`，来自 [sherpa 官方模型 Release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)。
- [官方转换说明](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html#sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16-bilingual-chinese-english) 指向原模型 [csukuangfj/k2fsa-zipformer-bilingual-zh-en-t](https://huggingface.co/csukuangfj/k2fsa-zipformer-bilingual-zh-en-t)。
- 原模型卡及 API 元数据在提交 `e2382758de9a0219b4efe682b95af30b399db3b8` 标注 `apache-2.0`；保存的许可字段摘录见 `LICENSES/models/Zipformer-original-model-card.md`。原模型、ONNX 转换和 int8 量化区分记录。
- 转换包的许可信息见原模型卡；原模型、转换来源和框架 LICENSE 均保留。

| APK 内文件（`assets/voice/`） | SHA256 |
|---|---|
| `encoder-epoch-99-avg-1.int8.onnx` | `909090f5df6874ea7be9aa76b57d5bb5b40d2ae1f7342cf7c3f6f0e14d0f8535` |
| `decoder-epoch-99-avg-1.onnx` | `89be509a83175261695bdef5fd1c7b9ab1129a663d1284e7ba9f8507b21e0906` |
| `joiner-epoch-99-avg-1.onnx` | `bcbcf0cc1b449bc32b7777084ab5a65d1199b822e2aae149d3a43adc9f4b5775` |
| `tokens.txt` | `a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3` |

## 标点

- 实际文件：`punctuation/model.int8.onnx`，来自 sherpa 的 `sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8` 转换包。
- SHA256：`65a3fb9f5ad7bfb96bf69e0dc4481df97f6ee60513c1d94ce981ba6effd524b1`。
- [sherpa 官方转换说明](https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/punctuation/pretrained_models.rst) 指向 [iic/punc_ct-transformer_zh-cn-common-vocab272727-pytorch](https://modelscope.cn/models/iic/punc_ct-transformer_zh-cn-common-vocab272727-pytorch)。原模型官方页面标注 Apache License 2.0，模型发布方为 iic / Alibaba。
- 转换包内 README 和脚本用于确认转换来源，保存的来源摘录见 `LICENSES/models/punctuation-conversion-README.md`；完整包中模型哈希由 `tools/prepare_voice.py` 验证。转换版本为 2024-04-12。

## 语音运行时与附加代码

官方 AAR 为 sherpa-onnx 1.13.8，SHA256 `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`。ONNX Runtime 为 1.28.2，APK 内版本字符串与固定 cmake 配方一致。AAR 还包含 eSpeak/Piper 原生实现，组件许可及对应源码见 `THIRD_PARTY_NOTICES.md`。

## 词典及调试资源

候选英文提示和长按词义使用 `gloss.db`。全拼基础数据来自固定 Fcitx prebuilt，版本和来源见 `PREBUILT_DATA_INPUTS.json`。WordNet 是仅供调试版使用的实验资源，正式 2.0.0 APK 不包含。
