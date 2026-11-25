package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.ResourcePackage
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 资源包端点的 HTTP 层测试（票据 05）：经 [AliyunApi] 接缝验证双端点
 * （CDN `DescribeCdnUserResourcePackage` + DCDN `DescribeDcdnUserResourcePackage`）、
 * 只查 valid 状态、商品码/容量单位过滤、用完排除、InstanceId 去重、响应解析与字段缺失容忍。
 *
 * 单 MockWebServer 承载两个端点，按 `Action` 区分派发。
 */
class PackageEndpointTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AliyunApi

    private val credentials = AccountCredentials(
        accessKeyId = "test-ak",
        accessKeySecret = "test-secret",
    )

    private val now: Instant = Instant.parse("2026-11-26T02:30:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = packageDispatcher()
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
    fun `双端点只查 valid 状态且携带正确的 Action 与 Version`() = runTest {
        api.resourcePackages(credentials, now)

        assertEquals(2, server.requestCount)
        val requests = (1..server.requestCount).map {
            val url = requireNotNull(server.takeRequest().requestUrl)
            Triple(
                url.queryParameter("Action"),
                url.queryParameter("Version"),
                url.queryParameter("Status"),
            )
        }
        val cdn = requests.filter { it.first == "DescribeCdnUserResourcePackage" }
        val dcdn = requests.filter { it.first == "DescribeDcdnUserResourcePackage" }
        assertEquals(1, cdn.size)
        assertEquals(1, dcdn.size)
        assertTrue(cdn.all { it.second == "2018-05-10" })
        assertTrue(dcdn.all { it.second == "2018-01-15" })
        // 已耗尽/已关闭的包不展示，只查 valid。
        assertEquals("valid", cdn.single().third)
        assertEquals("valid", dcdn.single().third)
    }

    @Test
    fun `保留 cdnflowbag 与 dcdnpaybag 流量包并排除其他商品码与请求数包`() = runTest {
        server.dispatcher = packageDispatcher(
            cdnBody = """
                {"ResourcePackageInfos":{"ResourcePackageInfo":[
                  $CDN_PACKAGE,
                  {"CommodityCode":"dcdnflowbag","InstanceId":"dcdn-1","Region":"中国内地",
                   "InitCapacity":"999","CurrCapacity":"999",
                   "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T00:00:00Z"},
                  {"CommodityCode":"ossflowbag","InstanceId":"oss-1","Region":"中国内地",
                   "InitCapacity":"888","CurrCapacity":"888",
                   "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T00:00:00Z"},
                  {"InstanceId":"no-commodity","Region":"中国内地",
                   "InitCapacity":"777","CurrCapacity":"777"}
                ]}}
            """.trimIndent(),
            dcdnBody = """
                {"ResourcePackageInfos":{"ResourcePackageInfo":[
                  $SHARED_PACKAGE,
                  {"CommodityCode":"dcdnpaybag","InstanceId":"https-1","Region":"中国内地",
                   "InitCapacity":"5000000","CurrCapacity":"5000000",
                   "InitCapacityBaseUnit":"次","CurrCapacityBaseUnit":"次",
                   "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T00:00:00Z"}
                ]}}
            """.trimIndent(),
        )

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(listOf("pkg-1", "shared-1"), packages.map { it.instanceId })
        assertEquals(
            listOf("cdnflowbag", "dcdnpaybag"),
            packages.map { it.commodityCode },
        )
    }

    @Test
    fun `解析总量 剩余量 区域 生效时间与到期时间`() = runTest {
        server.dispatcher = packageDispatcher(cdnBody = PACKAGE_RESPONSE)

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        val packageInfo = packages.single()
        assertEquals("pkg-1", packageInfo.instanceId)
        assertEquals("cdnflowbag", packageInfo.commodityCode)
        assertEquals("中国内地", packageInfo.region)
        assertEquals(1_073_741_824L, packageInfo.totalBytes)
        assertEquals(536_870_912L, packageInfo.remainingBytes)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), packageInfo.effectiveAt)
        assertEquals(Instant.parse("2027-12-31T16:00:00Z"), packageInfo.expiresAt)
    }

    @Test
    fun `字段缺失时按兜底值解析且不崩溃`() = runTest {
        server.dispatcher = packageDispatcher(
            cdnBody = """
                {"ResourcePackageInfos":{"ResourcePackageInfo":[
                  {"CommodityCode":"cdnflowbag","InstanceId":"sparse","InitCapacity":"100","CurrCapacity":"50"}
                ]}}
            """.trimIndent(),
        )

        val packageInfo = requireNotNull(api.resourcePackages(credentials, now).successOrNull()).single()

        assertEquals("sparse", packageInfo.instanceId)
        assertEquals(ResourcePackage.UNKNOWN_REGION, packageInfo.region)
        assertEquals(100L, packageInfo.totalBytes)
        assertEquals(50L, packageInfo.remainingBytes)
        assertEquals(Instant.EPOCH, packageInfo.effectiveAt)
        assertEquals(Instant.EPOCH, packageInfo.expiresAt)
    }

    @Test
    fun `剩余量为 0 或负数的包不展示`() = runTest {
        server.dispatcher = packageDispatcher(
            cdnBody = """
                {"ResourcePackageInfos":{"ResourcePackageInfo":[
                  $CDN_PACKAGE,
                  {"CommodityCode":"cdnflowbag","InstanceId":"used-up","Region":"中国内地",
                   "InitCapacity":"1073741824","CurrCapacity":"0",
                   "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T16:00:00Z"},
                  {"CommodityCode":"cdnflowbag","InstanceId":"no-remaining","Region":"中国内地",
                   "InitCapacity":"1073741824",
                   "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T16:00:00Z"}
                ]}}
            """.trimIndent(),
        )

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(listOf("pkg-1"), packages.map { it.instanceId })
    }

    @Test
    fun `空列表与缺失包装节点都返回空列表`() = runTest {
        server.dispatcher = packageDispatcher(cdnBody = """{"RequestId":"test"}""")

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(emptyList<ResourcePackage>(), packages)
    }

    @Test
    fun `单个条目返回对象而不是数组时也能解析`() = runTest {
        server.dispatcher = packageDispatcher(
            cdnBody = """{"ResourcePackageInfos":{"ResourcePackageInfo":$CDN_PACKAGE}}""",
        )

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(1, packages.size)
        assertEquals("pkg-1", packages.single().instanceId)
    }

    @Test
    fun `同一实例跨端点重复返回时按 InstanceId 去重`() = runTest {
        // 同一包在 CDN 与 DCDN 端点都出现（共享商品）。
        server.dispatcher = packageDispatcher(
            cdnBody = PACKAGE_RESPONSE,
            dcdnBody = PACKAGE_RESPONSE,
        )

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(listOf("pkg-1"), packages.map { it.instanceId })
    }

    @Test
    fun `DCDN 端点失败时容忍并返回 CDN 端点结果`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.queryParameter("Action")) {
                    "DescribeCdnUserResourcePackage" -> jsonResponse(PACKAGE_RESPONSE)
                    else -> MockResponse()
                        .setResponseCode(403)
                        .setBody("""{"Code":"DcdnServiceNotFound","Message":"stub"}""")
                }
        }

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(listOf("pkg-1"), packages.map { it.instanceId })
    }

    @Test
    fun `CDN 主端点失败时整体失败即使 DCDN 成功`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.queryParameter("Action")) {
                    "DescribeCdnUserResourcePackage" -> MockResponse().setResponseCode(500)
                    else -> jsonResponse(PACKAGE_RESPONSE)
                }
        }

        val failure = requireNotNull(api.resourcePackages(credentials, now).failureOrNull())

        assertEquals(ApiFailureKind.NETWORK, failure.kind)
    }

    /** 默认两端点都回空；可按 Action 覆盖响应体。 */
    private fun packageDispatcher(
        cdnBody: String = EMPTY_RESPONSE,
        dcdnBody: String = EMPTY_RESPONSE,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            when (request.requestUrl?.queryParameter("Action")) {
                "DescribeCdnUserResourcePackage" -> jsonResponse(cdnBody)
                "DescribeDcdnUserResourcePackage" -> jsonResponse(dcdnBody)
                else -> MockResponse().setResponseCode(404)
            }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private companion object {
        const val CDN_PACKAGE: String =
            """{"CommodityCode":"cdnflowbag","InstanceId":"pkg-1","Region":"中国内地",
               "InitCapacity":"1073741824","CurrCapacity":"536870912",
               "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T16:00:00Z"}"""

        const val SHARED_PACKAGE: String =
            """{"CommodityCode":"dcdnpaybag","InstanceId":"shared-1","Region":"中国内地",
               "InitCapacity":"107374182400","CurrCapacity":"53661095687",
               "InitCapacityBaseUnit":"Byte","CurrCapacityBaseUnit":"Byte",
               "StartTime":"2026-11-01T00:00:00Z","EndTime":"2027-12-31T16:00:00Z"}"""

        const val EMPTY_RESPONSE: String =
            """{"RequestId":"test","ResourcePackageInfos":{"ResourcePackageInfo":[]}}"""

        // 非 const：内容包含 const 字符串的插值，避免依赖编译期常量表达式的边界行为。
        val PACKAGE_RESPONSE: String =
            """{"RequestId":"test","ResourcePackageInfos":{"ResourcePackageInfo":[$CDN_PACKAGE]}}"""
    }
}