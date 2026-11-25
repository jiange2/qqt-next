package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.weekTotal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 已结算 7 天端点的 MockWebServer 测试（票据 04，spec「Testing Decisions」）。
 *
 * 只验证外部行为：业务参数（`Interval=3600`、北京「今天-7」到「今天」的时间窗、不传 DomainName）、
 * 小时点解析为 7 个北京日日点、今天的点被丢弃。签名与错误归一已由票据 03 在 RPC 层覆盖。
 */
class TrafficWeekEndpointTest {

    private lateinit var server: MockWebServer
    private lateinit var endpoint: TrafficWeekEndpoint

    private val credentials = AccountCredentials(
        accessKeyId = "test-ak",
        accessKeySecret = "test-secret",
    )

    /** 北京时间 2026-11-27 10:00：今天 = 11-27，窗口 11-20 至 11-26。 */
    private val now: Instant = Instant.parse("2026-11-27T02:00:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        endpoint = TrafficWeekEndpoint(
            AliyunRpcClient(
                baseUrl = server.url("/").toString(),
                sleeper = {},
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `请求携带 3600 秒粒度与今天减七的北京时间窗口`() = runTest {
        server.enqueue(jsonResponse(EMPTY_RESPONSE))

        endpoint.settledDays(credentials, now)

        val url = requireNotNull(server.takeRequest().requestUrl)
        assertEquals("DescribeDomainTrafficData", url.queryParameter("Action"))
        assertEquals("2018-05-10", url.queryParameter("Version"))
        // 账号全量：不传 DomainName（ADR 0016）。
        assertNull(url.queryParameter("DomainName"))
        assertEquals("3600", url.queryParameter("Interval"))
        assertEquals("2026-11-19T16:00:00Z", url.queryParameter("StartTime"))
        assertEquals("2026-11-26T16:00:00Z", url.queryParameter("EndTime"))
    }

    @Test
    fun `小时点解析为七个北京日日点并丢弃今天`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"TrafficDataPerInterval":{"DataModule":[
                  {"TimeStamp":"2026-11-25T15:00:00Z","Value":"1024"},
                  {"TimeStamp":"2026-11-25T16:00:00Z","Value":2048},
                  {"TimeStamp":"2026-11-26T16:30:00Z","Value":"4096"}
                ]}}
                """.trimIndent(),
            ),
        )

        val days = requireNotNull(endpoint.settledDays(credentials, now).successOrNull()) {
            "接口应返回成功结果"
        }

        assertEquals(7, days.size)
        // 北京 11-25 23:00 → 11-25；北京 11-26 00:00 → 11-26；北京 11-27 00:30 属今天，丢弃。
        assertEquals(LocalDate.parse("2026-11-25"), days[5].date)
        assertEquals(1_024L, days[5].bytes)
        assertEquals(LocalDate.parse("2026-11-26"), days[6].date)
        assertEquals(2_048L, days[6].bytes)
        assertEquals(3_072L, weekTotal(days))
    }

    @Test
    fun `空响应返回七个零点而不是失败`() = runTest {
        server.enqueue(jsonResponse(EMPTY_RESPONSE))

        val days = requireNotNull(endpoint.settledDays(credentials, now).successOrNull()) {
            "空数据点不应失败"
        }

        assertEquals(7, days.size)
        assertEquals(0L, weekTotal(days))
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        const val EMPTY_RESPONSE: String =
            """{"TrafficDataPerInterval":{"DataModule":[]}}"""
    }
}
