package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import java.io.IOException
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 阿里云 RPC v1（HMAC-SHA1）的统一 HTTP 客户端：签名、超时、限流退避与错误归一都在这里，
 * 后续票据的端点只负责「业务参数 + 响应解析」，不重复处理 HTTP 与错误。
 *
 * 签名算法沿用 `backend-next/server/src/services/cdn-traffic.ts`（ADR 0015 指定）：
 * 公共参数按参数名 ASCII 字典序、RFC3986 编码后用 `&` 连接，
 * `StringToSign = "GET&%2F&" + percentEncode(canonical)`，
 * `Signature = Base64(HMAC-SHA1(secret + "&", StringToSign))`，追加到 query。
 *
 * GET query 与 POST form 二选一：这里固定用 GET query（与既有服务端实现一致），
 * 后续票据不得混用两种形态。
 *
 * @param baseUrl 接口 host（CDN 用 [DEFAULT_CDN_BASE_URL]），可注入 MockWebServer 地址。
 * @param httpClient 可注入；连接/读写超时由 [defaultHttpClient] 统一设置。
 * @param clock 提供签名用的 UTC `Timestamp`，测试可注入固定时刻。
 * @param nonceFactory 每次请求唯一的 `SignatureNonce`。
 * @param retryDelaysMillis 限流（`Throttling`）后的退避序列；默认 200/400/800ms，最多重试 3 次。
 * @param sleeper 退避等待，测试注入空实现即可不真的等待。
 * @param ioDispatcher HTTP 阻塞调用的调度器，测试可注入 TestDispatcher。
 */
