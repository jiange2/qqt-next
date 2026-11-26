# 05: 资源包余量与明细页

**What to build:** 卡片显示资源包余量摘要；点卡片进入该账号的资源包明细页。数据取 `DescribeCdnUserResourcePackage`，只接受 `CommodityCode` 为 `cdnflowbag`（CDN 流量包）的条目，不混入全站加速/OSS 的包。按（区域，到期日）分组：组内求和剩余量与总量、计数包数，按到期日升序；未生效（生效时间晚于当前）的组要可识别标注；不同区域的余量不相加、不合并。分组为纯函数。

**Blocked by:** 03

**Status:** resolved

- [x] 卡片出现资源包余量摘要；明细页按区域分组、组内按到期日升序
- [x] 同区域同到期日的多个包合成一行并显示包数
- [x] 未生效的组明确标注（如「未生效」），且不当作可用余量
- [x] 非 `cdnflowbag` 条目被排除；区域之间不求和
- [x] 纯函数 JVM 测试：分组、排序、计数、未生效标注、区域不相加、空列表
- [x] 点卡片进入明细页、返回看板，导航与账号选择正确

## 实现备注

- 端点 `data/aliyun/PackageEndpoint.kt`：`DescribeCdnUserResourcePackage`、Version `2018-05-10`（`2017-11-10` 会 InvalidVersion），字段名按文档取 `InitCapacity`/`CurrCapacity`/`StartTime`/`EndTime`/`Region`/`CommodityCode`/`InstanceId`，并兼容 `Total`/`Remaining`/`EffectiveTime`/`ExpireTime` 别名。不传 `Status`：默认只回 `valid` 会藏掉已耗尽/已关闭的包。只接受 `CommodityCode=cdnflowbag`，缺商品码一律排除；单条目对象/数组两种响应都接受；字段缺失按 0 / EPOCH / `未知区域` 兜底。
- 纯函数 `domain/PackageGrouping.kt`：`groupPackages` 按（区域，北京自然日到期日）分组，组内求和剩余/总量并计数，按到期日升序（同日以区域名兜底稳定排序）；`notYetEffective` 取组内最早生效时刻是否晚于 `now`（即整组都没生效），`usableRemainingBytes` 对未生效组记 0。`packageGroupsByRegion` 供卡片摘要与明细页分区（区域内仍按到期日升序，区域间不做任何求和）。
- 卡片摘要 `ui/packages/PackageSummary.kt`：每行带区域名、到期日、剩余/总量；按区域轮转挑选展示组，**每个区域至少出现一组**，超出的显示「还有 N 组」（评审修正：不做全局取前 2 组，避免整块隐藏另一区域）；未生效组显示「未生效」且剩余按 0 呈现。卡片整卡可点，进入明细页。明细页 `PackageDetailScreen` 改为真列表 + 顶栏返回，区域分区、每行到期日/包数/剩余/总量/未生效标注；失败给重试。
- 导航：新增 `ui/navigation/PackageRoutes.kt`（`packages/{accountId}` + `packageDetailRoute`），`AppNavigation` 只做最小追加（顶栏在明细页显示返回箭头与固定标题）。
- `DashboardViewModel`：冷启动与下拉刷新时在今日累计之后串行取 `resourcePackages`（账号间仍并发且限 4；spec 规定账号内串行），**5 分钟定时器不重取资源包**（评审修正：资源包只按需/下拉取）；资源包与今日累计的错误/旧值互不覆盖；成功后写 `SnapshotModule.PACKAGES` 快照，内容为原始包事实（`PackageSnapshotCodec`），「未生效」在展示时按当时时刻重算；请求失败时回落该快照并标「数据时间 HH:mm（离线）」（票据 07 接入后）。
- 测试（只写未运行）：`PackageGroupingTest`（空列表、合并计数、到期日升序、未生效/可用余量、区域不相加、排除非 cdnflowbag、北京日切分、区域分区排序）、`PackageEndpointTest`（Action/Version/不传 Status、cdnflowbag 过滤、解析、字段缺失、空/缺节点、单对象）、`PackageRefreshTest`（刷新取包并写 PACKAGES 快照、包失败不污染今日与其他账号）。`DashboardViewModelTest` 的请求计数改为每次刷新 3（今日 + 7 天 + 资源包）。
- 第二次 merge `feat/cdn-dash`（票据 07/08）：与票据 07 的 `SnapshotStore` 合并——今日/7 天沿用其快照回落、「数据时间」与失败收尾，资源包作为账号内最后一步复用 `SnapshotStore` 写 `PACKAGES` 快照并统一结束 `isRefreshing`；卡片同时保留票据 07 的离线标注/错误提示与票据 05 的资源包摘要、点击下钻；删除与 `FailureText.kt` 重复的本地 `label()` 及未使用的 `MetricPlaceholder`。
- 本机 JDK 8、无 Android SDK 与 gradle：**未编译、未跑测试**；也**未对真实 OpenAPI 验证**（无真实只读 AK）。
- 2026-11-25 真机实测修正（用户反馈资源包提取不到，官方文档求证）：新版 CDN 资源包购买入口商品码已是 `dcdnpaybag`（CDN/全站加速共享），只过滤 `cdnflowbag` 会把新包全部丢掉。改为双端点合并：CDN `DescribeCdnUserResourcePackage`（2018-05-10，主端点，失败即整体失败）+ DCDN `DescribeDcdnUserResourcePackage`（2018-01-15，补充端点，失败容忍），按 `InstanceId` 去重；每端点按 `Status=valid/exhaust/closed` 各查一次（原「不传 Status」实际只回 valid，与备注意图不符，此次一并修正）；接受 `cdnflowbag`/`dcdnpaybag`，`dcdnpaybag` 按 `InitCapacityBaseUnit`/`CurrCapacityBaseUnit` 非 Byte 排除 HTTPS 请求数包（单位字段缺失容忍放行）。冷启动资源包请求数 1 → 6，相关测试计数已同步。

