# 06: 顶部汇总与缺失计数

**What to build:** 看板顶部两数：所有账号今日累计、所有账号最近 7 天总量，各带口径小字（今日为未结算、7 天为已结算日）。汇总排除停用账号与取数失败的账号；有账号失败时标注「N 个账号数据缺失」，让使用者知道合计不是全量。部分失败不得让汇总整体消失。汇总为纯函数。

**Blocked by:** 04

**Status:** resolved

- [x] 顶部出现今日累计与 7 天总量两数，口径小字正确
- [x] 停用账号不计入汇总
- [x] 失败账号不计入汇总且显示「N 个账号数据缺失」，N 与实际失败数一致
- [x] 全部账号失败/无账号时汇总显示合理（0 或空态），不崩溃
- [x] 纯函数 JVM 测试：排除停用与失败、缺失计数、全零

## 实现说明

- 汇总纯函数在 `domain/TopSummary.kt`：`summarizeTop(List<TopSummaryAccount>)` 输出今日合计、7 天合计、缺失账号数；停用跳过、失败排除并计缺失、null 按 0、空列表全零。
- 今日与 7 天共用 ViewModel 现有的单个失败状态（`AccountFetch.failure` 是两路失败的并集），因此缺失数是账号级并集，只计一次；两路合计也不含该账号（即使还留着上次成功的旧值）。未改刷新编排。
- `DashboardViewModel` 只新增 `DashboardUiState.topSummary` 与 combine 内一段映射；`DashboardScreen` 在 LazyColumn 首个 item 加顶部两数（随列表滚动，不遮挡下拉刷新）。
- JVM 测试 `domain/TopSummaryTest.kt` 6 例，字面量断言。本机未编译、未跑测试。
