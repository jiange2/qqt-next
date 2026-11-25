package com.cdndash.app.data.aliyun

/**
 * 数据层对 UI/ViewModel 的唯一结果类型：成功携带领域对象，失败携带归一后的原因。
 *
 * 错误归一（spec「错误归一」）只发生在 [AliyunRpcClient]：UI 不解析 HTTP 状态码、
 * 不看阿里云错误码，只按 [ApiFailureKind] 渲染文案。单账号失败只产生该账号的
 * [Failure]，不会抛出到调用方之外。
 */
sealed interface ApiResult<out T> {

    data class Ok<out T>(val value: T) : ApiResult<T>

    /**
     * @property kind 归一后的失败原因，直接决定卡片上的「凭证失效 / 权限不足 / 网络错误」。
     * @property code 阿里云返回的错误码（如 `InvalidAccessKeyId.NotFound`），仅用于排查，不展示。
     * @property httpStatus HTTP 状态码，仅用于排查。
     */
    data class Failure(
        val kind: ApiFailureKind,
        val code: String? = null,
        val httpStatus: Int? = null,
    ) : ApiResult<Nothing>
}

/** 归一后的失败原因；与 spec「错误归一」三类一一对应。 */
enum class ApiFailureKind {
    /** 鉴权类：AccessKeyId 无效、签名不匹配、RAM 主体被禁用 → 卡片「凭证失效」。 */
    CREDENTIAL_INVALID,

    /** 权限类：RAM 用户缺少对应接口的只读权限 → 卡片「权限不足」。 */
    PERMISSION_DENIED,

    /** 网络/超时/限流重试耗尽/未知业务错误 → 卡片「网络错误」。 */
    NETWORK,
}

/** 成功时返回领域对象，失败时返回 null；调用方只关心成功分支时使用。 */
@Suppress("UNCHECKED_CAST")
fun <T> ApiResult<T>.successOrNull(): T? = when (this) {
    is ApiResult.Ok<*> -> value as T
    is ApiResult.Failure -> null
}

/** 失败原因；成功时为 null。 */
fun ApiResult<*>.failureOrNull(): ApiResult.Failure? = this as? ApiResult.Failure

/** 成功时把领域对象变换为另一个领域对象，失败原样透传（端点解析响应时使用）。 */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok<*> -> ApiResult.Ok(transform(successOrNull()!!))
    is ApiResult.Failure -> this
}

/** 两个分支都有副作用时使用，避免调用方对泛型做 `is` 判断。 */
inline fun <T, R> ApiResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (ApiResult.Failure) -> R,
): R = when (this) {
    is ApiResult.Ok<*> -> onSuccess(successOrNull()!!)
    is ApiResult.Failure -> onFailure(this)
}
