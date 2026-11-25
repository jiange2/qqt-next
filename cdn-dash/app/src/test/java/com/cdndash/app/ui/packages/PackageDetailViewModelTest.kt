package com.cdndash.app.ui.packages

import com.cdndash.app.data.InMemoryAccountStore
import com.cdndash.app.data.aliyun.AliyunApiImpl
import com.cdndash.app.data.aliyun.AliyunRpcClient
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.data.aliyun.PackageSnapshotCodec
import com.cdndash.app.domain.ResourcePackage
import com.cdndash.app.domain.SnapshotModule
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 资源包明细页的 JVM 测试（票据 05、票据 07）：进页即取、成功写快照、
 * 失败回落到 `PACKAGES` 快照并带抓取时间、无快照时是纯错误态。
 *
 * HTTP 走 MockWebServer，持久化走内存 AccountStore，不碰 Android 框架。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackageDetailViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryAccountStore
    private lateinit var dispatcher: StandardTestDispatcher

    private val fixedNow: Instant = Instant.parse("2026-11-26T02:30:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        store = InMemoryAccountStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `取数失败时回落到 PACKAGES 快照并带抓取时间`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(500) }
        val account = store.addAccount("账号", "ak-ok", "secret")
        val snapshotAt = fixedNow.minusSeconds(3_600L).toEpochMilli()
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.PACKAGES,
            payloadJson = PackageSnapshotCodec.encode(listOf(CN_PACKAGE_BEAN)),
            fetchedAtMillis = snapshotAt,
        )

        val viewModel = newViewModel(account.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("中国内地"), requireNotNull(state.groups).map { it.region })
        assertEquals(snapshotAt, state.snapshotFetchedAtMillis)
        assertEquals(ApiFailureKind.NETWORK, state.failure)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun `没有快照时失败只给错误态`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(500) }
        val account = store.addAccount("账号", "ak-ok", "secret")

        val viewModel = newViewModel(account.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.groups)
        assertNull(state.snapshotFetchedAtMillis)
        assertEquals(ApiFailureKind.NETWORK, state.failure)
    }

    @Test
    fun `成功取数覆盖快照并清除离线标注`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { okResponse(PACKAGE_RESPONSE) }
        val account = store.addAccount("账号", "ak-ok", "secret")
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.PACKAGES,
            payloadJson = PackageSnapshotCodec.encode(listOf(CN_PACKAGE_BEAN)),
            fetchedAtMillis = fixedNow.minusSeconds(3_600L).toEpochMilli(),
        )

        val viewModel = newViewModel(account.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf("中国内地", "海外"), requireNotNull(state.groups).map { it.region }.toSet())
        assertNull(state.snapshotFetchedAtMillis)
        assertNull(state.failure)

        val snapshot = requireNotNull(store.snapshot(account.id, SnapshotModule.PACKAGES))
        assertEquals(fixedNow.toEpochMilli(), snapshot.fetchedAtMillis)
        assertEquals(2, PackageSnapshotCodec.decode(snapshot.payloadJson)?.size)
    }

    private fun newViewModel(accountId: String): PackageDetailViewModel {
        val api = AliyunApiImpl(
            cdnRpc = AliyunRpcClient(
                baseUrl = server.url("/").toString(),
                sleeper = {},
                ioDispatcher = dispatcher,
            ),
        )
        return PackageDetailViewModel(
            accountStore = store,
            aliyunApi = api,
            accountId = accountId,
            now = { fixedNow },
        )
    }

    private fun staticDispatcher(responseFactory: () -> MockResponse): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = responseFactory()
        }

    private fun okResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        val CN_PACKAGE_BEAN = ResourcePackage(
            instanceId = "cn-1",
            commodityCode = ResourcePackage.CDN_FLOW_PACKAGE_COMMODITY_CODE,
            region = "中国内地",
            totalBytes = 1_073_741_824L,
            remainingBytes = 536_870_912L,
            effectiveAt = Instant.parse("2026-11-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-12-31T16:00:00Z"),
        )

        val PACKAGE_RESPONSE: String =
            """
            {"ResourcePackageInfos":{"ResourcePackageInfo":[
              {"CommodityCode":"cdnflowbag","InstanceId":"cn-1","Region":"中国内地",
               "InitCapacity":"1073741824","CurrCapacity":"536870912",
               "StartTime":"2026-11-01T00:00:00Z","EndTime":"2026-12-31T16:00:00Z"},
              {"CommodityCode":"cdnflowbag","InstanceId":"ov-1","Region":"海外",
               "InitCapacity":"2147483648","CurrCapacity":"1073741824",
               "StartTime":"2026-11-01T00:00:00Z","EndTime":"2026-12-31T16:00:00Z"}
            ]}}
            """.trimIndent()
    }
}
