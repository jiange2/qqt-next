# 04: 本地专辑页编辑模式（批量管理扩展）

**What to build:** 本地专辑页（`local_album/{lid}`）加与本地音乐页同构的编辑模式。入口：顶栏 ⋮ 菜单只含「批量管理」一项（`LocalAlbumTopBarMenu`，`permissionGranted = false` 禁用）；为此解除 `MusicTopBar`「带返回键不渲染 actions」限制——页面自带 actions 时接管右侧操作区、带返回键也渲染，否则无返回键页面维持全局搜索（唯一调用点核实，其他页面零影响）。编辑态开关 / 勾选集合（`Set<LocalSong.id>`）/ 批量面板开关提升 `AppNavigation`，`remember(localAlbumEntry)` 以 `local_album` 返回栈条目为键（与本地音乐页各自独立、互不相通，离开本页键置空重建）。行交互与本地音乐页完全同构：序号位换勾选框、点行 / 长按 = 切换勾选（不跳播、不弹单首面板）、左滑收起（`swipe = null`）、灰显行不可选；顶栏整条换 `LocalMusicEditTopBar`（取消 / 已选 n 首 / 全选·取消全选，全选 = 本专辑全部可用行）；底栏复用 `LocalMusicBatchBar`（编辑信息、0 选禁用）；面板复用 `LocalBatchEditSheet`（专辑 / 专辑艺术家、留空 = 不改、两空保存禁用）；保存走同一 `saveBatchMetadata` 一次提交，Toast「已修改 n 首」经 notice、点保存即退出编辑模式。**跟随导航（grilling Q3 方案 A）**：提交前用纯函数 `batchAlbumNewKeys` 同步算目标键集合——非全选（含灰显行未被选）或键不变 → 空集，留在原页、列表自然变短；单键 → 页面跟随新专辑：等 `albums` 派生就绪（`first { 目标专辑含选中行 }`，1000ms 超时兜底 pop，等待期间校验条目身份防用户已离开）后 `LocalAlbumNav.albumName` 交接新名、`navigate` 新 lid 并 `popUpTo(local_album, inclusive)` 替换条目（返回直接回本地音乐页，不闪「专辑已不存在」）；多键（文件夹兜底专辑 + 只填专辑名 + 成员专辑艺术家不同 → 拆组）→ 无唯一跟随目标，`popBackStack()` 回本地音乐页。退出路径沿用：取消 / 系统返回（面板先收再退编辑，`BackHandler` 不退出页面）/ 切页（结构保证）/ 点保存 / 权限收回（两页编辑态一并退出）。硬边界：专辑卡片墙不做整张勾选；仍只写覆盖层（ADR 0020）；不新增 ADR。文档随实现同步：CONTEXT.md「编辑模式」词条改两页口径、spec 入口 / Out of Scope / Testing 段对齐、doc/ui.md 相应小节。

**Blocked by:** 01, 02

**Status:** open

- [ ] 专辑页顶栏右侧出现 ⋮（与返回键共存），菜单只有「批量管理」；未授权音频权限禁用；其他带返回键页面无变化
- [ ] 进入编辑模式：勾选框替换序号、点行 / 长按勾选不跳播不弹单首面板、左滑收起、灰显行不可选
- [ ] 顶栏取消 / 已选 n 首 / 全选正确；全选 = 本专辑全部可用行（灰显跳过）；已全选再点清空
- [ ] 0 选「编辑信息」禁用；≥1 选可点；批量栏不遮挡 MiniPlayer
- [ ] 面板双字段初始为空；两空保存禁用；留空字段不改；保存 Toast「已修改 n 首」+ 即退编辑
- [ ] 部分勾选搬出：留在原页、列表变短、专辑头歌曲数与封面随之更新（含灰显行未被选而非全选的情形）
- [ ] 全选 + 改名：页面跟随新专辑（顶栏标题 / 封面 / 列表更新），系统返回回本地音乐页而非旧专辑；全程不闪「专辑已不存在」
- [ ] 文件夹兜底专辑全选只填专辑名且成员专辑艺术家不同 → 拆成多张、自动返回本地音乐页
- [ ] 输入与当前展示值相同（含首尾空白）→ 留在原页、不发生导航（有标签专辑口径；文件夹兜底专辑键不同，见 Comments 评审裁定）
- [ ] 取消 / 系统返回退出编辑并清空勾选（面板开着先收面板），不退出页面
- [ ] 离开本页再回来是正常态；两页勾选互不相通（音乐页勾选后进专辑页无残留，反之亦然）
- [ ] 编辑中权限被收回 → 退出编辑模式 + 整库灰显
- [ ] 专辑卡片墙无整张勾选入口（Out of Scope 未动）

