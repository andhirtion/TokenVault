package com.yg.tokenvault.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 指纹 / 面容当 PIN 用。
 *
 * 做法：
 *  - 在 AndroidKeyStore 里生成一把「每次使用都必须用户认证」的 AES-256-GCM 密钥
 *    （setUserAuthenticationRequired + setInvalidatedByBiometricEnrollment）；
 *  - 开启时验证一次指纹，用这把 key 把 PIN 加密后存进 SharedPreferences；
 *  - 解锁时先弹系统生物识别，验证通过后系统才放行这一次解密，解出 PIN 走原解锁流程。
 *
 * 因此 PIN 明文不落盘，密钥不出安全区；换指纹 / 删光指纹会让 key 永久失效，
 * 这时需要重新开启（此时仍可用 PIN 进入）。
 */
object BiometricVault {
    private const val PREF = "tokenvault_bio"
    private const val KEY_ALIAS = "tokenvault_bio_key"
    private const val K_ENABLED = "enabled"
    private const val K_BLOB = "blob"
    private const val K_IV = "iv"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private val AUTH_LEVEL = BiometricManager.Authenticators.BIOMETRIC_STRONG

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 是否已开启（且本机还留着密文） */
    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, false) && !prefs(ctx).getString(K_BLOB, null).isNullOrEmpty()

    /** 这台设备能不能用生物识别（纯指纹，不含 PIN / 图形兜底） */
    fun canUse(ctx: Context): Boolean = try {
        BiometricManager.from(ctx).canAuthenticate(AUTH_LEVEL) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Throwable) {
        false
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = try {
        (keyStore().getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Throwable) {
        null
    }

    private fun ensureKey(): SecretKey {
        existingKey()?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        gen.init(builder.build())
        return gen.generateKey()
    }

    /** 开启：验证一次指纹，把 PIN 加密封存 */
    fun enable(
        activity: FragmentActivity,
        pin: String,
        onResult: (ok: Boolean, message: String?) -> Unit
    ) {
        if (!canUse(activity)) {
            onResult(false, "这台设备没有可用的指纹或面容")
            return
        }
        val cipher = try {
            Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, ensureKey()) }
        } catch (e: Throwable) {
            onResult(false, "无法创建密钥：${e.message ?: e.javaClass.simpleName}")
            return
        }
        prompt(
            activity = activity,
            cipher = cipher,
            title = "开启指纹解锁",
            subtitle = "验证一次指纹，把访问 PIN 存进安全区",
            negative = "取消",
            onSuccess = { c ->
                try {
                    val iv = c.iv
                    val ct = c.doFinal(pin.toByteArray(Charsets.UTF_8))
                    prefs(activity).edit()
                        .putBoolean(K_ENABLED, true)
                        .putString(K_BLOB, Base64.encodeToString(ct, Base64.NO_WRAP))
                        .putString(K_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                        .apply()
                    onResult(true, null)
                } catch (e: Throwable) {
                    onResult(false, "保存失败：${e.message ?: e.javaClass.simpleName}")
                }
            },
            onError = { msg -> onResult(false, msg) }
        )
    }

    /**
     * 解锁：弹指纹，成功后把 PIN 解出来交给回调。
     * 失败（取消、识别不过、密钥失效）一律回 null，界面继续用 PIN 键盘。
     */
    fun unlock(
        activity: FragmentActivity,
        onPin: (String?) -> Unit,
        onNotice: (String) -> Unit = {}
    ) {
        if (!isEnabled(activity)) {
            onPin(null)
            return
        }
        val key = existingKey()
        if (key == null) {
            disable(activity)
            onNotice("指纹数据已失效，请用 PIN 进入后重新开启")
            onPin(null)
            return
        }
        val ivB64 = prefs(activity).getString(K_IV, null)
        val blobB64 = prefs(activity).getString(K_BLOB, null)
        if (ivB64.isNullOrEmpty() || blobB64.isNullOrEmpty()) {
            disable(activity)
            onPin(null)
            return
        }
        val cipher = try {
            Cipher.getInstance(TRANSFORM).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
                )
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            onNotice("指纹已变更，请用 PIN 进入后重新开启")
            onPin(null)
            return
        } catch (e: Throwable) {
            disable(activity)
            onNotice("指纹不可用，请用 PIN 进入")
            onPin(null)
            return
        }
        prompt(
            activity = activity,
            cipher = cipher,
            title = "指纹解锁",
            subtitle = "验证指纹进入令牌库",
            negative = "改用 PIN",
            onSuccess = { c ->
                try {
                    val pin = String(
                        c.doFinal(Base64.decode(blobB64, Base64.NO_WRAP)),
                        Charsets.UTF_8
                    )
                    onPin(pin)
                } catch (e: Throwable) {
                    disable(activity)
                    onNotice("指纹解密失败，请用 PIN 进入后重新开启")
                    onPin(null)
                }
            },
            onError = { msg ->
                if (msg != null) onNotice(msg)
                onPin(null)
            }
        )
    }

    /** 关闭并抹掉密文与密钥 */
    fun disable(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (_: Throwable) {
        }
    }

    private fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negative: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String?) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c == null) onError("认证结果里没有密钥") else onSuccess(c)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // 用户取消 / 指纹未匹配过多次：都退回 PIN 输入
                onError(null)
            }
        }
        val dialog = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTH_LEVEL)
            .setNegativeButtonText(negative)
            .setConfirmationRequired(false)
            .build()
        dialog.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}