class AliyunRpcClient(
    baseUrl: String = DEFAULT_CDN_BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val nonceFactory: () -> String = { UUID.randomUUID().toString() },
    private val retryDelaysMillis: List<Long> = listOf(200L, 400L, 800L),
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()

    /**
     * 发起一次 RPC 调用（含限流重试），返回解析后的 JSON 对象或归一后的失败。
     *
     * `Throttling*` 按 [retryDelaysMillis] 指数退避有限次；重试耗尽后与其他未知业务错误
     * 一样归为 [ApiFailureKind.NETWORK]（spec「错误归一」）。
     */
    suspend fun call(
        account: AccountCredentials,
        action: String,
        version: String,
        businessParams: Map<String, String> = emptyMap(),
    ): ApiResult<JsonObject> {
        var attempt = 0
        while (true) {
            // 每次尝试都重新生成 SignatureNonce/Timestamp：重试也是独立请求，nonce 不重复。
            val params = commonParams(account, action, version) + businessParams
            val url = signedUrl(params, account.accessKeySecret)
            when (val outcome = executeOnce(url)) {
                is RpcOutcome.Success -> return ApiResult.Ok(outcome.body)
                is RpcOutcome.Error -> {
                    val throttled = outcome.code?.startsWith(THROTTLING_CODE_PREFIX) == true
                    if (!throttled || attempt >= retryDelaysMillis.size) {
                        return ApiResult.Failure(
                            // 限流重试耗尽后按网络错误处理；其余失败已是归一结果。
                            kind = if (throttled) ApiFailureKind.NETWORK else outcome.kind,
                            code = outcome.code,
                            httpStatus = outcome.httpStatus,
                        )
                    }
                    sleeper(retryDelaysMillis[attempt])
                    attempt++
                }
            }
        }
    }

    private suspend fun executeOnce(url: HttpUrl): RpcOutcome {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        val response = try {
            withContext(ioDispatcher) { httpClient.newCall(request).execute() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            return RpcOutcome.Error(ApiFailureKind.NETWORK, code = null, httpStatus = null)
        }
        return response.use { handleResponse(it) }
    }

    private fun handleResponse(response: Response): RpcOutcome {
        val raw = try {
            response.body?.string()
        } catch (io: IOException) {
            return RpcOutcome.Error(ApiFailureKind.NETWORK, code = null, httpStatus = response.code)
        }
        val body = raw
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val code = (body?.get("Code") as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        // BSS（QueryAccountBill）的成功响应也带 `Code: "Success"`，不能当成错误码；
        // CDN 的成功响应没有 `Code`，两种形态都要接受。
        if (code != null && !code.equals(SUCCESS_CODE, ignoreCase = true)) {
            return RpcOutcome.Error(
                kind = classifyFailure(code),
                code = code,
                httpStatus = response.code,
            )
        }
        if (!response.isSuccessful) {
            return RpcOutcome.Error(ApiFailureKind.NETWORK, code = null, httpStatus = response.code)
        }
        if (body == null) {
            return RpcOutcome.Error(ApiFailureKind.NETWORK, code = null, httpStatus = response.code)
        }
        return RpcOutcome.Success(body)
    }

    private fun commonParams(
        account: AccountCredentials,
        action: String,
        version: String,
    ): Map<String, String> = linkedMapOf(
        "Action" to action,
        "Version" to version,
        "Format" to "JSON",
        "AccessKeyId" to account.accessKeyId,
        "SignatureMethod" to "HMAC-SHA1",
        "SignatureVersion" to "1.0",
        "SignatureNonce" to nonceFactory(),
        "Timestamp" to formatRpcTimestamp(clock.instant()),
    )

    private fun signedUrl(params: Map<String, String>, accessKeySecret: String): HttpUrl {
        val canonical = params.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }
        val stringToSign = "GET&${percentEncode("/")}&${percentEncode(canonical)}"
        val signature = hmacSha1Base64("$accessKeySecret&", stringToSign)
        // 直接拼已编码的 canonical query：签名里的 `+` `/` `=` 必须显式百分号编码，
        // 交给 HttpUrl.Builder 可能原样保留 `+`（服务端会按空格解码）。
        return "${baseHttpUrl}?$canonical&Signature=${percentEncode(signature)}".toHttpUrl()
    }

    /** 内部结果：HTTP 层不直接构造泛型 [ApiResult]，避免擦除后的类型判断。 */
    private sealed interface RpcOutcome {
        data class Success(val body: JsonObject) : RpcOutcome
        data class Error(
            val kind: ApiFailureKind,
            val code: String?,
            val httpStatus: Int?,
        ) : RpcOutcome
    }

    companion object {
        /** CDN OpenAPI（今日累计、7 天走势、资源包）。 */
        const val DEFAULT_CDN_BASE_URL: String = "https://cdn.aliyuncs.com/"

        /** 费用中心 OpenAPI（账号账单，票据 08）。 */
        const val DEFAULT_BSS_BASE_URL: String = "https://business.aliyuncs.com/"

        private const val THROTTLING_CODE_PREFIX = "Throttling"

        /** BSS OpenAPI 成功响应里的 `Code` 值（CDN 成功响应不带 `Code`）。 */
        private const val SUCCESS_CODE = "Success"

        /** 连接/读写/整体调用都有上限；单账号一次请求最多 30 秒。 */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun hmacSha1Base64(key: String, data: String): String {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
        }

        /**
         * 阿里云签名要求的 RFC3986 编码：仅保留 `A-Z a-z 0-9 - _ . ~`，
         * 空格编码为 `%20`，并补编码 `java.net.URLEncoder` 不处理的 `* ! ' ( )`。
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
    }
}

/**
 * 错误码归一（spec「错误归一」）：只在这里做一次，端点与 UI 都不再关心错误码。
 *
 * - 鉴权类：`InvalidAccessKeyId*`、`SignatureDoesNotMatch`、`InvalidSecurityToken*`
 *   → [ApiFailureKind.CREDENTIAL_INVALID]（GLOSSARY「凭证失效」）。
 * - 权限类：`UnauthorizedOperation`、`NoPermission*`、BSS 的 `NotAuthorized`、
 *   **`Forbidden.RAM`**（RAM 用户对该接口无授权）与其他 `Forbidden.*`
 *   → [ApiFailureKind.PERMISSION_DENIED]。
 * - 其余（含 `Throttling` 重试耗尽、未知业务码）→ [ApiFailureKind.NETWORK]。
 */
internal fun classifyFailure(code: String): ApiFailureKind = when {
    code == "SignatureDoesNotMatch" ||
        code.startsWith("InvalidAccessKeyId") ||
        code.startsWith("InvalidSecurityToken") -> ApiFailureKind.CREDENTIAL_INVALID

    code == "UnauthorizedOperation" ||
        code.startsWith("NoPermission") ||
        // 费用中心（BSS）缺少 bss:QueryAccountBill 时返回 NotAuthorized。
        code.equals("NotAuthorized", ignoreCase = true) ||
        // Forbidden.*（含 Forbidden.RAM）表示 RAM 主体缺少该接口授权，属权限类而非密钥失效。
        code.startsWith("Forbidden") -> ApiFailureKind.PERMISSION_DENIED

    else -> ApiFailureKind.NETWORK
}

private val RPC_TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC)

/** 公共参数与时间窗共用的 UTC 时间戳格式：秒精度、`Z` 结尾（与既有服务端实现一致）。 */
fun formatRpcTimestamp(instant: Instant): String = RPC_TIMESTAMP_FORMAT.format(instant)

/** 解析接口返回的 `TimeStamp`（通常是 UTC `Z`，兼容带偏移的 ISO 8601）；失败返回 null。 */
fun parseAliyunTimestamp(raw: String): Instant? =
    runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
