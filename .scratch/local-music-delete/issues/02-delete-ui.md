# 02: 歌行左滑删除 UI 与列表联动

**What to build:** `LocalSongRow` 包 `SwipeRevealRow`（复用 RowSwipeReveal，ADR 0018），左滑露「删除」按钮（`DangerRed`、88dp、白字，对齐下载页）；本地音乐页与本地专辑页两处接线（同一行组件）。Android 9 及以下删除前弹 App 内 `AlertDialog` 确认（对齐下载页文案），Android 10+ 不叠加 App 框（系统框即确认）。删除成功：从 `_songs` 即时移除该行、专辑分组/计数重算、库快照原子落盘；失败：Toast「删除失败」并刷新行状态。长按元数据编辑保持不变，验证与左滑手势共存。灰显（不可用）行同样可左滑删除。播放中删除不特殊处理。

**Blocked by:** 01: 分版本删除链路与权限.

**Status:** resolved

- [ ] 本地音乐页歌行左滑露红色「删除」按钮，样式对齐下载页
- [ ] 本地专辑详情页歌行同样可左滑删除
- [ ] 单击播放、长按编辑元数据与左滑删除三者共存不冲突
- [ ] Android 10+ 删除仅系统框一次确认，无 App 叠加框
- [ ] Android ≤28 删除前 App 确认框，取消则不删
- [ ] 删除成功行即时消失、专辑分组/计数更新、重启后不复活（快照已落盘）
- [ ] 删除失败 Toast 提示并刷新行状态
- [ ] 灰显（文件不可用）行可左滑删除
- [ ] 删除正在播放的歌不主动停止，真断走「文件不可用」链路
- [ ] 飞行模式下可删除（纯本地、零网络）

## Comments

