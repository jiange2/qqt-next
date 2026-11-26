package com.cdndash.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 加密后的凭据：密文与 IV 均为 base64（NO_WRAP），可直接写入 DataStore。 */
data class SealedCredential(
    val ciphertextBase64: String,
    val ivBase64: String,
)

/**
 * 凭据加解密接缝。生产实现走 Android Keystore；调用方只经 AccountStore 间接使用，
 * 不接触密钥、IV 或密文。
 */
interface CredentialCipher {

    fun seal(plaintext: String): SealedCredential

    /** 解密失败返回 null（例如 Keystore 密钥被系统吊销），不抛出带密文上下文的异常。 */
    fun open(ciphertextBase64: String, ivBase64: String): String?
}

/**
 * 用 AndroidKeystore 中不可导出的 AES-GCM 密钥加解密凭据（ADR 0015、spec「密钥落盘」）。
 *
 * 密钥别名固定：[DEFAULT_ALIAS]。密钥不写日志、不导出、不参与备份；
 * 删除账号只会删除 DataStore 中的密文条目，密钥本身可继续服务其他账号。
 */
class KeystoreCredentialCipher(
    private val alias: String = DEFAULT_ALIAS,
) : CredentialCipher {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private val keyLock = Any()

    override fun seal(plaintext: String): SealedCredential {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return SealedCredential(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    override fun open(ciphertextBase64: String, ivBase64: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(Base64.decode(ciphertextBase64, Base64.NO_WRAP))
            String(plaintext, Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun secretKey(): SecretKey = synchronized(keyLock) {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        /** 固定别名：密钥在应用生命周期内复用，不随账号增删变化。 */
        const val DEFAULT_ALIAS: String = "cdn_dash_credentials_v1"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
