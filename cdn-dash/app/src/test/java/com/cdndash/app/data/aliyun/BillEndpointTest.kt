package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.formatMoney
import java.math.BigDecimal
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
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
 * 账单端点（票据 08）的 HTTP 层测试：经由 MockWebServer 验证请求参数、BSS 成功码、
 * 金额拆分、预付费标注与错误归一。期望值是手写字面量，不复算实现。
 */
class BillEndpointTest {

    private lateinit var billServer: MockWebServer
    private lateinit var cdnServer: MockWebServer
    private lateinit var api: AliyunApi

    private val credentials = AccountCredentials(
        accessKeyId = "test-ak",
        accessKeySecret = "test-secret",
    )

    @Before
    fun setUp() {
        billServer = MockWebServer()
        billServer.start()
        cdnServer = MockWebServer()
        cdnServer.start()
        api = AliyunApiImpl(
            cdnRpc = AliyunRpcClient(
                baseUrl = cdnServer.url("/").toString(),
                sleeper = {},
            ),
            bssRpc = AliyunRpcClient(
                baseUrl = billServer.url("/").toString(),
                sleeper = {},
            ),
        )
    }

    @After
    fun tearDown() {
        billServer.shutdown()
        cdnServer.shutdown()
    }

    @Test
    fun `账单请求走 BSS 并携带账期与月粒度参数`() = runTest {
        billServer.enqueue(jsonResponse(successResponse(EMPTY_ITEMS)))

        val result = api.monthlyBills(credentials, listOf(YearMonth.of(2026, 10)))

        requireNotNull(result.successOrNull()) { "空明细也应成功" }
        // 账单走 business.aliyuncs.com（注入的 bssRpc），不能打到 CDN host。
        assertEquals(0, cdnServer.requestCount)
        val request = billServer.takeRequest()
        val url = requireNotNull(request.requestUrl)
        assertEquals("GET", request.method)
        assertEquals("QueryAccountBill", url.queryParameter("Action"))
        assertEquals("2017-12-14", url.queryParameter("Version"))
        assertEquals("2026-10", url.queryParameter("BillingCycle"))
        assertEquals("MONTHLY", url.queryParameter("Granularity"))
        // 按产品分组才会返回 SubscriptionType/ProductCode，预付费标注依赖它们。
        assertEquals("true", url.queryParameter("IsGroupByProduct"))
        assertEquals("300", url.queryParameter("PageSize"))
        assertEquals("1", url.queryParameter("PageNum"))
        // 不按产品过滤：整个账号全部产品合计。
        assertNull(url.queryParameter("ProductCode"))
    }

