package com.qqt.music.data.api

import com.qqt.music.AppConfig
import okhttp3.FormBody
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
import android.util.Base64
import java.net.URLEncoder
import java.security.MessageDigest

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

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        )
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    /** GET 指定 URL 并返回 UTF-8 文本；网络失败或非 2xx 返回 null（歌词外链等轻量文本下载用） */
    fun fetchText(url: String): String? = try {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

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
