package com.yg.tokenvault.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 保险库加解密：PBKDF2-SHA256 派生 → AES-256-GCM。
 *
 * 主密码本身从不落盘，也不参与任何校验；解不开密文即视为密码错误。
 */
object VaultCrypto {

    const val ITERATIONS = 250_000
    const val SALT_BYTES = 32
    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(KDF)
            val raw = factory.generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    class Sealed(val iv: ByteArray, val cipherText: ByteArray)

    fun encrypt(key: SecretKey, plain: ByteArray): Sealed {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return Sealed(iv, cipher.doFinal(plain))
    }

    /** 密码错误或数据被篡改时返回 null（GCM 认证失败） */
    fun decrypt(key: SecretKey, iv: ByteArray, cipherText: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(cipherText)
    } catch (e: Exception) {
        null
    }

    fun b64(data: ByteArray): String =
        android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

    fun unb64(text: String): ByteArray? = try {
        android.util.Base64.decode(text, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}