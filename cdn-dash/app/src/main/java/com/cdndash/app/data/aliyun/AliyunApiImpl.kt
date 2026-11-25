package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.DayTrafficPoint
import com.cdndash.app.domain.MonthlyBill
import com.cdndash.app.domain.ResourcePackage
import com.cdndash.app.domain.TodayTraffic
import java.time.Instant
import java.time.YearMonth

/**
 * [AliyunApi] 的实现：只做转发，不含签名、HTTP 与解析。
 *
 * 每个端点一个独立文件，后续票据各自改自己的端点文件：
 * 04 → [TrafficWeekEndpoint]，05 → [PackageEndpoint]，08 → [BillEndpoint]。
 * 本类尽量不被后续票据改动，减少合并冲突。
 *
 * @param cdnRpc CDN OpenAPI（`cdn.aliyuncs.com`）的 RPC 客户端。
 * @param bssRpc 费用中心（`business.aliyuncs.com`）的 RPC 客户端，默认与 [cdnRpc] 相同。
 */
class AliyunApiImpl(
    cdnRpc: AliyunRpcClient,
    bssRpc: AliyunRpcClient = cdnRpc,
) : AliyunApi {

    private val todayEndpoint = TodayTrafficEndpoint(cdnRpc)
    private val weekEndpoint = TrafficWeekEndpoint(cdnRpc)
    private val packageEndpoint = PackageEndpoint(cdnRpc)
    private val billEndpoint = BillEndpoint(bssRpc)

    override suspend fun todayTraffic(
        account: AccountCredentials,
        now: Instant,
    ): ApiResult<TodayTraffic> = todayEndpoint.todayTraffic(account, now)

    override suspend fun settledDays(
        account: AccountCredentials,
        now: Instant,
    ): ApiResult<List<DayTrafficPoint>> = weekEndpoint.settledDays(account, now)

    override suspend fun resourcePackages(
        account: AccountCredentials,
        now: Instant,
    ): ApiResult<List<ResourcePackage>> = packageEndpoint.resourcePackages(account, now)

    override suspend fun monthlyBills(
        account: AccountCredentials,
        months: List<YearMonth>,
    ): ApiResult<List<MonthlyBill>> = billEndpoint.monthlyBills(account, months)
}
