package com.cdndash.app.ui.dashboard

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 看板刷新编排里的资源包分支（票据 05）：与今日累计互不覆盖、成功写快照。
 *
 * 与 [DashboardViewModelTest] 分开成文件，避免票据 04/07 并行改同一测试时冲突过大；
 * HTTP 仍走 MockWebServer，不碰 Android 框架。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackageRefreshTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryAccountStore
    private lateinit var dispatcher: TestDispatcher

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
    fun `刷新时取资源包并写入 PACKAGES 快照`() = runTest(dispatcher) {
        server.dispatcher = actionDispatcher()
        val account = store.addAccount("账号", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val accountState = viewModel.uiState.value.accounts.first { it.account.id == account.id }
        val groups = requireNotNull(accountState.packageGroups) { "资源包应写入状态" }
        // 中国内地与海外同到期日也各自成组（区域不合并）。
        assertEquals(setOf("中国内地", "海外"), groups.map { it.region }.toSet())
        assertTrue(groups.none { it.notYetEffective })
        assertNull(accountState.packageFailure)
        assertNotNull(accountState.today)

        val snapshot = store.snapshot(account.id, SnapshotModule.PACKAGES)
        assertNotNull(snapshot)
        assertEquals(fixedNow.toEpochMilli(), snapshot!!.fetchedAtMillis)
        val packages = requireNotNull(PackageSnapshotCodec.decode(snapshot.payloadJson))
        assertEquals(2, packages.size)
        assertTrue(packages.all { it.commodityCode == "cdnflowbag" })
    }

    @Test
    fun `资源包失败只标记该账号的本模块且不影响其他账号`() = runTest(dispatcher) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl
                val action = url?.queryParameter("Action")
                val accessKeyId = url?.queryParameter("AccessKeyId")
                return when {
                    action == "DescribeCdnUserResourcePackage" && accessKeyId == "ak-bad" ->
                        MockResponse()
                            .setResponseCode(403)
                            .setBody("""{"Code":"NoPermission","Message":"stub"}""")

                    action == "DescribeCdnUserResourcePackage" -> okResponse(PACKAGE_RESPONSE)
                    else -> okResponse(TRAFFIC_RESPONSE)
                }
            }
        }
        val okAccount = store.addAccount("正常账号", "ak-ok", "secret-ok")
        val badPackageAccount = store.addAccount("包权限缺失", "ak-bad", "secret-bad")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val okState = state.accounts.first { it.account.id == okAccount.id }
        val badState = state.accounts.first { it.account.id == badPackageAccount.id }

        // 正常账号：今日与资源包都成功。
        assertEquals(1_048_576L, okState.today?.bytes)
        assertEquals(2, requireNotNull(okState.packageGroups).size)
        assertNull(okState.packageFailure)

        // 包权限缺失的账号：今日累计照常出数，只有资源包模块标记「权限不足」。
        assertEquals(1_048_576L, badState.today?.bytes)
        assertNull(badState.failure)
        assertNull(badState.packageGroups)
        assertEquals(ApiFailureKind.PERMISSION_DENIED, badState.packageFailure)
    }

    @Test
    fun `资源包失败回落到 PACKAGES 快照并标出抓取时间`() = runTest(dispatcher) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.queryParameter("Action")) {
                    "DescribeCdnUserResourcePackage" -> MockResponse().setResponseCode(500)
                    else -> okResponse(TRAFFIC_RESPONSE)
                }
        }
        val account = store.addAccount("账号", "ak-ok", "secret")
        val snapshotAt = fixedNow.minusSeconds(3_600L).toEpochMilli()
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.PACKAGES,
            payloadJson = PackageSnapshotCodec.encode(
                listOf(
                    ResourcePackage(
                        instanceId = "cn-1",
                        commodityCode = ResourcePackage.CDN_FLOW_PACKAGE_COMMODITY_CODE,
                        region = "中国内地",
                        totalBytes = 1_073_741_824L,
                        remainingBytes = 536_870_912L,
                        effectiveAt = Instant.parse("2026-11-01T00:00:00Z"),
                        expiresAt = Instant.parse("2026-12-31T16:00:00Z"),
                    ),
                ),
            ),
            fetchedAtMillis = snapshotAt,
        )
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val accountState = viewModel.uiState.value.accounts.first { it.account.id == account.id }
        // 资源包接口失败不影响今日累计；余量视图回落快照并按区域分组。
        assertEquals(1_048_576L, accountState.today?.bytes)
        assertEquals(listOf("中国内地"), requireNotNull(accountState.packageGroups).map { it.region })
        assertEquals(snapshotAt, accountState.packageSnapshotFetchedAtMillis)
        assertEquals(ApiFailureKind.NETWORK, accountState.packageFailure)
    }

    @Test
    fun `前台定时刷新不重取资源包`() = runTest(dispatcher) {
        var packageRequests = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("Action")
                if (action == "DescribeCdnUserResourcePackage") {
                    packageRequests++
                    return okResponse(PACKAGE_RESPONSE)
                }
                return okResponse(TRAFFIC_RESPONSE)
            }
        }
        store.addAccount("账号", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onForegroundStart()
        runCurrent()
        // 冷启动取一次资源包（CDN + DCDN 端点各 valid/exhaust 两条），另两条是今日累计与 7 天。
        assertEquals(2, packageRequests)
        assertEquals(6, server.requestCount)

        // 定时轮只补今日累计：资源包不随定时器重取。
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(2, packageRequests)
        assertEquals(7, server.requestCount)

        viewModel.onForegroundStop()
    }

    private fun newViewModel(): DashboardViewModel {
        val api = AliyunApiImpl(
            cdnRpc = AliyunRpcClient(
                baseUrl = server.url("/").toString(),
                sleeper = {},
                ioDispatcher = dispatcher,
            ),
        )
        return DashboardViewModel(
            accountStore = store,
            aliyunApi = api,
            now = { fixedNow },
        )
    }

    private fun actionDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("Action")
                return when (action) {
                    "DescribeCdnUserResourcePackage" -> okResponse(PACKAGE_RESPONSE)
                    else -> okResponse(TRAFFIC_RESPONSE)
                }
            }
        }

    private fun okResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        const val TRAFFIC_RESPONSE: String =
            """{"TrafficDataPerInterval":{"DataModule":[
               {"TimeStamp":"2026-11-26T02:05:00Z","Value":"1048576"}
            ]}}"""

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
