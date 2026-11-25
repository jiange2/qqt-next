package com.cdndash.app.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.Locale

/** 花费页展示的账期数量：最近 12 个已出账完整月（ADR 0017）。 */
const val BILLING_MONTH_COUNT: Int = 12

/** 花费快照有效期：24 小时内重进花费页不重复请求（ADR 0017 / User Story 32）。 */
const val BILL_CACHE_TTL_HOURS: Long = 24L

/** `SubscriptionType` 里表示预付费的取值；其余取值（如 `PayAsYouGo`）都是后付费。 */
const val PREPAID_SUBSCRIPTION_TYPE: String = "Subscription"

/**
 * 会被当作 CDN 的产品码：BSS 里 CDN 的 `ProductCode` 是 `cdn`，
 * 资源包商品码是 `cdnflowbag`，两者都要认，否则买包当月的标注会漏。
 */
private val CDN_PRODUCT_CODES: Set<String> = setOf("cdn", "cdnflowbag")

private const val MILLIS_PER_HOUR: Long = 60L * 60L * 1_000L

/**
 * 最近 [BILLING_MONTH_COUNT] 个已出账的完整自然月，按时间升序（最旧在前）。
 *
 * 当月剔除：以北京时间判断「现在是哪个月」（与流量日界同一口径，spec「数据口径」），
 * 不读设备时区；[now] 由调用方注入，测试可用固定时刻。
 */
fun recentBillingMonths(now: Instant): List<YearMonth> {
    val currentMonth = YearMonth.from(now.atZone(BEIJING_ZONE))
    return (BILLING_MONTH_COUNT downTo 1).map { currentMonth.minusMonths(it.toLong()) }
}

/**
 * 单条账单明细是否构成「预付费 CDN」证据：订阅类型为预付费且产品码属于 CDN。
 *
 * 只认接口返回的 `SubscriptionType` + `ProductCode`；任一字段缺失都返回 false，
 * 不按金额大小或产品名猜测（ADR 0017：无证据不标注，避免把尖峰错归因）。
 */
fun isPrepaidCdnBillLine(subscriptionType: String?, productCode: String?): Boolean {
    if (!subscriptionType.equals(PREPAID_SUBSCRIPTION_TYPE, ignoreCase = true)) return false
    val code = productCode?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return false
    return code in CDN_PRODUCT_CODES
}

/** 全年合计：12 个账期的应付金额之和，金额运算始终用 [BigDecimal]，不经过 Double。 */
fun totalBillAmount(bills: List<MonthlyBill>): BigDecimal =
    bills.fold(BigDecimal.ZERO) { total, bill -> total + bill.pretaxAmount }

/**
 * 快照是否仍在 24 小时有效期内。
 *
 * 抓取时间落在「未来」（换机或时钟回拨）按新鲜处理，避免整页陷入反复请求；
 * 没有抓取时间（从未成功）一律视为过期。
 */
fun isBillCacheFresh(fetchedAtMillis: Long?, nowMillis: Long): Boolean {
    if (fetchedAtMillis == null) return false
    return nowMillis - fetchedAtMillis < BILL_CACHE_TTL_HOURS * MILLIS_PER_HOUR
}

/**
 * 快照可直接使用需同时满足：账期窗口未变（跨月后 12 个月整体平移）且在 24 小时内。
 * 只要有一条不满足就重新取数，避免把上个月窗口的数据当成这个月展示。
 */
fun isBillCacheUsable(
    cachedMonths: List<YearMonth>,
    expectedMonths: List<YearMonth>,
    fetchedAtMillis: Long?,
    nowMillis: Long,
): Boolean = cachedMonths == expectedMonths && isBillCacheFresh(fetchedAtMillis, nowMillis)
