package com.cdndash.app.ui.dashboard

import com.cdndash.app.data.InMemoryAccountStore
import com.cdndash.app.data.aliyun.AliyunApiImpl
import com.cdndash.app.data.aliyun.AliyunRpcClient
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.data.aliyun.TodayTrafficSnapshotCodec
import com.cdndash.app.domain.SnapshotModule
import com.cdndash.app.domain.TodayTraffic
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.Before
import org.junit.Test

/**
 * 看板刷新编排的 JVM 测试（票据 03）：账号间错误隔离、成功写快照、
 * 「打开 / 前台定时 / 退后台停止」三种触发。HTTP 仍走 MockWebServer，测试不碰 Android 框架。
 *
 * 票据 07 增补：失败回落到快照并带抓取时间、刷新失败保留已展示数据、无快照时仍是错误态、
 * 成功后覆盖快照并清除离线标注。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

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
    fun `一个账号凭证失效不影响另一账号出数`() = runTest(dispatcher) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.queryParameter("AccessKeyId")) {
                    "ak-bad" -> MockResponse()
                        .setResponseCode(400)
                        .setBody("""{"Code":"InvalidAccessKeyId.NotFound","Message":"stub"}""")

                    else -> MockResponse()
                        .setResponseCode(200)
                        .setBody(TRAFFIC_RESPONSE)
                }
        }
        val okAccount = store.addAccount("正常账号", "ak-ok", "secret-ok")
        val badAccount = store.addAccount("坏密钥", "ak-bad", "secret-bad")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val okState = state.accounts.first { it.account.id == okAccount.id }
        val badState = state.accounts.first { it.account.id == badAccount.id }
        assertEquals(1_048_576L, okState.today?.bytes)
        assertNull(okState.failure)
        assertEquals(ApiFailureKind.CREDENTIAL_INVALID, badState.failure)
        assertNull(badState.today)
    }

    @Test
    fun `成功取数写入带抓取时间的今日快照`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(200).setBody(TRAFFIC_RESPONSE) }
        val account = store.addAccount("账号", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val snapshot = store.snapshot(account.id, SnapshotModule.TODAY_TOTAL)
        assertNotNull(snapshot)
        assertEquals(fixedNow.toEpochMilli(), snapshot!!.fetchedAtMillis)
        assertEquals(1_048_576L, TodayTrafficSnapshotCodec.decode(snapshot.payloadJson)?.bytes)
    }

    @Test
    fun `打开后按设置间隔前台刷新且退后台停止`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(200).setBody(TRAFFIC_RESPONSE) }
        store.addAccount("账号", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onForegroundStart()
        // 定时器是无限循环，不能用 advanceUntilIdle（会一直推进虚拟时间）；
        // runCurrent 只跑完当前时刻的初始刷新。
        runCurrent()
        assertEquals(1, server.requestCount)

        // 默认刷新间隔 5 分钟：虚拟时间前进 5 分钟后应再请求一次。
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(2, server.requestCount)

        // 退到后台（ON_STOP）后定时器取消，再前进 30 分钟也不请求。
        viewModel.onForegroundStop()
        advanceTimeBy(30 * 60_000L)
        runCurrent()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `刷新间隔读取设置页的值`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(200).setBody(TRAFFIC_RESPONSE) }
        store.addAccount("账号", "ak-ok", "secret")
        store.setRefreshIntervalMinutes(15)
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onForegroundStart()
        runCurrent()
        assertEquals(1, server.requestCount)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(1, server.requestCount)

        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(2, server.requestCount)

        viewModel.onForegroundStop()
    }

    @Test
    fun `请求失败时回落到上次快照并给出抓取时间`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(500) }
        val account = store.addAccount("账号", "ak-ok", "secret")
        val snapshotAt = fixedNow.minusSeconds(3_600L).toEpochMilli()
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.TODAY_TOTAL,
            payloadJson = TodayTrafficSnapshotCodec.encode(
                TodayTraffic(bytes = 777L, dataThrough = fixedNow.minusSeconds(7_200L)),
            ),
            fetchedAtMillis = snapshotAt,
        )
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value.accounts.single()
        assertEquals(777L, state.today?.bytes)
        assertEquals(snapshotAt, state.snapshotFetchedAtMillis)
        assertEquals(ApiFailureKind.NETWORK, state.failure)
        // 失败绝不写、不清快照：旧快照的抓取时间与内容原样留着。
        val snapshot = store.snapshot(account.id, SnapshotModule.TODAY_TOTAL)
        assertEquals(snapshotAt, snapshot?.fetchedAtMillis)
        assertEquals(777L, TodayTrafficSnapshotCodec.decode(snapshot!!.payloadJson)?.bytes)
    }

    @Test
    fun `刷新失败保留已展示的数据与旧快照`() = runTest(dispatcher) {
        var failing = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (failing) {
                    MockResponse().setResponseCode(500)
                } else {
                    MockResponse().setResponseCode(200).setBody(TRAFFIC_RESPONSE)
                }
        }
        val account = store.addAccount("账号", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(1_048_576L, viewModel.uiState.value.accounts.single().today?.bytes)

        failing = true
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value.accounts.single()
        assertEquals(1_048_576L, state.today?.bytes)
        assertEquals(fixedNow.toEpochMilli(), state.snapshotFetchedAtMillis)
        assertEquals(ApiFailureKind.NETWORK, state.failure)
        assertEquals(false, state.isRefreshing)
        assertEquals(
            fixedNow.toEpochMilli(),
            store.snapshot(account.id, SnapshotModule.TODAY_TOTAL)?.fetchedAtMillis,
        )
    }

    @Test
    fun `没有快照时失败不展示数据也不空白成功`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(500) }
        val account = store.addAccount("账号", "ak-ok", "secret")
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value.accounts.single()
        assertNull(state.today)
        assertNull(state.snapshotFetchedAtMillis)
        assertEquals(ApiFailureKind.NETWORK, state.failure)
        assertNull(store.snapshot(account.id, SnapshotModule.TODAY_TOTAL))
    }

    @Test
    fun `成功取数后用新数据覆盖快照并清除离线标注`() = runTest(dispatcher) {
        server.dispatcher = staticDispatcher { MockResponse().setResponseCode(200).setBody(TRAFFIC_RESPONSE) }
        val account = store.addAccount("账号", "ak-ok", "secret")
        store.saveSnapshot(
            accountId = account.id,
            module = SnapshotModule.TODAY_TOTAL,
            payloadJson = TodayTrafficSnapshotCodec.encode(
                TodayTraffic(bytes = 777L, dataThrough = fixedNow.minusSeconds(7_200L)),
            ),
            fetchedAtMillis = fixedNow.minusSeconds(3_600L).toEpochMilli(),
        )
        val viewModel = newViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value.accounts.single()
        assertEquals(1_048_576L, state.today?.bytes)
        assertNull(state.snapshotFetchedAtMillis)
        assertNull(state.failure)
        val snapshot = store.snapshot(account.id, SnapshotModule.TODAY_TOTAL)
        assertEquals(fixedNow.toEpochMilli(), snapshot?.fetchedAtMillis)
        assertEquals(1_048_576L, TodayTrafficSnapshotCodec.decode(snapshot!!.payloadJson)?.bytes)
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

    private fun staticDispatcher(responseFactory: () -> MockResponse): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = responseFactory()
        }

    private companion object {
        const val TRAFFIC_RESPONSE: String =
            """{"TrafficDataPerInterval":{"DataModule":[
               {"TimeStamp":"2026-11-26T02:05:00Z","Value":"1048576"}
            ]}}"""
    }
}
