package com.qqt.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.qqt.music.data.api.ApiClient
import com.qqt.music.data.api.ResponseSnapshotStore
import com.qqt.music.data.local.NetworkMonitor
import com.qqt.music.update.AppUpdateChecker
import okhttp3.Interceptor as OkInterceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.util.concurrent.TimeUnit

/** CDN 防盗链约定 Referer：包名作 host，非真实来源页面（仓库级 ADR 0006）。换值 = 发版。 */
const val MEDIA_REFERER = "http://com.qqt.music/"

/** 图片加载共享 OkHttp 客户端：统一注入 -31 解混淆拦截器（仓库级 ADR 0011），Coil 与通知封面 BitmapLoader 共用 */
val mediaImageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(MediaByteDeobfuscator())
        .build()
}

class QqtApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // 响应快照与断网检测（ADR 0012）：先于任何网络请求初始化，
        // 未就绪时拦截器/横幅自动退化为直连与不显示，不会崩溃
        ResponseSnapshotStore.init(this)
        NetworkMonitor.init(this)
        // 自定义 UA（让设备快照的 User-Agent 列可辨平台/机型）：versionName 与 app_version 上报同源
        ApiClient.init(AppUpdateChecker.currentVersionName(this))
        // 旧版 Coil 盘存目录一次性清除（仓库级 ADR 0011 割接）：v1=旧默认目录；
        // v2=混淆初版无嗅探时写下的乱码条目（解码失败但源字节已入库）。本进程 Coil 只用 v3，删除无竞争
        Thread {
            listOf("image_cache", "image_cache_v2").forEach { cacheDir.resolve(it).deleteRecursively() }
        }.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(MediaRefererInterceptor()) }
            .okHttpClient(mediaImageClient)
            // 盘存目录版本化（仓库级 ADR 0011 割接）：容量策略保持 Coil 默认（2% 磁盘），
            // 目录名换版即整体作废旧条目，与 AudioCache 缓存键版本化同一机制
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache_v3"))
                    .build()
            }
            .build()
}

/** 为所有图片请求注入防盗链 Referer（Coil 默认不带 Referer，会被 CDN 白名单拒绝） */
class MediaRefererInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request.newBuilder()
            .headers(chain.request.headers.newBuilder().set("Referer", MEDIA_REFERER).build())
            .build()
        return chain.proceed(request)
    }
}

/** OSS 图片对象逐字节 +31 混淆存储（仓库级 ADR 0011）：媒体 host 的响应在字节流层面还原。
 *  前 12 字节魔数嗅探兼容迁移期——CDN 残留明文旧缓存直认透传，混淆态逐字节 -31 还原；
 *  图片魔数空间稀疏（混淆态误判为明文的概率可忽略），音频链路无可靠魔数仍走无条件还原 */
class MediaByteDeobfuscator : OkInterceptor {

    override fun intercept(chain: OkInterceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val body = response.body ?: return response
        if (request.url.host != AppConfig.MEDIA_HOST) return response
        val source = body.source()
        val head = Buffer()
        while (head.size < SNIFF_BYTES && source.read(head, SNIFF_BYTES - head.size) != -1L) {
            // 尽力读满嗅探窗口，流提前结束即停
        }
        if (head.size == 0L) return response
        val prefixed: Source = HeadPrefixedSource(head, source)
        val restored: Source = if (isPlaintextImage(head)) prefixed else DeobfuscatingSource(prefixed)
        return response.newBuilder()
            .body(object : ResponseBody() {
                override fun contentType(): MediaType? = body.contentType()
                override fun contentLength(): Long = body.contentLength()
                override fun source(): BufferedSource = restored.buffer()
            })
            .build()
    }

    private companion object {
        /** 覆盖 AVIF ftyp 偏移 4~7 的最小嗅探窗口 */
        const val SNIFF_BYTES = 12L
    }
}

private fun isPlaintextImage(head: Buffer): Boolean {
    val at = { i: Int -> head[i.toLong()].toInt() and 0xff }
    return (head.size >= 3 && at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) ||
        (head.size >= 8 && at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 &&
            at(4) == 0x0D && at(5) == 0x0A && at(6) == 0x1A && at(7) == 0x0A) ||
        (head.size >= 4 && at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38) ||
        (head.size >= 12 && at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
            at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50) ||
        (head.size >= 2 && at(0) == 0x42 && at(1) == 0x4D) ||
        (head.size >= 8 && at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70)
}

/** 先吐出嗅探窗口字节再续接上游 */
private class HeadPrefixedSource(private val head: Buffer, private val delegate: Source) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (!head.exhausted()) return head.read(sink, byteCount)
        return delegate.read(sink, byteCount)
    }

    override fun timeout() = delegate.timeout()

    override fun close() {
        head.close()
        delegate.close()
    }
}

private class DeobfuscatingSource(delegate: Source) : ForwardingSource(delegate) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        val temp = Buffer()
        val read = super.read(temp, byteCount)
        if (read > 0) {
            val bytes = temp.readByteArray()
            for (i in bytes.indices) {
                bytes[i] = ((bytes[i].toInt() + 256 - AppConfig.MEDIA_BYTE_SHIFT) and 0xff).toByte()
            }
            sink.write(bytes)
        }
        return read
    }
}
