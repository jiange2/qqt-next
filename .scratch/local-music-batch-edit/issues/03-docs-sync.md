# 03: 文档同步与归档

**What to build:** `android/doc/ui.md`：LocalMusicScreen 节补编辑模式（⋮ 菜单入口与权限门槛、选择态与勾选交互、顶栏 / 底栏、批量面板双字段与留空 = 不改、退出路径），LocalMetadataEditSheet 节补批量变体；`android/doc/storage.md`：覆盖层节补批量提交（一次整表原子写、与单首共用 editLock、空壳删键口径不变）。核对 CONTEXT.md「专辑艺术家 / 编辑模式 / 批量管理」词条与最终实现一致（含「专辑作者」弃用词）。spec 文字对齐代码事实（照 local-music-delete / view-switch 先例逐条核对）。票据 01 / 02 补实现 Comments。Status 推进与归档留待评审修复后由编排层统一执行；设备级验收清单保持未勾，待人工编译后真机逐项确认（本仓规约：AI 不编译、不跑 gradlew）。

**Blocked by:** 01, 02

**Status:** open

- [ ] ui.md 编辑模式入口 / 选择态 / 顶栏底栏 / 面板 / 退出路径与实现一致
- [ ] storage.md 批量提交口径与实现一致
- [ ] CONTEXT.md 三词条与实现一致（含「专辑艺术家」用词）
- [ ] spec 文字对齐代码事实，差异逐条记录
- [ ] 归档提交在评审修复合入之后（先例口径）

## Comments

- 2026-11-14 完成（文档同步提交落在 `ticket/lmb-03-docs-sync`，base c159c86，待编排层合入）。**同步清单**：
  - `android/doc/ui.md`：关键文件表 localmusic 行补批量管理（编辑模式勾选歌行 + `LocalMusicEditTopBar` / `LocalMusicBatchBar` / `LocalBatchEditSheet`、`LocalSongRow` 编辑态序号位换勾选框、ViewModel 批量保存一次提交）；导航层接线菜单 bullet 补批量管理项（第二项、仅歌曲视图、未授权禁用）并注明编辑态整条顶栏替换、菜单隐藏；新增编辑模式状态归属与顶栏/底栏/面板接线两条 bullet（`localMusicEditing` / `localMusicSelection` / `localMusicBatchPanelOpen` `remember(localMusicEntry)` 与视图状态同键、离开本页键置空重建、播放器覆盖层保留、不放 ViewModel、`exitLocalMusicEditMode` 共用退出、`BackHandler` 面板先收再退编辑；topBar lambda 整条换 `LocalMusicEditTopBar`、`MusicTopBar` 无内部本地分支、批量底栏放 Scaffold 内容区底部、面板导航层渲染保存过滤勾选行调 `saveBatchMetadata`）；`notice` bullet 补批量保存「已修改 n 首」；左滑机制节补编辑模式左滑收起 / 长按改勾选；LocalMusicScreen 节顶栏菜单改四项并新增「编辑模式（批量管理）」bullet（入口与权限门槛、勾选交互序号位换框、顶栏 / 底栏、批量面板、保存与退出路径），元数据编辑 bullet 注明编辑态长按改勾选；LocalMetadataEditSheet 节补批量变体 `LocalBatchEditSheet` 两条 bullet（双空字段、留空 = 不改、两空保存禁用、无恢复默认、一次提交、无播放器同步、规范词）。
  - `android/doc/storage.md`：覆盖层节补编辑提交段（单首与批量共用 `editLock`、单首表更新锁外先行 / 批量锁内、`batchAlbumCommit` 整表变换、`saveOverlay` 整表一次原子写、空壳删键口径不变、落盘失败只记日志）；启动加载「清空某字段」条注明单首面板口径（批量面板留空 = 不改）。
  - `android/project.md`：模块概述与 localmusic/ 目录注释补批量管理编辑模式，目录树补 `LocalMusicEditRules.kt`（照 local-music-delete 先例 b390d8e 新增文件同步树）。
  - `android/CONTEXT.md`「专辑艺术家 / 编辑模式 / 批量管理」三条词条（含「专辑作者」弃用词）经与最终实现逐条核对**一致，未改动**：专辑艺术家与艺术家不同字段、与专辑名构成归并键（`albumKey`）、单首与批量面板统一用此名 ✓；编辑模式仅歌曲视图、从批量管理进入、点行勾选非播放、顶栏取消 / 全选 / 计数、底部批量栏、取消或离开退出并清空、专辑卡片墙与本地专辑页无 ✓；批量管理仅改专辑 / 专辑艺术家、填了才改留空保持（与单首相反）、只写覆盖层不动文件（ADR 0020）、不含删除与其他字段 ✓。其它词条一律未动。
  - `spec.md`：文字对齐 7 处（详见 spec Comments：勾选框序号位、MusicTopBar 整条替换机制、面板开关状态归属、BackHandler 面板先收、纯函数接缝与锁内表更新、点保存即退出、测试文件名与例数）；补实现完成 Comments（票据 01：24926f0 / 9833c8b / 4512c68 合并 cb6d378；票据 02：6a97b9a / a177510 合并 c159c86）。**Status 不动**（仍 ready-for-agent，推进与归档由编排层在评审修复后统一执行，照 local-music-delete / view-switch 先例）。
  - **不新增 ADR 的判定经交付代码核对成立**：批量覆盖写是可逆的数据层扩展（延续 ADR 0020 / 0022、无推翻），「留空 = 不改」已写入 CONTEXT.md「批量管理」词条，身份 / 权限 / 快照模型不动；三条门槛（难以反转 / 无上下文会让人惊讶 / 真实取舍）均不成立。故未新增 ADR，`android/docs/adr/`、仓库级 `docs/adr/`、`GLOSSARY-MAP.md` 全未动。
  - 票据 01 / 02 Comments 记实现哈希与摘要；本票 Comments 记同步清单。三票 Status 与勾选项均不动（勾选项是设备级验收清单，待人工编译真机确认，照先例不勾）。历史 spec（`.scratch/local-music/`、`local-music-delete/`、`local-music-rescan/`、`local-music-view-switch/`）归档不改写。本票为文档同步，未改行为代码、未新增测试（照先例 169c7dc）。