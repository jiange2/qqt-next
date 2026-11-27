# 10: 资源包预计可用天数

**What to build:** 看板卡片摘要与资源包明细页，对每个（区域，到期日）组按「本区域最近 7 个已结算日的日均边缘出流量」估算还能用多少天；明细页在 7 天为实时数据时附「预计 MM-dd 用完」。区域拆分取 7 天响应的 `DomesticValue`/`OverseasValue`，看板零新增请求；明细页进页自取一次 7 天（+1 请求），失败回落 `WEEK_TREND` 快照并只给天数、不给日期。未生效、区域未知、旧快照没有区域字段时不显示估算；本区域 7 天为 0 时显示「近 7 天无流量，暂不估算」。比较用未取整原始天数，不足 0.5 天显示「不足 1 天」，超过 999 天显示「>999 天」。口径与边界见 ADR 0018。

**Blocked by:** 04（7 天）、05（资源包分组）

**Status:** resolved

- [x] 7 天端点解析 `DomesticValue`/`OverseasValue`，按北京日聚合时一并求和
- [x] `WEEK_TREND` 快照编解码带区域字段且兼容旧载荷（缺字段按 0）
- [x] 纯函数：区域日均、可用天数、到期比较、取整/封顶、北京自然日用完日
- [x] 看板卡片每行追加短标签，区块在存在估算时说明口径
- [x] 明细页每行追加天数（实时时附日期），页头补口径说明
- [x] 明细页 ViewModel 取 7 天、写快照、失败回落并标记离线
- [x] JVM 测试：聚合区域拆分、端点解析、日均/边界/文案、快照兼容、明细页成功与回落
- [ ] 真机验收（真实账号 + 弱网/断网）

## 实现备注

- 领域：`domain/Runway.kt` 新增纯函数。`regionalDailyAverage` 把「区域未知 / 旧快照无区域字段」归为 `Missing`（静默），只有本区域 7 天为 0 才 `NoTraffic`；分母恒为 7。`estimatePackageRunway` 对未生效/零余量/`Missing` 返回 `Hidden`，否则用 `usableRemaining ÷ 区域日均`：先用**未取整**原始天数与 `expiresAt` 比较（≥ 剩余到期天数 → `ExpiresFirst`），再做 `<0.5 → UnderOneDay`、`>999 → OverLimit`、四舍五入 → `Days(北京日 + N)`。`expiresAt == EPOCH`（到期未知）跳过比较。
- `PackageGroup` 补 `expiresAt`（组内最早到期时刻，保守比较；`EPOCH` 视为未知），由 `groupPackages` 填充；不影响原有分组与排序。
- 数据：`TrafficWeekEndpoint` 在同一次响应里解析 `DomesticValue`/`OverseasValue`（缺失按 0）；`aggregateSettledDays` 用 `DailyTotals` 分别累加总量/内地/海外，今天与窗口外仍整体丢弃。`WeekTrafficSnapshotCodec` 新增两个默认 0 的字段，旧 `WEEK_TREND` 载荷照常解码；旧快照因区域全 0 被识别为 `Missing`，升级后首次成功取数前不显示估算（ADR 0018）。
- UI：`PackageSummarySection` 接收 `week`，为每个展示组算短标签（「约可用 N 天」「不足 1 天」「>999 天」「到期前用不完」「近 7 天无流量，暂不估算」），有实际天数/到期结论时整块显示一次「天数按区域近 7 天日均估算」；`PackageGroupMeter` 的脚注在包数/未生效后追加标签。明细页 `PackageGroupRow` 追加「约可用 N 天 · 预计 MM-dd 用完」，页头补同一句口径；`weekSnapshotFetchedAtMillis != null` 时只给天数、不给日期。
- 明细页 `PackageDetailViewModel`：进页在资源包之后串行取一次 `settledDays`（每次进入 +1 请求，看板与 5 分钟定时器不变），成功写 `WEEK_TREND` 快照并清离线标记；失败回落该快照并标记离线，**不**设置页面级错误态；无凭据时包与 7 天都走各自回落。进页先把 `PACKAGES` 与 `WEEK_TREND` 两份快照亮出来。
- 测试（只写未运行）：`RunwayTest`（分母恒 7、区域不串用、旧快照静默、零流量、不足 1 天、>999、到期先于用完、等号、未取整比较、未知到期、未生效/零余量、北京日、文案）、`WeekAggregationTest` 增区域求和与今天丢弃、`TrafficWeekEndpointTest` 增区域解析与缺字段 0、`WeekTrafficSnapshotCodecTest`（往返、旧载荷、损坏）、`PackageDetailViewModelTest` 增 7 天成功写快照与失败回落且不污染包错误态。
- 2026-11-27 按用户显式要求由 AI 执行 `:app:testDebugUnitTest :app:assembleRelease`（JDK17 + ANDROID_HOME）：154 例全部通过，release APK 签名正常（CN=qqt-music）。此前实现阶段的「未编译、未跑测试」记录仅指当次收尾。DCDN 流量未纳入估算：`dcdnpaybag` 若同时被 DCDN 消耗，实际可用天数可能更短（ADR 0018）。
- 2026-11-27 真机「没有预测」修复：资源包接口 `Region` 返回区域码 `CN`（海外 `OVERSEAS`），而估算只认「中国内地/海外」，所有组被判「区域未知」静默隐藏。`domain/ResourcePackage.kt` 新增 `canonicalRegion`（CN/CHINA/MAINLAND/DOMESTIC/中国内地/中国大陆/中国 → 中国内地；OVERSEAS/OVERSEA/INTERNATIONAL/海外/国际 → 海外；其余原样），`groupPackages` 分组前归一，实时数据与旧快照同时生效；区域标题也随之显示中文区域名。新增 `PackageGroupingTest` 两例（归一合并、未知值原样）。

## 人工验收（真机，未执行）

- [ ] 卡片资源包行出现「约可用 N 天」，并有「天数按区域近 7 天日均估算」说明
- [ ] 中国内地包与海外包分别用各自区域的日均，数字不串用
- [ ] 包在到期前用不完时显示「到期前用不完」，不出现夸张的大天数
- [ ] 明细页显示「约可用 N 天 · 预计 MM-dd 用完」；断网进页时只显示天数
- [ ] 刚买未生效的包、没有区域拆分的旧快照都不出现估算，界面无报错
