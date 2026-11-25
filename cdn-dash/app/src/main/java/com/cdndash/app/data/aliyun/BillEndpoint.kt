package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.MonthlyBill
import java.time.YearMonth

/**
 * 账号账单端点（票据 08 实现）。
 *
 * 计划参数：`QueryAccountBill`、`Granularity=MONTHLY`，逐个月份请求（host 为
 * `business.aliyuncs.com`，由 [AliyunApiImpl] 注入另一个 [AliyunRpcClient]）；
 * 主数取 `PretaxAmount`，明细取现金与代金券。当月由票据 08 剔除。
 *
 * 本票据只留接缝：票据 08 只改这个文件。
 */
class BillEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun monthlyBills(
        account: AccountCredentials,
        months: List<YearMonth>,
    ): ApiResult<List<MonthlyBill>> =
        TODO("票据 08：近 12 个月账号花费（QueryAccountBill，Granularity=MONTHLY）")
}
