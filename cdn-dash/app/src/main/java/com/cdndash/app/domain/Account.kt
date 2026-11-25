package com.cdndash.app.domain

/**
 * 账号元数据。凭据（AccessKeyId/Secret）不在这个对象里：调用方永远只看到元数据，
 * 明文凭据只能经 AccountStore 按 id 单独读取（spec「唯一持久化接缝 AccountStore」）。
 *
 * @property sortOrder 手动排序序号，从 0 开始；看板顺序只由它决定，不随任何流量数据变化。
 */
data class Account(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val sortOrder: Int,
)

/** AccountStore 解密后返回给调用方的只读凭据；不参与序列化、不落盘、不进日志。 */
data class AccountCredentials(
    val accessKeyId: String,
    val accessKeySecret: String,
)

/** 快照缓存按「账号 + 模块」隔离；模块枚举在票据 02 定义，后续票据只填内容。 */
enum class SnapshotModule {
    /** 今日累计（今日 00:00 至查询时刻）。 */
    TODAY_TOTAL,

    /** 已结算的最近 7 天走势与合计。 */
    WEEK_TREND,

    /** 资源包余量视图。 */
    PACKAGES,

    /** 近 12 个月账号花费。 */
    MONTHLY_BILL,
}

/**
 * 某账号某模块最近一次成功抓取的结果。
 *
 * @property payloadJson 领域结果的 JSON 串，由后续票据的 API 层写入与读取。
 * @property fetchedAtMillis 抓取时刻（epoch millis），用于离线时标注数据时间。
 */
data class AccountSnapshot(
    val accountId: String,
    val module: SnapshotModule,
    val payloadJson: String,
    val fetchedAtMillis: Long,
)
