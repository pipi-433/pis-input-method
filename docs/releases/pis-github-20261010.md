# pis' input method：2026-10-10 最新构建

本次从最新源码重建 ARM64 Release APK，包含候选栏横向滑动和最新文档。版本沿用 2.0.0 / 20002，包名 `io.pis.inputmethod`，Android 8.0 及以上；使用原 pis Release 签名。

源码仓库：[pipi-433/pis-input-method](https://github.com/pipi-433/pis-input-method)。构建方法见 [BUILD.md](../../BUILD.md)，功能变更见 [CHANGES.md](../../CHANGES.md)。

## APK 与校验

- 文件：`pis-input-method-2.0.0-arm64.apk`，本地交付目录 `pis-input-method-2.0.0-github-20261010/`。
- 大小：1,324,913,970 字节，约 1.32 GB。
- SHA256：`c53d0947d4aee36fd952bcb231431efc62eb6ac7bd9080107e2c1486cbb46145`。
- 证书 SHA256：`328e3d422c799327c7f3a47c4ea1f7a99f52506e774dbfd4ec8fe5802736fee7`。
- 交付目录附 `SHA256SUMS.txt` 和 `package-audit.json`。

## 验证范围

`:app:assembleRelease` 成功（1 分 28 秒）。包检查确认 ARM64、Release 签名、模型/词典哈希、16KB ELF/ZIP 对齐通过；关闭调试标记，无调试验证组件、WordNet 或联网权限。

`:app:testDebugUnitTest` 成功（29 秒），5 项测试全部通过，覆盖字符串转义和主题序列化。修正了上游测试中对 2.0 主题无需迁移的过时断言，并验证候选颜色迁移及重新序列化后的稳定性。

候选栏交互、语音及真机测试待完成。APK 在本地提供，源码仓库包含源码、词频补丁、构建脚本和许可资料；密钥、凭据、缓存及大模型由本地配置或构建脚本管理。

此前 APK 与源码 ZIP 的历史记录保留在 [pis-2.0.0.md](pis-2.0.0.md)，其校验值对应此前交付。
