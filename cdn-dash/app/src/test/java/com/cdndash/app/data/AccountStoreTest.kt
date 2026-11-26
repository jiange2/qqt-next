package com.cdndash.app.data

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.visibleAccounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AccountStore 的外部行为测试（票据 02，spec「Testing Decisions」）。
 *
 * 用内存实现替换生产实现：只断言「保存后读回一致」「删除后账号/凭据/快照不存在」
 * 「快照带抓取时间」「重排后顺序保持」等语义。Keystore 的真实加解密是设备级行为，
 * 不放进 JVM 测试，列入真机人工验收。
 */
class AccountStoreTest {

    @Test
    fun `新增账号后元数据与明文凭据一致`() = runTest {
        val store = InMemoryAccountStore()

        val created = store.addAccount(
            name = "主站生产",
            accessKeyId = "AK-MAIN",
            accessKeySecret = "SK-MAIN",
        )

        val listed = store.accounts.first()
        assertEquals(1, listed.size)
        assertEquals(created.id, listed.single().id)
        assertEquals("主站生产", listed.single().name)
        assertTrue(listed.single().enabled)
        assertEquals(0, listed.single().sortOrder)
        assertEquals(
            AccountCredentials(accessKeyId = "AK-MAIN", accessKeySecret = "SK-MAIN"),
            store.credentials(created.id),
        )
    }

    @Test
    fun `多个账号新增后按手动序号追加在末尾`() = runTest {
        val store = InMemoryAccountStore()

        store.addAccount("主站生产", "AK-1", "SK-1")
        store.addAccount("海外测试", "AK-2", "SK-2")
        store.addAccount("备用账号", "AK-3", "SK-3")

        val listed = store.accounts.first()
        assertEquals(listOf("主站生产", "海外测试", "备用账号"), listed.map { it.name })
        assertEquals(listOf(0, 1, 2), listed.map { it.sortOrder })
    }

    @Test
    fun `删除账号后账号凭据与全部快照都不存在`() = runTest {
        val store = InMemoryAccountStore()
        val account = store.addAccount("待删除", "AK", "SK")
        SnapshotModule.entries.forEach { module ->
            store.saveSnapshot(
                accountId = account.id,
                module = module,
                payloadJson = "{\"module\":\"${module.name}\"}",
                fetchedAtMillis = 1_700_000_000_000L,
            )
        }

        store.deleteAccount(account.id)

        assertTrue(store.accounts.first().isEmpty())
        assertNull(store.credentials(account.id))
        SnapshotModule.entries.forEach { module ->
            assertNull(store.snapshot(account.id, module))
        }
    }

    @Test
    fun `快照保存领域 JSON 与抓取时间且可覆盖`() = runTest {
        val store = InMemoryAccountStore()
        val account = store.addAccount("主站生产", "AK", "SK")

        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.PACKAGES,
            payloadJson = "{\"groups\":[]}",
            fetchedAtMillis = 1_700_000_123_456L,
        )

        val saved = store.snapshot(account.id, SnapshotModule.PACKAGES)
        assertEquals("{\"groups\":[]}", saved?.payloadJson)
        assertEquals(1_700_000_123_456L, saved?.fetchedAtMillis)
        assertNull(store.snapshot(account.id, SnapshotModule.TODAY_TOTAL))

        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.PACKAGES,
            payloadJson = "{\"groups\":[1]}",
            fetchedAtMillis = 1_700_000_999_000L,
        )
        val updated = store.snapshot(account.id, SnapshotModule.PACKAGES)
        assertEquals("{\"groups\":[1]}", updated?.payloadJson)
        assertEquals(1_700_000_999_000L, updated?.fetchedAtMillis)
    }

    @Test
    fun `重排后顺序保持且新账号排在末尾`() = runTest {
        val store = InMemoryAccountStore()
        val first = store.addAccount("A", "AK-A", "SK-A")
        val second = store.addAccount("B", "AK-B", "SK-B")
        val third = store.addAccount("C", "AK-C", "SK-C")

        store.reorderAccounts(listOf(third.id, first.id, second.id))

        val reordered = store.accounts.first()
        assertEquals(listOf(third.id, first.id, second.id), reordered.map { it.id })
        assertEquals(listOf(0, 1, 2), reordered.map { it.sortOrder })

        val appended = store.addAccount("D", "AK-D", "SK-D")
        val afterAppend = store.accounts.first()
        assertEquals(appended.id, afterAppend.last().id)
        assertEquals(3, afterAppend.last().sortOrder)
    }

    @Test
    fun `停用后元数据保留但看板过滤掉`() = runTest {
        val store = InMemoryAccountStore()
        val visible = store.addAccount("主站生产", "AK-1", "SK-1")
        val hidden = store.addAccount("海外测试", "AK-2", "SK-2")

        store.setEnabled(hidden.id, false)

        val all = store.accounts.first()
        assertEquals(2, all.size)
        assertEquals(
            listOf(visible.id),
            visibleAccounts(all).map { it.id },
        )
        assertEquals(false, all.single { it.id == hidden.id }.enabled)
        // 停用不删配置：凭据仍可读，重新启用即可恢复。
        assertEquals("SK-2", store.credentials(hidden.id)?.accessKeySecret)
    }

    @Test
    fun `刷新间隔默认五分钟并可持久化为其他选项`() = runTest {
        val store = InMemoryAccountStore()

        assertEquals(5, store.refreshIntervalMinutes.first())

        store.setRefreshIntervalMinutes(15)
        assertEquals(15, store.refreshIntervalMinutes.first())
    }

    @Test
    fun `生物识别开关默认关闭`() = runTest {
        val store = InMemoryAccountStore()

        assertEquals(false, store.biometricEnabled.first())
    }

    @Test
    fun `生物识别开关保存后读回一致并可关回`() = runTest {
        val store = InMemoryAccountStore()

        store.setBiometricEnabled(true)
        assertEquals(true, store.biometricEnabled.first())

        store.setBiometricEnabled(false)
        assertEquals(false, store.biometricEnabled.first())
    }
}
