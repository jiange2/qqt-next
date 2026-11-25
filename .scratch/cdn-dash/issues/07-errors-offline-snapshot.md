# 07: 错误态、快照缓存与离线展示

**What to build:** 卡片上的错误要能分清「凭证失效」「权限不足」「网络错误」，让使用者知道去控制台改什么；花费接口无权限只在花费页提示「费用权限未开启」。每次成功取数把领域结果连同抓取时间写入 AccountStore 快照；断网或请求失败时展示上次成功快照并标明数据抓取时间，而不是一片空白。单账号错误只降级该卡片，其他账号与整页照常；下拉刷新失败保留旧数据并提示。

**Blocked by:** 03

**Status:** resolved

- [x] 三类错误在卡片上文案可区分，不合并成「出错了」
- [x] 失败账号显示上次快照（若有）+ 抓取时间；无快照时显示错误态而非空白
- [x] 快照按模块（今日/7 天/资源包）独立存取，失败不清除旧快照
- [x] 下拉刷新失败不导致页面清空
- [x] AccountStore 快照读写有 JVM 测试：带抓取时间、失败不覆盖旧快照（内存实现）
- [x] 离线飞行模式下的表现列入人工验收清单

## 实现备注

- 新增 `data/aliyun/SnapshotStore.kt`：把「账号 + 模块 → 快照读写 + 抓取时间」收口。持久化仍只走 `AccountStore`（未改接口）；`save/saveToday` 只在成功取数后调用，写盘异常返回 false 不抛出；`load/loadToday(accountId, module, decode)` 解码失败返回 null 且不删除任何东西。后续 7 天/资源包/账单复用 `load` + 各自编解码即可。
- `DashboardViewModel` 仅做追加：构造内建 `SnapshotStore(accountStore)`（工厂签名未变）；`AccountFetch`/`AccountTodayState` 新增 `snapshotFetchedAtMillis`/`weekSnapshotFetchedAtMillis`；`refreshAccount` 开始时若无数据先亮快照（`showSnapshotIfNothingToShow`），各模块成功写快照并清空离线标注，失败时保留已展示数据、否则回落该模块快照，二者皆无则保持 null + `failure`。失败路径不写、不清快照；原有的 `supervisorScope` 账号隔离与「进行中忽略重复刷新」未动，所以下拉失败不会清空列表。
- 合并 `feat/cdn-dash`（已含票据 04）时 `DashboardViewModel.kt` 冲突，按双方意图合并：保留 04 的「账号内串行 今日→7 天、凭证失败提前返回」，把今日/7 天的成功写快照改走 `SnapshotStore`，失败收尾改为 `showTodayFailure`/`showWeekFailure`（凭证失败也走今日回落，旧数据不被抹掉）；7 天模块同样支持「失败回落快照 + 数据时间（离线）」，`WeekMetric` 增加离线文案。`DashboardScreen.kt` 自动合并，仅在 `WeekMetric` 追加离线分支。
- `ui/dashboard/FailureText.kt`：`label()`（凭证失效/权限不足/网络错误）+ `actionHint()`（去控制台检查 AK / 补 RAM 的 CDN 读权限 / 检查网络后下拉重试）；`DashboardScreen` 删掉原私有 `label()`，卡片右上保留标签、卡片底部追加提示句。
- 离线标注：`AccountTodayState.today` 可能是实时值或快照值，`snapshotFetchedAtMillis != null` 时文案为「数据时间 HH:mm（离线）」（`domain/Time.kt` 新增 `formatBeijingHourMinute(epochMillis: Long)` 重载，固定北京时间）。无数据且失败时文案为「本次未取到数据」而不是「暂无数据」。
- 花费页、`AccountStore` 与花费侧无权限提示均未改（归票据 08）。
- 测试（只写未跑，本机 JDK8/无 SDK/无 gradle）：`SnapshotStoreTest` 6 例（带抓取时间、模块隔离、账号隔离、新成功覆盖、解码失败不清旧快照、无快照返回空）；`DashboardViewModelTest` 增 5 例（今日失败回落快照 + 抓取时间、7 天失败回落 7 天快照、刷新失败保留已展示数据与旧快照、无快照失败为错误态、成功覆盖并清离线标注）；`FailureTextTest` 2 例（三类标签互异、提示各异且可操作）。

## 人工验收（真机，未执行）

- [ ] 飞行模式下冷启动：卡片显示上次成功数据并标「数据时间 HH:mm（离线）」，不是空白；顶部/卡片不因失败消失
- [ ] 飞行模式下有数据时下拉刷新：列表与数字保持不变，错误标签为「网络错误」并提示「检查网络后下拉重试」
- [ ] 从未成功取过数（新装 + 飞行模式）：卡片显示「本次未取到数据」+ 网络错误提示，而不是空白或崩溃
- [ ] 把 AK 改错 / 在控制台禁用该 RAM 用户：卡片标「凭证失效」+ 去控制台检查 AK 的提示，其他账号照常出数
- [ ] RAM 用户去掉 CDN 读权限：卡片标「权限不足」+ 补授权提示；花费页仍由票据 08 的「费用权限未开启」负责
- [ ] 恢复网络后下拉：数字更新为实时值，离线标注消失，抓取时间随之刷新
- [ ] 杀进程重启后断网：仍能展示四个模块上次成功的快照与各自抓取时间（7 天/资源包/账单随 04/05/08 合并后一并验）
