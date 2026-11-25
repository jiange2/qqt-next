package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.MonthlyBill
import com.cdndash.app.domain.ResourcePackage
import com.cdndash.app.domain.TodayTraffic
import java.time.Instant
import java.time.YearMonth

/**
 * 唯一网络接缝（spec「唯一网络接缝 AliyunApi」）。
 *
 * 所有 UI 与 ViewModel 只依赖本接口：入参是账号凭据与时间窗，出参是领域对象；
 * 签名、HTTP、JSON 解析、错误归一都藏在实现（[AliyunApiImpl]）与 [AliyunRpcClient] 里，
 * 调用方永远不拼参数、不解析 JSON。
 *
 * 四个方法在本票据一次定义完，后续票据只填实现、不改形态：
 * - [todayTraffic]：票据 03 实现（本票）。
 * - [settledDays]：票据 04 实现（[TrafficWeekEndpoint]）。
 * - [resourcePackages]：票据 05 实现（[PackageEndpoint]）。
 * - [monthlyBills]：票据 08 实现（[BillEndpoint]）。
 *
 * 失败以 [ApiResult.Failure] 返回而不是抛异常；调用方仍应捕获意外异常（如解密失败），
 * 保证单账号错误不传染给其他账号。
 */
interface AliyunApi {

    /**
     * 今日累计：北京时间今天 00:00 至 [now] 的边缘出流量。
     * 数据源 `DescribeDomainTrafficData`，不传 `DomainName`（账号全量）、`Interval=300`（ADR 0016）。
     */
    suspend fun todayTraffic(account: AccountCredentials, now: Instant): ApiResult<TodayTraffic>

    /**
     * 最近 7 个已结算日（不含今天）的按北京自然日聚合点。
     * 票据 04 实现：`Interval=3600`，端上按北京日聚合、丢弃今天的点。
     */
    suspend fun settledDays(account: AccountCredentials, now: Instant): ApiResult<List<DayTrafficPoint>>

    /** 账号下的 CDN 流量包（`cdnflowbag`）条目，票据 05 实现。 */
    suspend fun resourcePackages(account: AccountCredentials, now: Instant): ApiResult<List<ResourcePackage>>

    /** 逐月的账号花费，[months] 为要取的完整账期（不含当月），票据 08 实现。 */
    suspend fun monthlyBills(account: AccountCredentials, months: List<YearMonth>): ApiResult<List<MonthlyBill>>
}
