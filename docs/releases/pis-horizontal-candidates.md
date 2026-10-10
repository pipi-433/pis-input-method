# 顶部候选栏横向滑动

2026-10-10 源码更新，已包含在最新 ARM64 Release APK；模拟器和此前源码 ZIP 保持原版本。包信息见 [最新构建记录](pis-github-20261010.md)。

查看整体状态：[实现状态](../../PIS_MIGRATION_STATUS.md)；查看本轮变更：[修改记录](../../CHANGES.md)。

顶部左侧候选区采用单行横向 RecyclerView：向左拖动查看后面的中文候选/英文单词，向右拖动返回。右侧灯效模块固定，不随候选滚动；候选中文、英文提示、字号和长按词义保留。完整句子翻译/语音显示条沿用原逻辑。

## 实现

- 原 FlexboxLayoutManager 显式禁止横向滚动。改为横向 LinearLayoutManager，候选视口占用左侧可用宽度，保持 64dp 输出栏高度。
- 原生 updateCandidatesBulk 首批最多返回 16 个词；接近末尾，或较宽视口需要更多内容时，通过已有 getCandidates(offset, limit) 每批获取 32 个。追加仅 notifyItemRangeInserted，不整表重绑；不在每次打字时取整张词表。
- 原生位置直接作为候选下标和稳定 ID，重复文字具有不同 ID。RecyclerView 拦截拖动并向子控件发送 CANCEL，停止点击/长按计时；短点击与静止长按沿用原选择/释义入口。
- 原生候选更新使旧分页失效，丢弃旧尾部；拼写/首批候选变化回到开头。分页应用前检查 UI 版本、预期下标、原生候选事件对象和输入状态快照，避免旧页混进新输入。新候选缓存是只读事件快照，不保存用户历史。
- 新编辑器、视图销毁/窗口隐藏时取消分页；同一列表只有一个请求。已知总数到尾部，或未知总数返回短页/空页时停止加载。
- 原展开网格按初始可见候选边界开始；横向拖动/追加不反复重置展开窗口。扩展入口仍是右侧点阵。

主要源码（以下路径均相对本项目）：

- [HorizontalCandidateComponent.kt](../../app/src/main/java/org/fcitx/fcitx5/android/input/candidates/horizontal/HorizontalCandidateComponent.kt)：横向布局、分页、输入切换与取消。
- [HorizontalCandidateViewAdapter.kt](../../app/src/main/java/org/fcitx/fcitx5/android/input/candidates/horizontal/HorizontalCandidateViewAdapter.kt)：追加通知与稳定位置 ID。
- [CandidateUi.kt](../../app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/CandidateUi.kt)：左侧候选视口。
- [FcitxAPI.kt](../../app/src/main/java/org/fcitx/fcitx5/android/core/FcitxAPI.kt) / [Fcitx.kt](../../app/src/main/java/org/fcitx/fcitx5/android/core/Fcitx.kt)：最新原生候选事件缓存。

## 检查和交付状态

`:app:compileDebugKotlin`（1 分 4 秒）及最新 `:app:assembleRelease`（1 分 28 秒）均成功，相关 diff 空白检查及 Release 包检查通过。UI、滑动和真机测试待完成。

最新 APK 包含本轮功能，使用原 Release 签名。此前 2.0.0 手机 APK 和 371 MB 本地源码快照不包含本轮功能，作为历史交付保留；最新源码见项目 GitHub 仓库。
