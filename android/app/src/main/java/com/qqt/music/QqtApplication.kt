package com.qqt.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.intercept.Interceptor
import coil.request.ImageResult

/** CDN 防盗链约定 Referer：包名作 host，非真实来源页面（仓库级 ADR 0006）。换值 = 发版。 */
const val MEDIA_REFERER = "http://com.qqt.music/"

class QqtApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(MediaRefererInterceptor()) }
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
