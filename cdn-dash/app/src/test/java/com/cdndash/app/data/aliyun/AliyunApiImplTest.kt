package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 网络接缝 `AliyunApi` 的 HTTP 层测试（票据 03，spec「Testing Decisions」）。
 *
 * 全部经由 MockWebServer 验证外部行为：请求参数、签名复原、响应解析、错误归一与限流退避。
 * 签名只按「独立复原字符串并比对」的方式验证，不断言中间字符串。
 */
class AliyunApiImplTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AliyunApi

    private val credentials = AccountCredentials(
        accessKeyId = "test-ak",
        accessKeySecret = "test-secret",
    )

    /** 北京时间 2026-11-26 10:30，用于固定时间窗与截止时刻的断言。 */
    private val now = Instant.parse("2026-11-26T02:30:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = AliyunApiImpl(
            cdnRpc = AliyunRpcClient(
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
    fun `今日累计请求携带公共参数与北京时间窗口`() = runTest {
        server.enqueue(jsonResponse(EMPTY_TRAFFIC_RESPONSE))

        api.todayTraffic(credentials, now)

        val request = server.takeRequest()
        val url = requireNotNull(request.requestUrl)
        assertEquals("GET", request.method)
        assertEquals("DescribeDomainTrafficData", url.queryParameter("Action"))
        assertEquals("2018-05-10", url.queryParameter("Version"))
        assertEquals("JSON", url.queryParameter("Format"))
        assertEquals("test-ak", url.queryParameter("AccessKeyId"))
        assertEquals("HMAC-SHA1", url.queryParameter("SignatureMethod"))
        assertEquals("1.0", url.queryParameter("SignatureVersion"))
        assertTrue(requireNotNull(url.queryParameter("SignatureNonce")).isNotBlank())
        assertTrue(
            requireNotNull(url.queryParameter("Timestamp"))
                .matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")),
        )
        // 业务参数：账号全量（不传 DomainName）、5 分钟粒度、北京时间今天 00:00 至请求时刻。
        assertNull(url.queryParameter("DomainName"))
        assertEquals("300", url.queryParameter("Interval"))
        assertEquals("2026-11-25T16:00:00Z", url.queryParameter("StartTime"))
        assertEquals("2026-11-26T02:30:00Z", url.queryParameter("EndTime"))
    }

    @Test
    fun `签名可被同样算法独立复原`() = runTest {
        server.enqueue(jsonResponse(EMPTY_TRAFFIC_RESPONSE))

        api.todayTraffic(credentials, now)

        val url = requireNotNull(server.takeRequest().requestUrl)
        val unsigned = url.queryParameterNames
            .filterNot { it == "Signature" }
            .associateWith { name -> requireNotNull(url.queryParameter(name)) }
        val canonical = unsigned.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }
        val stringToSign = "GET&${percentEncode("/")}&${percentEncode(canonical)}"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(
            SecretKeySpec(
                "${credentials.accessKeySecret}&".toByteArray(Charsets.UTF_8),
                "HmacSHA1",
            ),
        )
        val expected = Base64.getEncoder().encodeToString(
            mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(expected, url.queryParameter("Signature"))
    }

    @Test
    fun `每次请求的 SignatureNonce 不同`() = runTest {
        server.enqueue(jsonResponse(EMPTY_TRAFFIC_RESPONSE))
        server.enqueue(jsonResponse(EMPTY_TRAFFIC_RESPONSE))

        api.todayTraffic(credentials, now)
        api.todayTraffic(credentials, now)

        val first = requireNotNull(server.takeRequest().requestUrl).queryParameter("SignatureNonce")
        val second = requireNotNull(server.takeRequest().requestUrl).queryParameter("SignatureNonce")
        assertNotEquals(first, second)
    }

    @Test
    fun `响应解析为今日累计与数据截止时刻`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"TrafficDataPerInterval":{"DataModule":[
                  {"TimeStamp":"2026-11-26T02:00:00Z","Value":"1024"},
                  {"TimeStamp":"2026-11-26T02:05:00Z","Value":"2048"}
                ]}}
                """.trimIndent(),
            ),
        )

        val result = api.todayTraffic(credentials, now)

        val today = requireNotNull(result.successOrNull()) { "接口应返回成功结果" }
        assertEquals(3072L, today.bytes)
        assertEquals(Instant.parse("2026-11-26T02:05:00Z"), today.dataThrough)
    }

    @Test
    fun `空数据点按 0 且不崩溃`() = runTest {
        server.enqueue(jsonResponse(EMPTY_TRAFFIC_RESPONSE))

        val result = api.todayTraffic(credentials, now)

        val today = requireNotNull(result.successOrNull()) { "空数据点不应失败" }
        assertEquals(0L, today.bytes)
        // 没有数据点时截止时刻退化为请求时刻。
        assertEquals(now, today.dataThrough)
    }

    @Test
    fun `鉴权与权限错误分别归一为凭证失效与权限不足`() = runTest {
        assertFailureKind(
            response = errorResponse(400, "InvalidAccessKeyId.NotFound"),
            expected = ApiFailureKind.CREDENTIAL_INVALID,
        )
        assertFailureKind(
            response = errorResponse(400, "SignatureDoesNotMatch"),
            expected = ApiFailureKind.CREDENTIAL_INVALID,
        )
        assertFailureKind(
            response = errorResponse(403, "Forbidden.RAM"),
            expected = ApiFailureKind.CREDENTIAL_INVALID,
        )
        assertFailureKind(
            response = errorResponse(403, "UnauthorizedOperation"),
            expected = ApiFailureKind.PERMISSION_DENIED,
        )
        assertFailureKind(
            response = errorResponse(403, "NoPermission"),
            expected = ApiFailureKind.PERMISSION_DENIED,
        )
    }

    @Test
    fun `HTTP 非 2xx 且无错误码归为网络错误`() = runTest {
        assertFailureKind(
            response = MockResponse().setResponseCode(500),
            expected = ApiFailureKind.NETWORK,
        )
    }

    @Test
    fun `连接失败归为网络错误`() = runTest {
        // 直接断连；OkHttp 可能自动重试一次，dispatcher 每次都断，避免队列为空时阻塞。
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }

        val result = api.todayTraffic(credentials, now)

        assertEquals(ApiFailureKind.NETWORK, requireNotNull(result.failureOrNull()).kind)
    }

    @Test
    fun `限流退避有限次后归为网络错误`() = runTest {
        repeat(4) { server.enqueue(errorResponse(503, "Throttling")) }

        val result = api.todayTraffic(credentials, now)

        val failure = requireNotNull(result.failureOrNull()) { "重试耗尽应失败" }
        assertEquals(ApiFailureKind.NETWORK, failure.kind)
        assertEquals("Throttling", failure.code)
        // 1 次初始请求 + 3 次退避重试。
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `限流后重试成功仍返回数据`() = runTest {
        server.enqueue(errorResponse(503, "Throttling"))
        server.enqueue(
            jsonResponse(
                """{"TrafficDataPerInterval":{"DataModule":[
                   {"TimeStamp":"2026-11-26T02:05:00Z","Value":"512"}
                ]}}""",
            ),
        )

        val result = api.todayTraffic(credentials, now)

        assertEquals(512L, requireNotNull(result.successOrNull()).bytes)
        assertEquals(2, server.requestCount)
    }

    private suspend fun assertFailureKind(response: MockResponse, expected: ApiFailureKind) {
        server.enqueue(response)
        val result = api.todayTraffic(credentials, now)
        assertEquals(expected, requireNotNull(result.failureOrNull()).kind)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun errorResponse(status: Int, code: String): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"Code":"$code","Message":"stub","RequestId":"test"}""")

    /**
     * 测试侧独立实现的 RFC3986 编码，刻意不复用生产代码：
     * `URLEncoder` 不编码 `! ' ( )`、把空格写成 `+`，这里补齐并替换。
     */
    private fun percentEncode(input: String): String =
        URLEncoder.encode(input, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
            .replace("!", "%21")
            .replace("'", "%27")
            .replace("(", "%28")
            .replace(")", "%29")

    private companion object {
        const val EMPTY_TRAFFIC_RESPONSE: String =
            """{"TrafficDataPerInterval":{"DataModule":[]}}"""
    }
}