## Comments

- 2026-11-14 grill-with-docs 定稿：Q1 专辑页 ⋮ 菜单只含「批量管理」（同本地音乐页入口形态；解除 TopBar「带返回键不渲染 actions」限制——grep 核实 `MusicTopBar` 唯一调用点在 `AppNavigation`，其余页面不传 actions、行为不变）；Q2 与本地音乐页完全同构（行交互 / 编辑顶栏 / 底栏 / 同一批量面板 / 留空 = 不改 / Toast + 即退），不加字段、不带删除；Q3 方案 A——全选改名把当前专辑键改没时页面跟随新专辑（等价自动进入新专辑），部分勾选搬出留在原页。用户「完全同意」。机制委托项：拆组多键无唯一目标时返回本地音乐页、跟随前等派生就绪防闪空态，属实现机制随答复确认。
- 2026-11-14 实现完成（待人工编译，本仓规约 AI 不跑 gradlew；提交哈希待入库后补）。改动七文件：`LocalAlbum.kt`（`albumKey()` 委托新纯函数 `albumKeyOf(album, albumArtist)`，文件夹兜底键推导同源复用）；`LocalMetadataEdit.kt`（`batchAlbumNewKeys`：非全选或键不变 → 空集留在原页；全选按「trim 非空输入 = 新展示值、留空 = 原展示值」逐行算新键——与 `batchAlbumOverride` 提交后展示值严格一致，含同标签清覆盖不改展示值的口径）；`LocalBatchFollowTest.kt` 新增 13 例（部分勾选 / 灰显阻断 / 留空 / 改名 / 只改艺术家 / 同值不动 / 同标签清覆盖后按标签键 / 文件夹专辑拆组字面量键断言 / 空列表 / albumKeyOf 一致性）；`TopBar.kt`（actions 非空即渲染、否则无返回键才显示搜索）；`LocalAlbumScreen.kt`（`editing` / `selection` / `onToggleSelect` 参数、`LaunchedEffect(editing)` 收左滑、编辑态 `swipe = null`、`LocalAlbumTopBarMenu`）；`AppNavigation.kt`（专辑页三态 `remember(localAlbumEntry)`、`exitLocalAlbumEditMode`、权限收回双页退出、专辑页编辑 `BackHandler`、topBar / actions / 批量底栏分支、专辑面板 onSave：先算 `batchAlbumNewKeys` 再 `saveBatchMetadata` 再退出，单键跟随走 `albums.first{...}` + `withTimeoutOrNull(1000)` + 条目身份守卫 + `popUpTo(inclusive)` 替换，多键 pop；编辑候选沿用整库可见歌曲派生、两处批量面板共用同一份）；`LocalSongRow.kt`（KDoc 编辑态适用范围补专辑页）。文档同步随本票：CONTEXT.md「编辑模式」词条（两页各自状态、勾选互不相通、专辑页全选口径与跟随语义）、spec.md 入口 / Out of Scope / Testing / Comments、doc/ui.md（TopBar / LocalAlbumScreen / AppNavigation 接线 / 批量面板小节）。勾选项为设备级验收清单，待人工编译真机逐项确认，照先例保持未勾；Status 推进与归档由编排层在评审后统一执行。
- 2026-11-15 双轴评审裁定（随编辑候选特性一并评审）：Spec 轴行为零缺陷，1 处口径裁定——「输入与当前展示值相同 → 留在原页」按键基判据执行：文件夹兜底专辑的展示名是文件夹名，输入它作专辑名会把键从 folder 换成 album，是真实换组（歌曲真的搬进一张新专辑键组），`batchAlbumNewKeys` 返回单键、页面跟随，符合 L3「键不变才留页」的机制定义；该验收行按有标签专辑理解，文件夹专辑此情形属预期跟随，真机验收时按此口径确认。Standards 轴无本票违规（弃用词修复在编辑候选侧）。