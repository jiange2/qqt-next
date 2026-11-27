package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 资源包余量视图中的一行（GLOSSARY「资源包余量视图」）：同区域、同到期日的包合并为一行。
 *
 * 分组键是（区域，到期日），所以不同区域永远不会合并、余量也永远不会相加
 * （GLOSSARY「区域」）。到期日按北京自然日切分：出差换时区不影响「哪个包先到期」。
 *
 * @property packageCount 组内包数；同到期日的多个包合并后显示。
 * @property totalBytes 组内总量之和。
 * @property remainingBytes 组内剩余量之和（包含未生效包的余量，仅用于展示原始值）。
 * @property effectiveAt 组内最早的生效时刻；所有包都晚于当前时刻才算「未生效」。
 * @property expiresAt 组内最早的到期时刻；估算可用天数时与它比较。`EPOCH` 表示到期时间未知。
 * @property notYetEffective 生效时刻晚于当前时刻的组；这类组不得当作可用余量。
 */
data class PackageGroup(
    val region: String,
    val expiresOn: LocalDate,
    val packageCount: Int,
    val totalBytes: Long,
    val remainingBytes: Long,
    val effectiveAt: Instant,
    val expiresAt: Instant,
    val notYetEffective: Boolean,
) {
    /**
     * 可用于抵扣的剩余量：未生效的组一律记 0，避免把「刚买还没开始抵扣」的包
     * 误读成当前可用余量（User Story 18）。
     */
    val usableRemainingBytes: Long
        get() = if (notYetEffective) 0L else remainingBytes
}

/** 一个区域下的全部到期日分组；区域之间不合并，仅用于界面分区展示。 */
data class RegionPackageGroups(
    val region: String,
    val groups: List<PackageGroup>,
)

/**
 * 把接口返回的资源包条目整理成余量视图（纯函数，spec「纯函数」）。
 *
 * - 只接受 [ResourcePackage.FLOW_PACKAGE_COMMODITY_CODES]（`cdnflowbag`/`dcdnpaybag`），
 *   不混入全站加速专用或 OSS 的包；端点已过滤，这里再过滤一次，纯函数自身也是安全的。
 * - 按（区域，北京自然日到期日）分组：组内剩余量/总量求和、计数包数。
 * - 排序：到期日升序；同日不同区域以区域名兜底，保证输出稳定。
 * - 组内剩余量合计为 0 的组不输出（整组用完）；单包用完但组内还有余量时保留，
 *   包数照实计数（用完与否按合并后的组判断，不剔除单个包）。
 * - 空列表返回空列表，不抛异常。
 *
 * @param now 判定「未生效」的参照时刻；由调用方注入，便于测试固定时刻。
 */
fun groupPackages(packages: List<ResourcePackage>, now: Instant): List<PackageGroup> =
    packages
        .filter { it.commodityCode in ResourcePackage.FLOW_PACKAGE_COMMODITY_CODES }
        // 接口区域码（CN/OVERSEAS）先归一，避免同一区域被拆成两组、估算也认不出区域。
        .map { it.copy(region = canonicalRegion(it.region)) }
        .groupBy { packageInfo -> packageInfo.region to packageInfo.expiresAt.beijingDate() }
        .map { (key, group) ->
            val earliestEffective = group.minOf { it.effectiveAt }
            PackageGroup(
                region = key.first,
                expiresOn = key.second,
                packageCount = group.size,
                totalBytes = group.sumOf { it.totalBytes },
                remainingBytes = group.sumOf { it.remainingBytes },
                effectiveAt = earliestEffective,
                // 同组同到期日，取最早时刻做保守比较；缺失到期时间（EPOCH）保持 EPOCH。
                expiresAt = group.minOf { it.expiresAt },
                // 「未生效的组」= 组内所有包都还没生效（最早生效时刻仍晚于当前）。
                notYetEffective = earliestEffective.isAfter(now),
            )
        }
        .sortedWith(compareBy({ it.expiresOn }, { it.region }))
        .filter { it.remainingBytes > 0L }

/**
 * 按区域分区展示（看板摘要与明细页共用）：区域内按到期日升序，区域之间按名称升序，
 * 仅决定渲染顺序，不做任何跨区域求和。
 */
fun packageGroupsByRegion(groups: List<PackageGroup>): List<RegionPackageGroups> =
    groups
        .groupBy { it.region }
        .toSortedMap()
        .map { (region, regionGroups) ->
            RegionPackageGroups(
                region = region,
                groups = regionGroups.sortedBy { it.expiresOn },
            )
        }

/**
 * 看板卡片摘要挑出要显示的组（纯函数）：**每个区域至少出现一组**，再按到期日轮转补足到
 * [limit] 组。不能只对全局列表取前 N 组，否则另一个区域会被整块隐藏（GLOSSARY「区域」）。
 *
 * - 区域数超过 [limit] 时以区域数为准（宁可多一行，也不隐藏某个区域）；
 * - 区域之间不合并、不相加，挑出来的行仍各自带着区域名；
 * - 每轮每个区域取一组（区域内按到期日升序），直到没有可取的组或已满额。
 *
 * @return 要展示的组；未展示的组数由调用方按 `总组数 - 返回值大小` 显示「还有 N 组」。
 */
fun selectPackageSummaryGroups(
    groups: List<PackageGroup>,
    limit: Int,
): List<PackageGroup> {
    val byRegion = packageGroupsByRegion(groups)
    if (byRegion.isEmpty()) return emptyList()
    val maxRows = maxOf(limit, byRegion.size)
    val selected = ArrayList<PackageGroup>(maxRows)
    var index = 0
    while (selected.size < maxRows) {
        var added = false
        for (section in byRegion) {
            if (selected.size >= maxRows) break
            val group = section.groups.getOrNull(index) ?: continue
            selected += group
            added = true
        }
        if (!added) break
        index++
    }
    return selected
}

private val PACKAGE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** 北京自然日 `yyyy-MM-dd`，明细页的「到期日」列。 */
fun formatPackageDate(date: LocalDate): String = PACKAGE_DATE_FORMAT.format(date)

/** 把时刻换算成北京自然日再格式化；生效/到期日都用这一个口径。 */
fun formatPackageDate(instant: Instant): String = formatPackageDate(instant.beijingDate())

private fun Instant.beijingDate(): LocalDate = atZone(BEIJING_ZONE).toLocalDate()
