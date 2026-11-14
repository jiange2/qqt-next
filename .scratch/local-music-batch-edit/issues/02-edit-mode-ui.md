# 02: 编辑模式选择态与批量面板 UI

**What to build:** `LocalMusicTopBarMenu` 加「批量管理」项（视图切换之后、重新扫描之前；仅歌曲视图 `!showAlbums` 显示；`permissionGranted = false` 禁用，沿用 rescanEnabled 范式；空库不额外禁用）。编辑模式开关 + 勾选集合（`Set<LocalSong.id>`）提升到 `AppNavigation`，`remember` 以 `local_music` 返回栈条目为键（与视图状态同构，离开本页键置空重建，勾选不跨页保留）；不放 LocalMusicViewModel。歌行编辑态：前置勾选框、点行 / 长按切换勾选（不跳播、不弹单首面板）、左滑收起（`swipe = null`）、不可用行（`available = false`）不可选。顶栏编辑态「取消 / 全选·取消全选 / 已选 n 首」、⋮ 隐藏；全选 = 可见列表全部可选行、已全选再点清空。底栏批量操作栏「编辑信息」（0 选禁用，避让 MiniPlayer / 底部导航）。批量 ModalBottomSheet：仅专辑、专辑艺术家两个自由文本字段，初始为空、不预填、无「恢复默认」，两字段 trim 皆空时保存禁用；保存接票据 01 的批量入口，成功关面板 + 退出编辑模式 + 清空勾选（Toast 由 notice 链路弹）。退出路径：取消按钮、系统返回（BackHandler，不退出页面）、切页 / 进其他页面（结构保证）、保存成功、编辑中权限被收回（退出 + 沿用整库灰显）。进入编辑模式收起菜单与左滑展开态。

**Blocked by:** 01

**Status:** open

- [ ] 歌曲视图 ⋮ 菜单出现「批量管理」（视图切换之后），专辑视图不显示；未授权禁用
- [ ] 进入编辑模式：勾选框出现、点行 / 长按勾选不跳播不弹单首面板、左滑收起
- [ ] 顶栏取消 / 全选 / 已选 n 正确；全选跳过灰显行；已全选再点清空
- [ ] 0 选「编辑信息」禁用；≥1 选可点；批量栏不遮挡 MiniPlayer / 底部导航
- [ ] 面板双字段初始为空；两空保存禁用；留空字段不改
- [ ] 保存 → Toast「已修改 n 首」+ 自动退出 + 勾选清空；专辑视图分组 / 计数 / 封面随之更新
- [ ] 取消 / 系统返回退出编辑模式、勾选清空、未保存输入丢弃，不退出页面
- [ ] 切底部 Tab / 进其他页面再回来是正常态（勾选不保留）
- [ ] 编辑中权限被收回 → 退出编辑模式 + 整库灰显
- [ ] 本地专辑页与专辑卡片墙无编辑模式入口（Out of Scope 未动）

## Comments

- 2026-11-14 实现完成：提交 6a97b9a、a177510（合并 c159c86），落在集成分支 `feat/local-music-batch-edit`。改动七文件：新增 `LocalMusicEditRules.kt`、`LocalBatchEditSheet.kt`、`LocalMusicEditRulesTest.kt`，改 `AppNavigation.kt`、`LocalMusicScreen.kt`、`LocalSongRow.kt`、`LocalMetadataEditSheet.kt`。摘要：纯规则 `LocalMusicEditRules.kt`（`selectableSongIds` 灰显跳过 / `toggleSelectAll` 已全选再点清空 / `isAllSelected` 顶栏文案判据 / `batchPanelCanSave` 两字段 trim 皆空禁用）+ JVM 测试 13 例；菜单「批量管理」插视图切换之后、重新扫描之前（仅 `!showAlbums` 显示、`editEnabled = permissionGranted` 禁用、空库不额外禁用、点击收起菜单）；编辑态 `localMusicEditing` / 勾选 `localMusicSelection` / 面板 `localMusicBatchPanelOpen` 提升 `AppNavigation` `remember(localMusicEntry)`（与视图状态同键，离开本页键置空重建），`exitLocalMusicEditMode` 统一清空；歌行序号位换前置勾选框（`Checkbox onCheckedChange = null` 只展示、点行 / 长按切换、灰显 `enabled` 挡住）、编辑态 `swipe = null`、`LaunchedEffect(showAlbums, editing)` 收起左滑；topBar lambda 编辑态整条换 `LocalMusicEditTopBar`（取消 / 已选 n 首 / 全选·取消全选，⋮ 隐藏）、`LocalMusicBatchBar`（编辑信息、0 选禁用、Scaffold 内容区底部 NavHost 之下）；`LocalBatchEditSheet` 双空字段、留空 = 不改、无恢复默认、保存接 `saveBatchMetadata` 后即退出（`MetadataField` private→internal 共用）；退出路径：取消 / `BackHandler`（面板先收再退编辑、不退出页面）/ 切页（结构保证）/ 点保存 / 权限收回 `LaunchedEffect`。勾选项为设备级验收清单，待人工编译真机逐项确认（本仓规约：AI 不编译、不跑 gradlew），照先例保持未勾；Status 推进与归档由编排层在评审修复后统一执行。