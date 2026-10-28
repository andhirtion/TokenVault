package com.yg.tokenvault.data

import com.yg.tokenvault.util.Base32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TOTP (RFC 6238) 与 Steam Guard 的实现。
 *
 * 全部本地计算：HmacSHA* → 动态截断 → 取模。Steam 用 5 位 26 字符表。
 */
object Totp {

    data class Result(
        val code: String,
        val remaining: Int,
        val period: Int,
        val progress: Float
    )

    private val macCache = HashMap<String, Mac>()

    private fun mac(algo: String, key: ByteArray): Mac {
        val cacheKey = algo + ":" + key.contentHashCode()
        macCache[cacheKey]?.let { return it }
        val m = Mac.getInstance("Hmac$algo")
        m.init(SecretKeySpec(key, "Hmac$algo"))
        if (macCache.size > 32) macCache.clear()
        macCache[cacheKey] = m
        return m
    }

    /** 计算指定时间戳的验证码；无法解码密钥时返回 null */
    fun generate(
        secret: String,
        timestampMs: Long = System.currentTimeMillis(),
        digits: Int = 6,
        period: Int = 30,
        algo: String = "SHA1",
        charset: String = "NUM"
    ): Result? {
        val key = Base32.decode(secret) ?: return null
        if (key.isEmpty()) return null

        val epochSec = timestampMs / 1000
        val counter = epochSec / period
        val remaining = (period - (epochSec % period)).toInt().coerceIn(1, period)

        val msg = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            msg[i] = (c and 0xFF).toByte()
            c = c shr 8
        }

        val hash = try {
            mac(algo, key).doFinal(msg)
        } catch (e: Exception) {
            return null
        }

        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val code = when (charset) {
            "STEAM" -> buildString {
                var v = binary
                repeat(5) {
                    append(Platforms.STEAM_CHARS[v % Platforms.STEAM_CHARS.length])
                    v /= Platforms.STEAM_CHARS.length
                }
            }
            else -> {
                val mod = 10.0.pow(digits).toInt()
                (binary % mod).toString().padStart(digits, '0')
            }
        }

        return Result(
            code = code,
            remaining = remaining,
            period = period,
            progress = remaining.toFloat() / period.toFloat()
        )
    }

    /** 解析 otpauth://totp/... 链接 */
    data class Parsed(
        val platformKey: String,
        val label: String,
        val account: String,
        val secret: String,
        val digits: Int,
        val period: Int,
        val algo: String,
        val charsetHint: String = "NUM"
    )

    fun parseOtpAuth(uri: String): Parsed? {
        val text = uri.trim()
        if (!text.startsWith("otpauth://", ignoreCase = true)) return null
        return try {
            val withoutScheme = text.substringAfter("://")
            val pathAndQuery = withoutScheme.substringAfter('/', "")
            val path = pathAndQuery.substringBefore('?')
            val query = pathAndQuery.substringAfter('?', "")

            val params = query.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null
                else it.substring(0, i).lowercase() to
                    java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()

            val secret = params["secret"] ?: return null
            val label = java.net.URLDecoder.decode(path, "UTF-8")
            val issuerRaw = params["issuer"]
            val issuer = issuerRaw?.takeIf { it.isNotBlank() } ?: label.substringBefore(':')
            val account = if (label.contains(':')) label.substringAfter(':') else label

            val platform = Platforms.guess(issuer)
            val type = withoutScheme.substringBefore('/').lowercase()
            val charset = if (type == "steam" || platform.key == "steam") "STEAM" else "NUM"

            Parsed(
                platformKey = platform.key,
                label = issuer.trim(),
                account = account.trim(),
                secret = Base32.normalize(secret),
                digits = params["digits"]?.toIntOrNull()
                    ?: if (charset == "STEAM") 5 else platform.digits,
                period = params["period"]?.toIntOrNull() ?: platform.period,
                algo = params["algorithm"]?.uppercase() ?: platform.algo,
                charsetHint = charset
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 把验证码按 3+3 / 4+4 / 2+3 分组 */
    fun format(code: String): String = when (code.length) {
        6 -> code.substring(0, 3) + " " + code.substring(3)
        8 -> code.substring(0, 4) + " " + code.substring(4)
        5 -> code.substring(0, 2) + " " + code.substring(2)
        else -> code
    }
}