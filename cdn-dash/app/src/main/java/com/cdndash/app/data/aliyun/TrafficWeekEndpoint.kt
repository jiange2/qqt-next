package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.DayTrafficPoint
import java.time.Instant

/**
 * 已结算 7 天端点（票据 04 实现）。
 *
 * 计划参数：`DescribeDomainTrafficData`、`Interval=3600`、时间窗为北京时间
 * 「今天-7」00:00 至「今天」00:00；响应的小时点按北京自然日聚合、丢弃今天。
 *
 * 本票据只留接缝：票据 04 只改这个文件，不碰 [AliyunApiImpl]。
 */
class TrafficWeekEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun settledDays(
        account: AccountCredentials,
        now: Instant,
    ): ApiResult<List<DayTrafficPoint>> =
        TODO("票据 04：已结算 7 天走势（DescribeDomainTrafficData Interval=3600 + 北京自然日聚合）")
}
