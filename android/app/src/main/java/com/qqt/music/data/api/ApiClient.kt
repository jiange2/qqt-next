package com.qqt.music.data.api

import com.qqt.music.AppConfig
import com.qqt.music.MEDIA_REFERER
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import com.google.gson.JsonObject
import android.os.Build
import android.util.Base64
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface ApiService {
    @FormUrlEncoded
    @POST("api.php")
    suspend fun callApi(@Field("data") data: String): JsonObject
}

object ApiClient {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.javaObjectType, BooleanAdapter())
        .registerTypeAdapter(Boolean::class.javaPrimitiveType, BooleanAdapter())
        .serializeNulls()
        .create()

    /** 主域 host：仅主域请求参与故障切换，歌词外链等第三方地址不受影响 */
    private val primaryHost: String = AppConfig.BASE_URL.toHttpUrl().host

    /** 回退入口 host/port：切换时整体替换请求的 host 与端口，路径与参数不变 */
    private val fallbackHttpUrl: HttpUrl = AppConfig.FALLBACK_BASE_URL.toHttpUrl()

    /** 主用与回退两个入口的 host（ADR 0017）：「服务器不可达」判定只认这两个入口的请求，CDN 域名不参与 */
    internal val entryHosts: Set<String> = setOf(primaryHost, fallbackHttpUrl.host)

    /** 进程内粘性：一旦切换到回退地址，本次运行期主域请求直接走回退，冷启动恢复主用优先 */
    private val useFallback = AtomicBoolean(false)

    /** 客户端 UA：让设备快照的 User-Agent 列一眼可辨平台与机型（格式对齐 audio-player 的 APA/ 口径）。
     *  Application.onCreate 注入；未注入时不写头（等同旧版 OkHttp 默认 okhttp/x.y.z） */
    private var userAgent: String? = null

    /** versionName 由调用方取 PackageManager（与 app_version 上报同源）；机型含非可见 ASCII 时
     *  整体退化为 "?"，防 OkHttp 头值校验抛异常波及全部请求 */
    fun init(versionName: String) {
        val model = Build.MODEL.let { m -> if (m.isNotEmpty() && m.all { c -> c in ' '..'~' }) m else "?" }
        userAgent = "QQT/${versionName.ifBlank { "?" }} (Android ${Build.VERSION.SDK_INT}; $model)"
    }

    /** 全部请求注入 UA；置于链首以便日志拦截器记录到该头 */
    private val userAgentInterceptor = Interceptor { chain ->
        val ua = userAgent
        val request = if (ua == null) chain.request()
        else chain.request().newBuilder().header("User-Agent", ua).build()
        chain.proceed(request)
    }

    /** 故障切换拦截器：主域请求遭遇连接层失败时改写为回退地址重试一次（仓库 docs/adr/0009） */
    private val failoverInterceptor = Interceptor { chain ->
        var request = chain.request()
        if (useFallback.get() && request.url.host == primaryHost) {
            request = request.withFallbackHost()
        }
        try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (!useFallback.get() && request.url.host == primaryHost && e.isConnectFailure()) {
                useFallback.set(true)
                chain.proceed(request.withFallbackHost())
            } else {
                throw e
            }
        }
    }

    /** 探测发包用的主 client（含 failover 全链）：lateinit + init 赋值，快照拦截器先在链中注册、
     *  client 引用运行时才读，规避「拦截器 ↔ client」的属性初始化环（编译期类型推断成环） */
    private lateinit var okHttpClient: OkHttpClient

    /** 响应快照拦截器（ADR 0012、0017） */
    private val snapshotInterceptor = ResponseSnapshotInterceptor { okHttpClient }

    init {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            // 快照拦截器必须置于 failover 外层（ADR 0017）：连接失败先走完主用+回退两次尝试才回放，
            // 这也是「两入口均失败 = 服务器不可达」判定的前提；回放构造的响应不触网、不经 failover
            .addInterceptor(snapshotInterceptor)
            .addInterceptor(failoverInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
            .build()
    }

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    /** GET 指定 URL 并返回 UTF-8 文本；网络失败或非 2xx 返回 null（歌词外链等轻量文本下载用）。
     *  带 CDN 约定 Referer（ADR 0006）：App 内全部 CDN 出口统一注入，保持单一不变量，为将来 lrc/ 收紧禁空 Referer 预留。 */
    fun fetchText(url: String): String? = try {
        okHttpClient.newCall(
            Request.Builder().url(url).header("Referer", MEDIA_REFERER).build()
        ).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

    /** 把请求 host/port 整体替换为回退入口，路径与查询参数保持不变 */
    private fun Request.withFallbackHost(): Request =
        newBuilder()
            .url(url.newBuilder().host(fallbackHttpUrl.host).port(fallbackHttpUrl.port).build())
            .build()

    /** Encode params as base64(urlencode(json)) with auth fields injected */
    fun buildData(params: Map<String, Any>): String {
        val salt = System.currentTimeMillis().toString()
        val sign = md5("${AppConfig.SIGN_KEY}$salt")
        val merged = params.toMutableMap().apply {
            put("package_name", AppConfig.PACKAGE_NAME)
            put("salt", salt)
            put("sign", sign)
        }
        val json = gson.toJson(merged)
        val urlEncoded = URLEncoder.encode(json, "UTF-8")
        return Base64.encodeToString(urlEncoded.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** 连接层失败判定（ADR 0009、0017）：DNS 解析失败、连接被拒/主机不可达、连接超时；
 *  读超时不切换也不进「服务器不可达」（后端慢时同源 IP 同样慢）。failover 改写重试与
 *  快照拦截器的粘性态判定共用同一口径。 */
internal fun IOException.isConnectFailure(): Boolean = when (this) {
    is UnknownHostException -> true
    is ConnectException, is NoRouteToHostException -> true
    is SocketTimeoutException -> message?.lowercase()?.contains("connect") == true
    else -> false
}
