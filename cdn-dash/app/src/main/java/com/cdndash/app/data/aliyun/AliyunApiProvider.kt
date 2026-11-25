package com.cdndash.app.data.aliyun

/**
 * 生产环境的网络接缝组装点：两个 host 共用一个 OkHttpClient（连接池与超时一致）。
 * 不在此处读取任何账号信息；凭据由 ViewModel 按账号从 AccountStore 读出后传入。
 */
object AliyunApiProvider {

    fun create(): AliyunApi {
        val httpClient = AliyunRpcClient.defaultHttpClient()
        return AliyunApiImpl(
            cdnRpc = AliyunRpcClient(
                baseUrl = AliyunRpcClient.DEFAULT_CDN_BASE_URL,
                httpClient = httpClient,
            ),
            bssRpc = AliyunRpcClient(
                baseUrl = AliyunRpcClient.DEFAULT_BSS_BASE_URL,
                httpClient = httpClient,
            ),
        )
    }
}
