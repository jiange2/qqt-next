package com.qqt.music.ui.screens.player

import android.util.Base64
import com.qqt.music.AppConfig
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 歌词密文解密（仓库 docs/adr/0010）：`elrc1:` 前缀文本 = AES-256-CBC + PKCS5Padding，口令取
 *  AppConfig.LRC_CONTENT_SECRET，key/IV 派生与服务端 crypt.ts 的 deriveLrc 严格对齐——
 *  sha256("elrc1-key:口令") 全 32 字节为 key，sha256("elrc1-iv:口令") 前 16 字节为 IV。
 *  无前缀视为明文原样返回（存量文件与内嵌文本）；有前缀但解码/解密失败返回 null，
 *  调用方归入「歌词加载失败」，绝不把密文当明文喂给解析器出乱码 */
internal object LrcCipher {

    private const val PREFIX = "elrc1:"

    private fun derive(info: String, length: Int): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest("$info:${AppConfig.LRC_CONTENT_SECRET}".toByteArray(Charsets.UTF_8))
            .copyOf(length)

    /** 有前缀 → 解密（失败返回 null）；无前缀 → 视为明文原样返回 */
    fun decrypt(text: String): String? {
        if (!text.startsWith(PREFIX)) return text
        return try {
            val data = Base64.decode(text.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derive("elrc1-key", 32), "AES"),
                IvParameterSpec(derive("elrc1-iv", 16)),
            )
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
