package com.cdndash.app.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资源包余量视图的纯函数测试（票据 05，spec「Testing Decisions」）。
 *
 * 全部用字面量断言：分组、排序、计数、未生效标注、区域不相加、非 cdnflowbag 排除、空列表。
 * 「未生效」的参照时刻固定为 [NOW]，不依赖运行时刻。
 */
class PackageGroupingTest {

    private val now: Instant = Instant.parse("2026-11-26T02:30:00Z")

    @Test
    fun `空列表不产生任何分组`() {
        assertEquals(emptyList<PackageGroup>(), groupPackages(emptyList(), now))
    }

    @Test
    fun `同区域同到期日的多个包合并为一行并计数`() {
        val groups = groupPackages(
            listOf(
                packageInfo(
                    instanceId = "pkg-a",
                    region = "中国内地",
                    totalBytes = 1000,
                    remainingBytes = 600,
                    expiresAt = "2026-12-31T08:00:00Z",
                ),
                packageInfo(
                    instanceId = "pkg-b",
                    region = "中国内地",
                    totalBytes = 2000,
                    remainingBytes = 400,
                    // 同一天、不同小时：北京 2026-12-31 18:00，仍应并入同一组。
                    expiresAt = "2026-12-31T10:00:00Z",
                ),
            ),
            now,
        )

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(2, group.packageCount)
        assertEquals(3000L, group.totalBytes)
        assertEquals(1000L, group.remainingBytes)
        assertEquals(LocalDate.of(2026, 12, 31), group.expiresOn)
    }

    @Test
    fun `分组按到期日升序`() {
        val groups = groupPackages(
            listOf(
                packageInfo(instanceId = "later", expiresAt = "2027-03-31T08:00:00Z"),
                packageInfo(instanceId = "sooner", expiresAt = "2026-12-31T08:00:00Z"),
            ),
            now,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 3, 31)),
            groups.map { it.expiresOn },
        )
    }

    @Test
    fun `生效时间晚于当前的分组标注未生效且不计入可用余量`() {
        val notYetEffective = groupPackages(
            listOf(
                packageInfo(
                    instanceId = "future",
                    remainingBytes = 500,
                    effectiveAt = "2026-12-01T00:00:00Z",
                    expiresAt = "2027-12-31T08:00:00Z",
                ),
            ),
            now,
        ).single()

        assertTrue(notYetEffective.notYetEffective)
        assertEquals(500L, notYetEffective.remainingBytes)
        // 「未生效」的余量不得当作当前可用余量（User Story 18）。
        assertEquals(0L, notYetEffective.usableRemainingBytes)
    }

    @Test
    fun `已生效的分组不标注未生效且计入可用余量`() {
        val effective = groupPackages(
            listOf(
                packageInfo(
                    instanceId = "active",
                    remainingBytes = 500,
                    effectiveAt = "2026-11-01T00:00:00Z",
                    expiresAt = "2027-12-31T08:00:00Z",
                ),
            ),
            now,
        ).single()

        assertFalse(effective.notYetEffective)
        assertEquals(500L, effective.usableRemainingBytes)
    }

    @Test
    fun `不同区域各自成组且余量不相加`() {
        val groups = groupPackages(
            listOf(
                packageInfo(
                    instanceId = "cn",
                    region = "中国内地",
                    totalBytes = 1000,
                    remainingBytes = 600,
                    expiresAt = "2026-12-31T08:00:00Z",
                ),
                packageInfo(
                    instanceId = "overseas",
                    region = "海外",
                    totalBytes = 500,
                    remainingBytes = 200,
                    expiresAt = "2026-12-31T08:00:00Z",
                ),
            ),
            now,
        )

        assertEquals(2, groups.size)
        val byRegion = groups.associateBy { it.region }
        assertEquals(600L, byRegion.getValue("中国内地").remainingBytes)
        assertEquals(1000L, byRegion.getValue("中国内地").totalBytes)
        assertEquals(200L, byRegion.getValue("海外").remainingBytes)
        assertEquals(500L, byRegion.getValue("海外").totalBytes)
    }

    @Test
    fun `非 cdnflowbag 条目被排除`() {
        val groups = groupPackages(
            listOf(
                packageInfo(
                    instanceId = "cdn",
                    commodityCode = "cdnflowbag",
                    totalBytes = 1000,
                    remainingBytes = 600,
                    expiresAt = "2026-12-31T08:00:00Z",
                ),
                packageInfo(
                    instanceId = "dcdn",
                    commodityCode = "dcdnflowbag",
                    totalBytes = 9999,
                    remainingBytes = 9999,
                    expiresAt = "2026-12-31T08:00:00Z",
                ),
                packageInfo(
                    instanceId = "oss",
                    commodityCode = "ossflowbag",
                    totalBytes = 8888,
                    remainingBytes = 8888,
                    expiresAt = "2026-12-31T08:00:00Z",
                ),
            ),
            now,
        )

        assertEquals(1, groups.size)
        assertEquals(1, groups.single().packageCount)
        assertEquals(1000L, groups.single().totalBytes)
        assertEquals(600L, groups.single().remainingBytes)
    }

    @Test
    fun `到期日按北京自然日切分`() {
        // 2026-12-31T16:00:00Z = 北京 2027-01-01 00:00，应归到 2027-01-01 而不是 12-31。
        val group = groupPackages(
            listOf(packageInfo(instanceId = "boundary", expiresAt = "2026-12-31T16:00:00Z")),
            now,
        ).single()

        assertEquals(LocalDate.of(2027, 1, 1), group.expiresOn)
    }

    @Test
    fun `按区域展示时区域内按到期日升序`() {
        val groups = groupPackages(
            listOf(
                packageInfo(instanceId = "cn-late", region = "中国内地", expiresAt = "2027-06-30T08:00:00Z"),
                packageInfo(instanceId = "cn-soon", region = "中国内地", expiresAt = "2026-12-31T08:00:00Z"),
                packageInfo(instanceId = "overseas", region = "海外", expiresAt = "2027-01-31T08:00:00Z"),
            ),
            now,
        )

        val regions = packageGroupsByRegion(groups)

        assertEquals(listOf("中国内地", "海外"), regions.map { it.region })
        assertEquals(
            listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 6, 30)),
            regions.first().groups.map { it.expiresOn },
        )
    }

    private fun packageInfo(
        instanceId: String,
        commodityCode: String = ResourcePackage.CDN_FLOW_PACKAGE_COMMODITY_CODE,
        region: String = "中国内地",
        totalBytes: Long = 1024L,
        remainingBytes: Long = 512L,
        effectiveAt: String = "2026-11-01T00:00:00Z",
        expiresAt: String = "2027-12-31T08:00:00Z",
    ): ResourcePackage = ResourcePackage(
        instanceId = instanceId,
        commodityCode = commodityCode,
        region = region,
        totalBytes = totalBytes,
        remainingBytes = remainingBytes,
        effectiveAt = Instant.parse(effectiveAt),
        expiresAt = Instant.parse(expiresAt),
    )
}
