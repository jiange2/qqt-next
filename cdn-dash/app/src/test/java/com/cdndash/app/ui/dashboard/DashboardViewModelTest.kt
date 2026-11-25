package com.cdndash.app.ui.dashboard

import com.cdndash.app.data.InMemoryAccountStore
import com.cdndash.app.data.aliyun.AliyunApiImpl
import com.cdndash.app.data.aliyun.AliyunRpcClient
import com.cdndash.app.data.aliyun.ApiFailureKind
import com.cdndash.app.data.aliyun.TodayTrafficSnapshotCodec
import com.cdndash.app.domain.SnapshotModule
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
        // runCurrent 只跑完当前时刻的初始刷新。每账号每轮两条请求（今日累计 + 已结算 7 天）。
        runCurrent()
        assertEquals(2, server.requestCount)

        // 默认刷新间隔 5 分钟：虚拟时间前进 5 分钟后应再刷新一轮。
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(4, server.requestCount)

        // 退到后台（ON_STOP）后定时器取消，再前进 30 分钟也不请求。
        viewModel.onForegroundStop()
        advanceTimeBy(30 * 60_000L)
        runCurrent()
        assertEquals(4, server.requestCount)
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
        assertEquals(2, server.requestCount)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(2, server.requestCount)

        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(4, server.requestCount)

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
