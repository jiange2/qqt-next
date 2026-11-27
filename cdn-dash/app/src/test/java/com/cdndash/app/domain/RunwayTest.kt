package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资源包预计可用天数的纯函数测试（票据 10，ADR 0018）。
 *
 * 期望值全部是独立字面量：区域日均分母恒 7、区域不串用、旧快照静默、
 * 到期比较用未取整天数、北京自然日推算用完日。
 * 基准时刻取北京时间 2026-11-27 10:00。
 */
class RunwayTest {

    private val now: Instant = Instant.parse("2026-11-27T02:00:00Z")

    @Test
    fun `区域日均按七天合计除以七`() {
        val average = regionalDailyAverage(
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            region = ResourcePackage.MAINLAND_REGION,
        )

        assertEquals(1_024.0, (average as RegionalAverage.Available).bytesPerDay, 0.0001)
    }

    @Test
    fun `只有一天有数据时分母仍是七`() {
        val onlyOneDay = listOf(
            DayTrafficPoint(
                date = LocalDate.parse("2026-11-26"),
                bytes = 7_168,
                domesticBytes = 7_168,
            ),
        ) + (0 until 6).map { offset ->
            DayTrafficPoint(date = LocalDate.parse("2026-11-20").plusDays(offset.toLong()), bytes = 0)
        }

        val average = regionalDailyAverage(onlyOneDay, ResourcePackage.MAINLAND_REGION)

        assertEquals(7_168.0 / 7.0, (average as RegionalAverage.Available).bytesPerDay, 0.0001)
    }

    @Test
    fun `海外包只用海外日均且不串用内地流量`() {
        val runway = estimatePackageRunway(
            group = group(region = ResourcePackage.OVERSEAS_REGION, remainingBytes = 4_096),
            week = week(domesticBytes = 10_240, overseasBytes = 2_048),
            now = now,
        )

        // 海外日均 2048，剩 4096 → 2 天；若误用内地日均会得到不足 1 天。
        assertEquals(PackageRunway.Days(days = 2, exhaustionDate = LocalDate.parse("2026-11-29")), runway)
    }

    @Test
    fun `内地包只用内地日均`() {
        val runway = estimatePackageRunway(
            group = group(remainingBytes = 10_240),
            week = week(domesticBytes = 1_024, overseasBytes = 10_240),
            now = now,
        )

        assertEquals(PackageRunway.Days(days = 10, exhaustionDate = LocalDate.parse("2026-12-07")), runway)
    }

    @Test
    fun `未知区域不估算`() {
        val runway = estimatePackageRunway(
            group = group(region = ResourcePackage.UNKNOWN_REGION),
            week = week(domesticBytes = 1_024, overseasBytes = 1_024),
            now = now,
        )

        assertEquals(PackageRunway.Hidden, runway)
    }

    @Test
    fun `旧快照没有区域字段时静默而不是当成零流量`() {
        val legacyWeek = (0 until SETTLED_WEEK_DAY_COUNT).map { offset ->
            DayTrafficPoint(
                date = LocalDate.parse("2026-11-20").plusDays(offset.toLong()),
                bytes = 1_024,
            )
        }

        assertEquals(PackageRunway.Hidden, estimatePackageRunway(group(), legacyWeek, now))
    }

    @Test
    fun `七天全零显示无流量`() {
        val runway = estimatePackageRunway(group(), week(domesticBytes = 0, overseasBytes = 0), now)

        assertEquals(PackageRunway.NoTraffic, runway)
    }

    @Test
    fun `本区域零流量但另一区域有流量时显示无流量`() {
        val runway = estimatePackageRunway(
            group = group(),
            week = week(domesticBytes = 0, overseasBytes = 1_024),
            now = now,
        )

        assertEquals(PackageRunway.NoTraffic, runway)
    }

    @Test
    fun `不足半天显示不足一天`() {
        val runway = estimatePackageRunway(
            group = group(remainingBytes = 100),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.UnderOneDay, runway)
    }

    @Test
    fun `超过九百九十九天封顶`() {
        val runway = estimatePackageRunway(
            group = group(remainingBytes = 1_024_000, expiresAt = Instant.EPOCH),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.OverLimit, runway)
    }

    @Test
    fun `到期早于用完显示到期前用不完`() {
        val runway = estimatePackageRunway(
            group = group(
                remainingBytes = 10_240,
                expiresAt = now.plusSeconds(5 * 86_400L),
            ),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.ExpiresFirst, runway)
    }

