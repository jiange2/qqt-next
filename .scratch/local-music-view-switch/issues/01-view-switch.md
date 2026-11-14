# 01: 默认专辑视图 + 切换进顶栏菜单

**What to build:** 视图状态提升到 AppNavigation（remember 以 local_music 的 NavBackStackEntry 为键，初值 true = 专辑），分别传给 LocalMusicScreen（替换页内 remember）与 LocalMusicTopBarMenu（新增 showAlbums / onToggleView）；整体删除 `LibraryViewSwitch` / `ViewSwitchSegment`，`LibraryArea` 去掉切换行与 onSelectView；⋮ 菜单第一项新增视图切换（文案随当前视图翻转：「切换到歌曲视图 / 切换到专辑视图」，点击翻转并收起菜单，始终可点）；更新页头注释与相关旧注释。状态不入 ViewModel、不落盘。

**Blocked by:** 无。

**Status:** open

- [ ] 进入本地音乐页默认专辑卡片墙（含从本地专辑页返回、进程被杀重启）
- [ ] 内容区顶部胶囊分段控件已移除，卡片墙 / 歌曲列表直接从顶栏下方开始
- [ ] ⋮ 菜单第一项为视图切换，文案随当前视图翻转，点击立即切换并收起菜单
- [ ] 空库 / 未授权 / 加载中切换项仍显示、可点
- [ ] 「重新扫描」（含未授权禁用）与「排除的文件夹」行为不变
- [ ] 切到歌曲视图后离开再进回专辑视图（状态不落盘、不入 ViewModel）
- [ ] 打开播放器覆盖层再收起，视图状态保留
- [ ] 页头注释与相关旧注释更新

## Comments

- 2026-11-14 实现完成：提交 7372fb8（合并 fad377d），落在集成分支 `feat/local-music-view-switch`。改动两文件：`AppNavigation.kt`、`LocalMusicScreen.kt`。摘要：默认专辑视图；整体删除 `LibraryViewSwitch` / `ViewSwitchSegment` 胶囊分段控件，`LibraryArea` 去掉切换行与 `onSelectView` 参数（保留 `showAlbums`）；视图切换收进顶栏 ⋮ 菜单第一项——`LocalMusicTopBarMenu` 新增 `showAlbums` / `onToggleView` 参数，文案随当前视图翻转（「切换到歌曲视图 / 切换到专辑视图」），点击翻转并收起菜单，始终显示、始终可点（不依赖音频权限与库内容，区别于「重新扫描」的 `rescanEnabled`）；视图状态提升到 `AppNavigation`：`remember` 以 `local_music` 返回栈条目为键（初值 true = 专辑，离开本页键置空重建 → 返回必回专辑；播放器覆盖层不切 NavHost → 状态保留），替换 `LocalMusicScreen` 页内 remember 并分别传给页面与顶栏菜单；视图变化收起左滑展开态（`LaunchedEffect(showAlbums)`）；页头注释与菜单注释更新。状态不入 ViewModel、不落盘、不新增设置页、图标保持 MoreVert。勾选项为设备级验收清单，待人工编译真机逐项确认（本仓规约：AI 不编译、不跑 gradlew），照先例保持未勾；Status 推进与归档由编排层在评审修复后统一执行。