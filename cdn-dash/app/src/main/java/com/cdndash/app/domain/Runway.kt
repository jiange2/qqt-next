package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 预计可用天数的展示上限：超过就显示「>999 天」，不再给具体值。 */
const val MAX_RUNWAY_DAYS: Int = 999

/** 原始天数不足半天时显示「不足 1 天」；再小也没法用整天表达。 */
private const val MIN_VISIBLE_DAYS: Double = 0.5

private const val MILLIS_PER_DAY: Double = 86_400_000.0

/**
 * 某区域的最近 7 天日均（GLOSSARY「区域日均」）的三种结局。
 *
 * 「数据缺失」与「没有流量」必须分开：前者是旧快照/接口没给区域拆分，后者是真的零消耗，
 * 混在一起会把「不知道」显示成「不消耗」。
 */
sealed interface RegionalAverage {
    /** 区域未知，或 7 天列表缺少区域字段（旧快照）——不显示任何估算。 */
    data object Missing : RegionalAverage

    /** 区域数据存在但本区域最近 7 天为 0——估算没有意义。 */
    data object NoTraffic : RegionalAverage

    /** 本区域最近 7 个已结算日的日均边缘出流量（字节/天）。 */
    data class Available(val bytesPerDay: Double) : RegionalAverage
}

/**
 * 按区域拆分最近 7 天，得到该区域的日均消耗速度。
 *
 * 分母恒为 [SETTLED_WEEK_DAY_COUNT]：缺日已在聚合时补 0，不按列表长度除。
 * 只认 GLOSSARY「区域」的两个已知值；「未知区域」没有对应的流量拆分，返回 [RegionalAverage.Missing]。
 *
 * @param week 最近 7 个已结算日；null 表示本次没有 7 天数据。
 */
fun regionalDailyAverage(week: List<DayTrafficPoint>?, region: String): RegionalAverage {
    if (week.isNullOrEmpty()) return RegionalAverage.Missing
    val regionalBytes = when (region) {
        ResourcePackage.MAINLAND_REGION -> week.sumOf { it.domesticBytes }
        ResourcePackage.OVERSEAS_REGION -> week.sumOf { it.overseasBytes }
        else -> return RegionalAverage.Missing
    }
    if (regionalBytes > 0L) {
        return RegionalAverage.Available(regionalBytes.toDouble() / SETTLED_WEEK_DAY_COUNT)
    }
    val hasRegionalData = week.any { it.domesticBytes > 0L || it.overseasBytes > 0L }
    // 总量有数、区域字段全 0 = 旧快照或接口没返回拆分，不能当作零流量。
    return if (!hasRegionalData && weekTotal(week) > 0L) {
        RegionalAverage.Missing
    } else {
        RegionalAverage.NoTraffic
    }
}

/**
 * 一个资源包组的预计可用天数（GLOSSARY「预计可用天数」）。
 *
 * 口径：
 * - 只按本组所在区域计算，跨区域不相加；
 * - 分子用 [PackageGroup.usableRemainingBytes]：未生效的组按 0，直接 [Hidden]；
 * - 先与到期时刻比较，再取整：比较用**未取整**的原始天数，避免 5.7 天四舍五入成 6 天、
 *   而包只剩 6 天到期时被误判成「到期前用不完」；
 * - 到期时刻未知（[Instant.EPOCH]）时跳过比较，只给天数；
 * - 原始天数 < 0.5 显示「不足 1 天」，> 999 显示「>999 天」，其余四舍五入成整天。
 *
 * @param now 估算参照时刻，同时决定预计用完日的北京自然日。
 */
fun estimatePackageRunway(
    group: PackageGroup,
    week: List<DayTrafficPoint>?,
    now: Instant,
): PackageRunway {
    if (group.notYetEffective || group.usableRemainingBytes <= 0L) return PackageRunway.Hidden
    return when (val average = regionalDailyAverage(week, group.region)) {
        RegionalAverage.Missing -> PackageRunway.Hidden
        RegionalAverage.NoTraffic -> PackageRunway.NoTraffic
        is RegionalAverage.Available -> estimateWithAverage(group, average.bytesPerDay, now)
    }
}

private fun estimateWithAverage(
    group: PackageGroup,
    bytesPerDay: Double,
    now: Instant,
): PackageRunway {
    val rawDays = group.usableRemainingBytes / bytesPerDay
    val expiryDays = group.expiresAt
        .takeUnless { it == Instant.EPOCH }
        ?.let { (it.toEpochMilli() - now.toEpochMilli()) / MILLIS_PER_DAY }
    if (expiryDays != null && rawDays >= expiryDays) return PackageRunway.ExpiresFirst
    if (rawDays > MAX_RUNWAY_DAYS) return PackageRunway.OverLimit
    if (rawDays < MIN_VISIBLE_DAYS) return PackageRunway.UnderOneDay
    val days = rawDays.roundToInt()
    return PackageRunway.Days(
        days = days,
        exhaustionDate = now.atZone(BEIJING_ZONE).toLocalDate().plusDays(days.toLong()),
    )
}

/** 预计可用天数的展示状态；[Hidden] 表示这一组不显示任何天数文案。 */
sealed interface PackageRunway {
    /** 无区域数据、区域未知、未生效或已无余量——静默。 */
    data object Hidden : PackageRunway

    /** 本区域最近 7 天无流量，无法估算。 */
    data object NoTraffic : PackageRunway

    /** 按当前速度到到期日也用不完，不报具体天数。 */
    data object ExpiresFirst : PackageRunway

    /** 可用余量不足半天。 */
    data object UnderOneDay : PackageRunway

    /** 超过 [MAX_RUNWAY_DAYS] 天。 */
    data object OverLimit : PackageRunway

    /** 预计可用整天数；[exhaustionDate] 是北京自然日。 */
    data class Days(val days: Int, val exhaustionDate: LocalDate) : PackageRunway
}

private val RUNWAY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

/** 明细页的预计用完日（北京自然日，`MM-dd`）。 */
fun formatRunwayDate(date: LocalDate): String = RUNWAY_DATE_FORMAT.format(date)

/** 看板卡片的短标签；[PackageRunway.Hidden] 返回 null，调用方不占位。 */
fun runwayDayLabel(runway: PackageRunway): String? = when (runway) {
    PackageRunway.Hidden -> null
    PackageRunway.NoTraffic -> "近 7 天无流量，暂不估算"
    PackageRunway.ExpiresFirst -> "到期前用不完"
    PackageRunway.UnderOneDay -> "不足 1 天"
    PackageRunway.OverLimit -> ">999 天"
    is PackageRunway.Days -> "约可用 ${runway.days} 天"
}

/**
 * 明细页标签：有具体天数且 [allowDate] 时附上预计用完日。
 *
 * [allowDate] 为 false 表示 7 天数据来自离线快照——天数仍可展示（是估算），
 * 但不再用旧数据推一个看起来精确的日期。
 */
fun runwayDetailLabel(runway: PackageRunway, allowDate: Boolean): String? = when (runway) {
    is PackageRunway.Days ->
        if (allowDate) {
            "约可用 ${runway.days} 天 · 预计 ${formatRunwayDate(runway.exhaustionDate)} 用完"
        } else {
            "约可用 ${runway.days} 天"
        }

    else -> runwayDayLabel(runway)
}