- 2026-11-25 用户裁定：已用完的流量包不展示。三态查询改回只查 `Status=valid`（已耗尽/已关闭不再列出），并在解析时排除剩余量 ≤ 0 的条目（valid 状态可能滞后于余量归零）。冷启动资源包请求数 6 → 2（双端点各 1 条），相关测试计数已同步；字段缺失兜底测试的 sparse 条目补 `CurrCapacity` 以免被新排除规则丢弃。
- 2026-11-25 用户再裁定：改为按到期时间取包，去掉 `Status=valid` 过滤条件。该接口只有 `Status`（valid/exhaust/closed，默认 valid）枚举参数、没有时间参数，故请求不带 `Status`，客户端合并后只保留 `EndTime` 晚于当前的包（缺失 `EndTime` 视为不可证明未来，同样排除）；请求数不变（每账号 2 条）。用完的包不在端点剔除：由 `groupPackages` 在合并分组后隐藏整组剩余量为 0 的组（单包用完但组内仍有剩余时保留，包数照实计数）。

- 2026-11-26 用真实账号核实「2T 组合只显示 1T」：接口 `Status` 缺省只返回 valid，**已耗尽的包不再返回**（`Status=exhaust` 才列出，`exhausted` 会被 CDN 端点判 InvalidStatus、DCDN 静默空）。修复为每端点 valid + exhaust 两轮，合并后再按（区域，到期日）分组求和；已耗尽包计入组总量、剩余 0，整组用尽仍隐藏。同账号实测：1T（余 553.3G）+ 1T 已耗尽（同到期日 2026-12-15）合并回 2T；另一 1T 已耗尽（到期 2027-09-07）整组隐藏；50G 单独成组。
- 2026-11-25 用户反馈「同组应 2T 只剩 600G 却只显示 1T」排查加固：到期时间缺失/解析失败（EPOCH）不再当作已过期丢弃，按未知到期保留；容量字段兼容千分位逗号；无 InstanceId 的兜底 id 带上 Action 前缀，避免双端点序号撞 id 被去重误删。分组键仍是（区域，北京自然日到期日）：两个包若到期日不同会拆成两行，这是现有口径，需在明细页按到期日区分确认。

## 人工验收（真机，未执行）

- [ ] 看板卡片出现「资源包余量」，不同区域的组分行展示、没有任何跨区域合计
- [ ] 资源包多于 2 组时显示「还有 N 组」
- [ ] 点账号卡片进入明细页，顶栏标题为「资源包明细」、返回箭头回到看板，账号正确
- [ ] 明细页按区域分区，区域内按到期日升序；同到期日多包合并为一行且包数正确
- [ ] 刚买未生效的包显示「未生效」且卡片摘要中其剩余按 0 计
- [ ] 账号缺少 CDN 读权限时卡片摘要与明细页显示「权限不足」，其他账号照常