- 2026-11-14 完成。提交 4ad4a38（合并 ea98f00），7 个文件：新增 `data/local/LocalDeleteRules.kt`（`songsAfterDelete` / `needsAppDeleteConfirm` 纯规则）与 `LocalDeleteRulesTest.kt`；改 `LocalSongRow.kt`（加 `modifier` / `swipe`，`SwipeRevealRow(rowKey = song.id)` 包裹，`combinedClickable` 一行未动、不自绘背景）、`LocalMusicScreen.kt`、`LocalAlbumScreen.kt`、`LocalMusicViewModel.kt`、`ui/navigation/AppNavigation.kt`。两页歌行左滑露红色「删除」（88dp / `DangerRed` / 白字 14sp，样式由组件写死，按钮点击先收起再执行），页面级 `RowSwipeRevealState` + `rememberLazyListState()` 滚动收起、歌曲/专辑视图切换收起；权限被关时不传 `swipe`（文件真不可用的灰显行照常可删）。导航层无条件注册双 launcher（`StartIntentSenderForResult` 拉 30+/29 系统框、`RequestPermission` 拉 ≤28 WRITE）并渲染**唯一**一份 ≤28 App 确认框（标题 `删除本地歌曲`、正文 `「${song.displayTitle()}」将从设备存储删除，其他应用也不再可见。`、`删除` / `取消`，两个 Text 不设 color 走 M3 默认）；放常驻宿主是因为页面级 launcher 在导航到专辑页时随组合注销会丢回调。`notice` 的 Toast 消费上移导航层并删掉本地音乐页原消费点 → **修掉专辑页删除失败静默/迟到的 bug**，两页与顶栏重扫提示统一且不双弹。删除成功即 `songsAfterDelete` 从整库 `_songs` 移除该行 + 快照原子落盘（照 `markUnavailable` 范式、无新锁），专辑分组/卡片计数/专辑页列表与头部计数纯派生自动更新；**删空自动返回**绑定删除成功事件（`LocalDeleteSuccess` 带专辑键与**同步算好**的剩余可见行数——`albums` 派生异步重算，消费时读它会拿到旧值）+ 当前路由专辑键匹配，不做「album == null 就 pop」的通用观察。专辑页补 `onAlbumPageEntered()`（只核对权限、刻意不刷新）+ `ON_RESUME` 观察，修「进程被杀直接恢复到专辑页 → `permissionGranted` 停在初值 false → 整库误灰显、删除露不出」。失败 → Toast「删除失败」+ `markUnavailable`；取消 → 一行不动；播放中删除零特殊处理（真断走既有 `unavailableLocalSongId` → 文件不可用链路）。不清理覆盖层 / 排除清单 / 队列快照。JVM 行为测试 8 例已写、未执行（本仓规约：AI 不编译）。**已知取舍（不藏）**：① **在途刷新竞态**——静默刷新的整库查询若在删除前执行、提交在删除后，被删行会复活并重新落盘，直到下次刷新才消失；窗口毫秒级、入口刷新只在进页面触发，按裁决刻意不缓解（不取消 refreshJob、不维护已删 id 黑名单）。② **长按 + 左滑仓内无先例**——按住不动 >400ms 再横滑时长按先触发弹出元数据面板、行可能停在半开位（滚动可收起）；按最简包裹实现，未加 `enabled` 回传机制，该失败模式留真机验证记录。③ **`SendIntentException` 边缘**——系统框拉起自身失败时契约回 `RESULT_CANCELED` + `EXTRA_SEND_INTENT_EXCEPTION`，接缝按契约归一为 `Cancelled` → 静默不动、无 Toast；未做 extra 区分，罕见、记录未修。④ `markUnavailable` 对已灰显行是 no-op（状态本就正确，可接受）。⑤ 快照写失败只记日志，「重启不复活」不可静态观测、需真机。⑥ 删空返回的专辑键取删除时刻整库里的现行行，swipe 到确认之间改专辑名的极端场景键不匹配 → 不 pop（显示换名后/空态），可接受。⑦ 落盘异步（`viewModelScope`）与刷新/排除开关共用写锁，最后写入胜出。勾选项为设备级验收清单，留待人工编译后真机逐项确认。
- 2026-11-14 评审修复：提交 2045f7a（接缝与纯规则 + 测试）与 2961096（ViewModel 与导航层），落在 `ticket/lmd-review-fixes`（base 1132f31）。① FIX1 硬 bug——`LocalDeleteResult.Failure` 增 `fileUnavailable`（仅 `normalizeDeleteRows` 非正行数置位，其余失败构造保持默认 false），`applyDeleteResult` 失败一律 Toast「删除失败」、仅 `fileUnavailable` 才 `markUnavailable`：拒绝写入权限 / provider 异常 / 异常框结果码不再把可播文件永久灰显（灰显落盘且阻断播放，要正面证据；真不可播仍走 `unavailableLocalSongId` 链路、下次刷新自然消失）。② FIX2——`onSystemDeleteDialogResult` 增 `launchFailed` 形参，导航层 launcher 检查 `EXTRA_SEND_INTENT_EXCEPTION` 置位；发送失败不喂接缝、直接 `Failure()` → Toast 且不灰显，不再被当成用户取消静默吞掉（接缝 CANCELED→Cancelled 归一未动，真取消仍静默）。③ FIX3——两个回调 `pendingDeleteSong == null`（进程死亡丢状态）时先置「删除失败」再返回，不再静默。④ FIX4——新增纯函数 `remainingVisibleInAlbum(songs, albumKey, excludedFolders: Collection<String>)`（`excludingFolders` 后按 `albumKey` 清点），VM 删空判据改调它；原同义反复测试改为直接调接缝、对独立字面量断言（2 可见+1 排除→2、删最后一首可见→0、全排除→0、别专辑不计、空列表→0），保留 `songsAfterDelete`→`groupIntoAlbums` 端到端测试且期望值全字面量。⑤ FIX5——`删除失败与用户取消都不动列表` 三处 `assertSame` 改 `assertEquals`（契约是内容级「一行不动」，不绑定返回实例），删 `assertSame` import。JVM 测试 `LocalMusicDeleterTest` 13 例 + `LocalDeleteRulesTest` 9 例（已写未执行，本仓规约 AI 不编译）。FIX6 文档与注释同步见 spec / 票据03 的评审修复提交。Status 与勾选项未动，留待编排层归档。
- 2026-11-14 归档：resolved。实现 4ad4a38（合并 ea98f00）；评审修复 2045f7a/2961096/591fbbc（合并 47ce6f6）。双轴 review 查出的硬伤已全部修复：① **拒绝写入权限曾无条件 `markUnavailable` 并落盘**，把完好可播的文件永久灰显（专辑页不刷新 → 整会话且重启后仍不可点）——现仅在确认媒体库行已不在时灰显，其余失败只 Toast；② **IntentSender 拉起自身失败曾被归一为用户取消而静默**（违反用户故事 8）——导航层现检查 `EXTRA_SEND_INTENT_EXCEPTION` 并标成拉起失败，弹「删除失败」、行不灰显；③ **进程死亡丢 `pendingDeleteSong` 曾静默返回**——两个回调现给提示。另把删空自动返回的判据抽为纯函数接缝 `remainingVisibleInAlbum`（原测试与生产同源重算、属重言式），测试改独立字面量断言，失败/取消的 `assertSame` 改内容级 `assertEquals`。JVM 测试 9 例已写、未执行。勾选项为设备级验收清单，照先例保持未勾，留待人工编译后真机逐项确认。