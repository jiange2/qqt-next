package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.ResourcePackage
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 资源包端点的 HTTP 层测试（票据 05）：经 [AliyunApi] 接缝验证
 * `Action`/`Version`、`cdnflowbag` 过滤、响应解析与字段缺失容忍。
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
    fun `资源包请求携带正确的 Action 与 Version 且不按状态过滤`() = runTest {
        server.enqueue(jsonResponse(EMPTY_RESPONSE))

        api.resourcePackages(credentials, now)

        val request = server.takeRequest()
        val url = requireNotNull(request.requestUrl)
        assertEquals("GET", request.method)
        assertEquals("DescribeCdnUserResourcePackage", url.queryParameter("Action"))
        assertEquals("2017-11-10", url.queryParameter("Version"))
        assertEquals("JSON", url.queryParameter("Format"))
        // 不传 Status：默认只回 valid，会把已耗尽/已关闭的包藏起来，余量视图要全部列出。
        assertNull(url.queryParameter("Status"))
    }

    @Test
    fun `只保留 CommodityCode 为 cdnflowbag 的条目`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"ResourcePackageInfos":{"ResourcePackageInfo":[
                  $CDN_PACKAGE,
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
            ),
        )

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(2, packages.size)
        assertTrue(packages.all { it.commodityCode == "cdnflowbag" })
        assertEquals(listOf("pkg-1", "pkg-1"), packages.map { it.instanceId })
    }

    @Test
    fun `解析总量 剩余量 区域 生效时间与到期时间`() = runTest {
        server.enqueue(jsonResponse(PACKAGE_RESPONSE))

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
        server.enqueue(
            jsonResponse(
                """
                {"ResourcePackageInfos":{"ResourcePackageInfo":[
                  {"CommodityCode":"cdnflowbag","InstanceId":"sparse","InitCapacity":"100"}
                ]}}
                """.trimIndent(),
            ),
        )

        val packageInfo = requireNotNull(api.resourcePackages(credentials, now).successOrNull()).single()

        assertEquals("sparse", packageInfo.instanceId)
        assertEquals(ResourcePackage.UNKNOWN_REGION, packageInfo.region)
        assertEquals(100L, packageInfo.totalBytes)
        assertEquals(0L, packageInfo.remainingBytes)
        assertEquals(Instant.EPOCH, packageInfo.effectiveAt)
        assertEquals(Instant.EPOCH, packageInfo.expiresAt)
    }

    @Test
    fun `空列表与缺失包装节点都返回空列表`() = runTest {
        server.enqueue(jsonResponse(EMPTY_RESPONSE))
        server.enqueue(jsonResponse("""{"RequestId":"test"}"""))

        val empty = requireNotNull(api.resourcePackages(credentials, now).successOrNull())
        val missingNode = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(emptyList<ResourcePackage>(), empty)
        assertEquals(emptyList<ResourcePackage>(), missingNode)
    }

    @Test
    fun `单个条目返回对象而不是数组时也能解析`() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"ResourcePackageInfos":{"ResourcePackageInfo":$CDN_PACKAGE}}
                """.trimIndent(),
            ),
        )

        val packages = requireNotNull(api.resourcePackages(credentials, now).successOrNull())

        assertEquals(1, packages.size)
        assertEquals("pkg-1", packages.single().instanceId)
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

        const val EMPTY_RESPONSE: String =
            """{"RequestId":"test","ResourcePackageInfos":{"ResourcePackageInfo":[]}}"""

        // 非 const：内容包含 const 字符串的插值，避免依赖编译期常量表达式的边界行为。
        val PACKAGE_RESPONSE: String =
            """{"RequestId":"test","ResourcePackageInfos":{"ResourcePackageInfo":[$CDN_PACKAGE]}}"""
    }
}
