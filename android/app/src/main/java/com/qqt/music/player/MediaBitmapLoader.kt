package com.qqt.music.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.qqt.music.MEDIA_REFERER
import com.qqt.music.mediaImageClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 媒体封面 BitmapLoader（仓库级 ADR 0011）：供 MediaSession 通知/系统媒体卡片加载封面用。
 *
 * Media3 默认 SimpleBitmapLoader 走裸 java.net.URL——既不带 CDN 防盗链 Referer，
 * 也不解混淆字节。本加载器走共享媒体 OkHttp 客户端（mediaImageClient，含 -31 还原
 * 拦截器），封面请求与 Coil 同一条解混淆路径。
 */
class MediaBitmapLoader : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean =
        Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: return Futures.immediateFailedFuture(
                IllegalArgumentException("Could not decode image data")
            )
        return Futures.immediateFuture(bitmap)
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme == "file") {
            val bitmap = BitmapFactory.decodeFile(uri.path)
                ?: return Futures.immediateFailedFuture(
                    IOException("Could not decode artwork file")
                )
            return Futures.immediateFuture(bitmap)
        }
        val future = SettableFuture.create<Bitmap>()
        mediaImageClient.newCall(
            Request.Builder().url(uri.toString()).header("Referer", MEDIA_REFERER).build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.setException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val bytes = it.body?.bytes() ?: throw IOException("empty artwork body")
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw IOException("Could not decode artwork")
                        future.set(bitmap)
                    } catch (e: Exception) {
                        future.setException(e)
                    }
                }
            }
        })
        return future
    }
}