    @Test
    fun `到期天数正好等于用完天数也是到期前用不完`() {
        val runway = estimatePackageRunway(
            group = group(
                remainingBytes = 5_120,
                expiresAt = now.plusSeconds(5 * 86_400L),
            ),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.ExpiresFirst, runway)
    }

    @Test
    fun `到期比较用未取整的原始天数`() {
        // 5.7 天：四舍五入是 6 天，但包还有 6 天到期，不能判成「到期前用不完」。
        val runway = estimatePackageRunway(
            group = group(
                remainingBytes = 5_837,
                expiresAt = now.plusSeconds(6 * 86_400L),
            ),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertTrue(runway is PackageRunway.Days)
        assertEquals(6, (runway as PackageRunway.Days).days)
    }

    @Test
    fun `到期时间未知时仍给天数`() {
        val runway = estimatePackageRunway(
            group = group(
                remainingBytes = 10_240,
                expiresAt = Instant.EPOCH,
                expiresOn = LocalDate.parse("2026-11-27"),
            ),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.Days(days = 10, exhaustionDate = LocalDate.parse("2026-12-07")), runway)
    }

    @Test
    fun `未生效的组不估算`() {
        val runway = estimatePackageRunway(
            group = group(notYetEffective = true),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.Hidden, runway)
    }

    @Test
    fun `已无余量的组不估算`() {
        val runway = estimatePackageRunway(
            group = group(remainingBytes = 0),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = now,
        )

        assertEquals(PackageRunway.Hidden, runway)
    }

    @Test
    fun `预计用完日按北京自然日推算`() {
        // 北京时间 11-28 00:30：今天已是 11-28，两天后是 11-30（不是 UTC 的 11-29）。
        val afterMidnight = Instant.parse("2026-11-27T16:30:00Z")

        val runway = estimatePackageRunway(
            group = group(remainingBytes = 2_048),
            week = week(domesticBytes = 1_024, overseasBytes = 0),
            now = afterMidnight,
        )

        assertEquals(PackageRunway.Days(days = 2, exhaustionDate = LocalDate.parse("2026-11-30")), runway)
    }

    @Test
    fun `看板短标签与明细标签的文案`() {
        val days = PackageRunway.Days(days = 18, exhaustionDate = LocalDate.parse("2026-12-03"))

        assertEquals("约可用 18 天", runwayDayLabel(days))
        assertEquals("不足 1 天", runwayDayLabel(PackageRunway.UnderOneDay))
        assertEquals(">999 天", runwayDayLabel(PackageRunway.OverLimit))
        assertEquals("到期前用不完", runwayDayLabel(PackageRunway.ExpiresFirst))
        assertEquals("近 7 天无流量，暂不估算", runwayDayLabel(PackageRunway.NoTraffic))
        assertNull(runwayDayLabel(PackageRunway.Hidden))

        assertEquals("约可用 18 天 · 预计 12-03 用完", runwayDetailLabel(days, allowDate = true))
        assertEquals("约可用 18 天", runwayDetailLabel(days, allowDate = false))
    }

    private fun week(domesticBytes: Long, overseasBytes: Long): List<DayTrafficPoint> =
        (0 until SETTLED_WEEK_DAY_COUNT).map { offset ->
            DayTrafficPoint(
                date = LocalDate.parse("2026-11-20").plusDays(offset.toLong()),
                bytes = domesticBytes + overseasBytes,
                domesticBytes = domesticBytes,
                overseasBytes = overseasBytes,
            )
        }

    private fun group(
        region: String = ResourcePackage.MAINLAND_REGION,
        remainingBytes: Long = 10_240L,
        expiresAt: Instant = Instant.parse("2027-01-01T00:00:00Z"),
        expiresOn: LocalDate = expiresAt.atZone(BEIJING_ZONE).toLocalDate(),
        notYetEffective: Boolean = false,
    ): PackageGroup = PackageGroup(
        region = region,
        expiresOn = expiresOn,
        packageCount = 1,
        totalBytes = 1_000_000L,
        remainingBytes = remainingBytes,
        effectiveAt = Instant.parse("2026-11-01T00:00:00Z"),
        expiresAt = expiresAt,
        notYetEffective = notYetEffective,
    )
}
