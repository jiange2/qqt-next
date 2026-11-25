package com.cdndash.app.ui.spend

import com.cdndash.app.data.InMemoryAccountStore
import com.cdndash.app.data.aliyun.AliyunApiImpl
import com.cdndash.app.data.aliyun.AliyunRpcClient
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.data.aliyun.MonthlyBillSnapshotCodec
import com.cdndash.app.domain.MonthlyBill
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.recentBillingMonths
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 花费页编排的 JVM 测试（票据 08）：按需加载、24 小时快照命中、跨月/超时重取、
 * 账号切换、无权限与刷新失败保留旧数据。HTTP 仍走 MockWebServer，不碰 Android 框架。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpendViewModelTest {

    private lateinit var billServer: MockWebServer
    private lateinit var store: InMemoryAccountStore
    private lateinit var dispatcher: TestDispatcher

    private val fixedNow: Instant = Instant.parse("2026-11-26T02:30:00Z")

    @Before
    fun setUp() {
        billServer = MockWebServer()
        billServer.start()
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        store = InMemoryAccountStore()
    }

    @After
    fun tearDown() {
        billServer.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `命中 24 小时快照时不发账单请求`() = runTest(dispatcher) {
        val account = store.addAccount("主站生产", "ak-ok", "secret")
        val cached = cachedBills(amount = "12.34")
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.MONTHLY_BILL,
            payloadJson = MonthlyBillSnapshotCodec.encode(cached),
            fetchedAtMillis = fixedNow.toEpochMilli() - 60 * 60 * 1_000L,
        )
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(0, billServer.requestCount)
        val state = viewModel.uiState.value
        assertEquals(12, state.bills.size)
        assertEquals(BigDecimal("12.34"), state.bills.last().pretaxAmount)
        assertNull(state.failure)
        assertEquals(fixedNow.toEpochMilli() - 60 * 60 * 1_000L, state.fetchedAtMillis)
    }

    @Test
    fun `超过 24 小时的快照会重取 12 个月并更新快照时间`() = runTest(dispatcher) {
        billServer.dispatcher = staticDispatcher { jsonResponse(monthResponse("1.00")) }
        val account = store.addAccount("主站生产", "ak-ok", "secret")
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.MONTHLY_BILL,
            payloadJson = MonthlyBillSnapshotCodec.encode(cachedBills(amount = "12.34")),
            fetchedAtMillis = fixedNow.toEpochMilli() - 25 * 60 * 60 * 1_000L,
        )
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(12, billServer.requestCount)
        val state = viewModel.uiState.value
        assertEquals(12, state.bills.size)
        assertTrue(state.bills.all { it.pretaxAmount.compareTo(BigDecimal("1.00")) == 0 })
        assertEquals(fixedNow.toEpochMilli(), state.fetchedAtMillis)
        val snapshot = requireNotNull(store.snapshot(account.id, SnapshotModule.MONTHLY_BILL))
        assertEquals(fixedNow.toEpochMilli(), snapshot.fetchedAtMillis)
    }

    @Test
    fun `无费用权限时显示权限错误且失败即停`() = runTest(dispatcher) {
        billServer.dispatcher = staticDispatcher {
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"Code":"UnauthorizedOperation","Message":"stub"}""")
        }
        store.addAccount("无费用权限", "ak-no-bss", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ApiFailureKind.PERMISSION_DENIED, state.failure)
        assertTrue(state.bills.isEmpty())
        assertFalse(state.isRefreshing)
        // 第一个账期就失败，不把 12 次请求都打一遍。
        assertEquals(1, billServer.requestCount)
    }

    @Test
    fun `切换账号按需加载且已加载账号不重复请求`() = runTest(dispatcher) {
        billServer.dispatcher = staticDispatcher { jsonResponse(monthResponse("1.00")) }
        val first = store.addAccount("账号一", "ak-1", "secret-1")
        val second = store.addAccount("账号二", "ak-2", "secret-2")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(12, billServer.requestCount)
        assertEquals(first.id, viewModel.uiState.value.selectedAccountId)

        viewModel.selectAccount(second.id)
        advanceUntilIdle()
        assertEquals(24, billServer.requestCount)
        assertEquals(second.id, viewModel.uiState.value.selectedAccountId)

        // 切回已加载账号：状态仍在，不再请求。
        viewModel.selectAccount(first.id)
        advanceUntilIdle()
        assertEquals(24, billServer.requestCount)
    }

    @Test
    fun `刷新失败保留上次成功账单与数据时间`() = runTest(dispatcher) {
        var shouldFail = false
        billServer.dispatcher = staticDispatcher {
            if (shouldFail) {
                MockResponse().setResponseCode(500)
            } else {
                jsonResponse(monthResponse("1.00"))
            }
        }
        val account = store.addAccount("主站生产", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.bills.size)

        shouldFail = true
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ApiFailureKind.NETWORK, state.failure)
        assertEquals(12, state.bills.size)
        assertEquals(fixedNow.toEpochMilli(), state.fetchedAtMillis)
        assertEquals(account.id, state.selectedAccountId)
    }

    private fun newViewModel(): SpendViewModel {
        val rpc = AliyunRpcClient(
            baseUrl = billServer.url("/").toString(),
            sleeper = {},
            ioDispatcher = dispatcher,
        )
        return SpendViewModel(
            accountStore = store,
            aliyunApi = AliyunApiImpl(cdnRpc = rpc, bssRpc = rpc),
            now = { fixedNow },
        )
    }

    private fun cachedBills(amount: String): List<MonthlyBill> =
        recentBillingMonths(fixedNow).map { month ->
            MonthlyBill(
                month = month,
                pretaxAmount = BigDecimal(amount),
                cashAmount = BigDecimal.ZERO,
                voucherAmount = BigDecimal.ZERO,
            )
        }

    private fun staticDispatcher(responseFactory: () -> MockResponse): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = responseFactory()
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun monthResponse(amount: String): String =
        """{"Code":"Success","Success":true,"Data":{"TotalCount":1,"Items":{"Item":[
           {"PretaxAmount":"$amount","CashAmount":"$amount",
            "DeductedByCashCoupons":"0","DeductedByCoupons":"0",
            "SubscriptionType":"PayAsYouGo","ProductCode":"cdn","Currency":"CNY"}
        ]}}}"""
}
