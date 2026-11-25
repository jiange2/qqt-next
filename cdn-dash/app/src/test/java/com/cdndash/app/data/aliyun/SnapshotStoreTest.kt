package com.cdndash.app.data.aliyun

import com.cdndash.app.data.InMemoryAccountStore
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.TodayTraffic
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照收口 [SnapshotStore] 的外部行为测试（票据 07，spec「Testing Decisions」）。
 *
 * 用内存 AccountStore 替换持久化：只断言「带抓取时间」「成功后覆盖」「失败/损坏不覆盖旧快照」
 * 「按模块与账号隔离」。跑在 JVM 上，不碰 Android 框架。
 */
class SnapshotStoreTest {

    private val fetchedAt = Instant.parse("2026-11-26T02:30:00Z").toEpochMilli()

    @Test
    fun `今日快照保存后读回领域值与抓取时间`() = runTest {
        val accountStore = InMemoryAccountStore()
        val account = accountStore.addAccount("主站生产", "AK", "SK")
        val snapshots = SnapshotStore(accountStore)
        val today = TodayTraffic(
            bytes = 1_048_576L,
            dataThrough = Instant.parse("2026-11-26T02:25:00Z"),
        )

        assertTrue(snapshots.saveToday(account.id, today, fetchedAtMillis = fetchedAt))

        val cached = snapshots.loadToday(account.id)
        assertNotNull(cached)
        assertEquals(today, cached!!.value)
        assertEquals(fetchedAt, cached.fetchedAtMillis)
    }

    @Test
    fun `同一账号不同模块的快照互不覆盖`() = runTest {
        val accountStore = InMemoryAccountStore()
        val account = accountStore.addAccount("主站生产", "AK", "SK")
        val snapshots = SnapshotStore(accountStore)

        snapshots.saveToday(account.id, TodayTraffic(100L, Instant.parse("2026-11-26T02:00:00Z")), fetchedAt)
        snapshots.save(
            accountId = account.id,
            module = SnapshotModule.PACKAGES,
            payloadJson = """{"groups":[],"account":"A"}""",
            fetchedAtMillis = fetchedAt + 60_000L,
        )

        assertEquals(100L, snapshots.loadToday(account.id)?.value?.bytes)
        val packages = snapshots.load(account.id, SnapshotModule.PACKAGES) { it }
        assertEquals("""{"groups":[],"account":"A"}""", packages?.value)
        assertEquals(fetchedAt + 60_000L, packages?.fetchedAtMillis)
        // 未写过快照的模块仍是空，不会被别的模块顺手填上。
        assertNull(snapshots.load(account.id, SnapshotModule.WEEK_TREND) { it })
    }

    @Test
    fun `不同账号的同一模块快照互相隔离`() = runTest {
        val accountStore = InMemoryAccountStore()
        val first = accountStore.addAccount("A", "AK-A", "SK-A")
        val second = accountStore.addAccount("B", "AK-B", "SK-B")
        val snapshots = SnapshotStore(accountStore)

        snapshots.saveToday(first.id, TodayTraffic(1L, Instant.parse("2026-11-26T01:00:00Z")), fetchedAt)
        snapshots.saveToday(second.id, TodayTraffic(2L, Instant.parse("2026-11-26T01:10:00Z")), fetchedAt + 1L)

        assertEquals(1L, snapshots.loadToday(first.id)?.value?.bytes)
        assertEquals(fetchedAt, snapshots.loadToday(first.id)?.fetchedAtMillis)
        assertEquals(2L, snapshots.loadToday(second.id)?.value?.bytes)
        assertEquals(fetchedAt + 1L, snapshots.loadToday(second.id)?.fetchedAtMillis)
    }

    @Test
    fun `新成功覆盖旧快照的领域值与抓取时间`() = runTest {
        val accountStore = InMemoryAccountStore()
        val account = accountStore.addAccount("主站生产", "AK", "SK")
        val snapshots = SnapshotStore(accountStore)

        snapshots.saveToday(account.id, TodayTraffic(100L, Instant.parse("2026-11-26T02:00:00Z")), fetchedAt)
        snapshots.saveToday(
            account.id,
            TodayTraffic(200L, Instant.parse("2026-11-26T02:30:00Z")),
            fetchedAt + 3_600_000L,
        )

        val cached = snapshots.loadToday(account.id)
        assertEquals(200L, cached?.value?.bytes)
        assertEquals(fetchedAt + 3_600_000L, cached?.fetchedAtMillis)
    }

    @Test
    fun `解码失败返回空且不清除旧快照`() = runTest {
        val accountStore = InMemoryAccountStore()
        val account = accountStore.addAccount("主站生产", "AK", "SK")
        val snapshots = SnapshotStore(accountStore)
        snapshots.saveToday(account.id, TodayTraffic(100L, Instant.parse("2026-11-26T02:00:00Z")), fetchedAt)

        // 失败/损坏路径只读不写：解码返回 null，底层快照必须原样留着，等下一条提示重试。
        assertNull(snapshots.load<String>(account.id, SnapshotModule.TODAY_TOTAL) { null })

        val raw = accountStore.snapshot(account.id, SnapshotModule.TODAY_TOTAL)
        assertEquals(fetchedAt, raw?.fetchedAtMillis)
        assertEquals(100L, snapshots.loadToday(account.id)?.value?.bytes)
    }

    @Test
    fun `没有快照时读取返回空`() = runTest {
        val accountStore = InMemoryAccountStore()
        val account = accountStore.addAccount("主站生产", "AK", "SK")
        val snapshots = SnapshotStore(accountStore)

        assertNull(snapshots.loadToday(account.id))
        assertNull(accountStore.snapshot(account.id, SnapshotModule.TODAY_TOTAL))
    }
}