    @Test
    fun `逐月请求并按传入账期顺序返回`() = runTest {
        billServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cycle = requireNotNull(request.requestUrl?.queryParameter("BillingCycle"))
                val amount = when (cycle) {
                    "2025-12" -> "10.00"
                    "2026-01" -> "20.00"
                    else -> error("未预期的账期 $cycle")
                }
                return jsonResponse(successResponse(item(pretax = amount)))
            }
        }
        val months = listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1))

        val bills = requireNotNull(api.monthlyBills(credentials, months).successOrNull())

        assertEquals(months, bills.map { it.month })
        assertEquals(listOf(BigDecimal("10.00"), BigDecimal("20.00")), bills.map { it.pretaxAmount })
        assertEquals(2, billServer.requestCount)
    }

    @Test
    fun `BSS 成功响应里的 Code 为 Success 不被当作错误并解析金额`() = runTest {
        billServer.enqueue(
            jsonResponse(
                successResponse(
                    item(pretax = "100.50", cash = "60.00", cashCoupons = "5.00", coupons = "2.00") +
                        "," +
                        item(pretax = "23.45", cash = "23.45", productCode = "oss"),
                ),
            ),
        )

        val bill = requireNotNull(
            api.monthlyBills(credentials, listOf(YearMonth.of(2026, 9))).successOrNull(),
        ).single()

        // 主数求和 PretaxAmount；现金与两种代金券字段分列（ADR 0017）。
        assertEquals(BigDecimal("123.95"), bill.pretaxAmount)
        assertEquals(BigDecimal("83.45"), bill.cashAmount)
        assertEquals(BigDecimal("7.00"), bill.voucherAmount)
        assertEquals("CNY", bill.currency)
        assertEquals("123.95", formatMoney(bill.pretaxAmount))
        assertFalse(bill.includesPrepaidPackage)
    }

    @Test
    fun `订阅型 CDN 明细标注含预付费资源包`() = runTest {
        billServer.enqueue(
            jsonResponse(
                successResponse(
                    item(pretax = "3000.00", subscriptionType = "Subscription", productCode = "cdn") +
                        "," +
                        item(pretax = "12.00", subscriptionType = "PayAsYouGo", productCode = "cdn"),
                ),
            ),
        )

        val bill = requireNotNull(
            api.monthlyBills(credentials, listOf(YearMonth.of(2026, 3))).successOrNull(),
        ).single()

        assertTrue(bill.includesPrepaidPackage)
        assertEquals(BigDecimal("3012.00"), bill.pretaxAmount)
    }

    @Test
    fun `后付费或非 CDN 的订阅明细都不标注预付费`() = runTest {
        billServer.enqueue(
            jsonResponse(
                successResponse(
                    item(pretax = "88.00", subscriptionType = "PayAsYouGo", productCode = "cdn") +
                        "," +
                        item(pretax = "120.00", subscriptionType = "Subscription", productCode = "ecs"),
                ),
            ),
        )

        val bill = requireNotNull(
            api.monthlyBills(credentials, listOf(YearMonth.of(2026, 4))).successOrNull(),
        ).single()

        assertFalse(bill.includesPrepaidPackage)
        assertEquals(BigDecimal("208.00"), bill.pretaxAmount)
    }

    @Test
    fun `明细字段缺失不猜金额也不猜预付费`() = runTest {
        billServer.enqueue(
            jsonResponse(
                successResponse("""{"Currency":"CNY"}"""),
            ),
        )

        val bill = requireNotNull(
            api.monthlyBills(credentials, listOf(YearMonth.of(2026, 5))).successOrNull(),
        ).single()

        assertEquals(BigDecimal.ZERO, bill.pretaxAmount)
        assertEquals(BigDecimal.ZERO, bill.cashAmount)
        assertEquals(BigDecimal.ZERO, bill.voucherAmount)
        assertFalse(bill.includesPrepaidPackage)
    }

    @Test
    fun `超过一页时按 TotalCount 继续翻页并累计`() = runTest {
        billServer.enqueue(
            jsonResponse(
                """{"Code":"Success","Success":true,"Data":{"TotalCount":2,
                   "Items":{"Item":[${item(pretax = "10.00")}]}}}""",
            ),
        )
        billServer.enqueue(
            jsonResponse(
                """{"Code":"Success","Success":true,"Data":{"TotalCount":2,
                   "Items":{"Item":[${item(pretax = "20.00")}]}}}""",
            ),
        )

        val bill = requireNotNull(
            api.monthlyBills(credentials, listOf(YearMonth.of(2026, 6))).successOrNull(),
        ).single()

        assertEquals(BigDecimal("30.00"), bill.pretaxAmount)
        assertEquals(2, billServer.requestCount)
        val secondPage = requireNotNull(billServer.takeRequest().requestUrl).queryParameter("PageNum")
        assertEquals("1", secondPage)
        val lastPage = requireNotNull(billServer.takeRequest().requestUrl).queryParameter("PageNum")
        assertEquals("2", lastPage)
    }

    @Test
    fun `权限与鉴权错误分别归一`() = runTest {
        billServer.enqueue(errorResponse(403, "UnauthorizedOperation"))
        assertEquals(
            ApiFailureKind.PERMISSION_DENIED,
            requireNotNull(api.monthlyBills(credentials, listOf(YearMonth.of(2026, 7))).failureOrNull()).kind,
        )

        billServer.enqueue(errorResponse(400, "InvalidAccessKeyId.NotFound"))
        assertEquals(
            ApiFailureKind.CREDENTIAL_INVALID,
            requireNotNull(api.monthlyBills(credentials, listOf(YearMonth.of(2026, 7))).failureOrNull()).kind,
        )

        billServer.enqueue(errorResponse(403, "NoPermission"))
        assertEquals(
            ApiFailureKind.PERMISSION_DENIED,
            requireNotNull(api.monthlyBills(credentials, listOf(YearMonth.of(2026, 7))).failureOrNull()).kind,
        )

        // BSS 缺少 bss:QueryAccountBill 时返回 NotAuthorized，同样归为权限类。
        billServer.enqueue(errorResponse(403, "NotAuthorized"))
        assertEquals(
            ApiFailureKind.PERMISSION_DENIED,
            requireNotNull(api.monthlyBills(credentials, listOf(YearMonth.of(2026, 7))).failureOrNull()).kind,
        )
    }

    @Test
    fun `HTTP 500 无错误码归为网络错误`() = runTest {
        billServer.enqueue(MockResponse().setResponseCode(500))

        val failure = requireNotNull(
            api.monthlyBills(credentials, listOf(YearMonth.of(2026, 8))).failureOrNull(),
        )

        assertEquals(ApiFailureKind.NETWORK, failure.kind)
    }

    private fun successResponse(items: String): String =
        """{"Code":"Success","Message":"Successful!","Success":true,
           "Data":{"TotalCount":1,"Items":{"Item":[$items]}}}"""

    private fun item(
        pretax: String = "0",
        cash: String = "0",
        cashCoupons: String = "0",
        coupons: String = "0",
        subscriptionType: String = "PayAsYouGo",
        productCode: String = "cdn",
    ): String =
        """{"PretaxAmount":"$pretax","CashAmount":"$cash",
           "DeductedByCashCoupons":"$cashCoupons","DeductedByCoupons":"$coupons",
           "SubscriptionType":"$subscriptionType","ProductCode":"$productCode","Currency":"CNY"}"""

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

    private companion object {
        const val EMPTY_ITEMS: String = ""
    }
}
