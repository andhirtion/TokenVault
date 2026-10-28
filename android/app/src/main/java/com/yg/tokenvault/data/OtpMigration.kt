package com.yg.tokenvault.data

import com.yg.tokenvault.util.Base32

/**
 * 解析 Google Authenticator 的导出链接 otpauth-migration://offline?data=...
 *
 * data 是 URL-safe base64 编码的 protobuf（MigrationPayload），格式见
 * https://github.com/google/google-authenticator-android 的 proto/otp-migration.proto。
 *
 * 自己手写 varint / length-delimited 解析，不引入 protobuf 运行时。
 */
object OtpMigration {

    /** 极简 protobuf 读取器：只管 varint 与 length-delimited 两种最常用的 wire type */
    private class Reader(private val buf: ByteArray) {
        var pos = 0
            private set

        fun hasMore(): Boolean = pos < buf.size

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (pos < buf.size) {
                val b = buf[pos].toInt() and 0xFF
                pos++
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                if (shift > 63) return result
            }
            return result
        }

        fun bytes(n: Int): ByteArray {
            val len = n.coerceAtLeast(0)
            val end = minOf(pos + len, buf.size)
            val out = buf.copyOfRange(pos, end)
            pos = end
            return out
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> pos += varint().toInt()
                5 -> pos += 4
                else -> pos = buf.size
            }
            if (pos > buf.size) pos = buf.size
            if (pos < 0) pos = buf.size
        }
    }

    /** 把 otpauth-migration:// 链接解析成若干条密钥 */
    fun parse(uri: String): List<Totp.Parsed> {
        val text = uri.trim()
        if (!text.startsWith("otpauth-migration://", ignoreCase = true)) return emptyList()

        val query = text.substringAfter('?', "")
        if (query.isEmpty()) return emptyList()
        var raw: String? = null
        for (pair in query.split('&')) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            if (pair.substring(0, i).equals("data", ignoreCase = true)) {
                raw = pair.substring(i + 1)
                break
            }
        }
        val data = raw ?: return emptyList()
        val bytes = base64UrlDecode(data) ?: return emptyList()

        val out = ArrayList<Totp.Parsed>()
        val reader = Reader(bytes)
        while (reader.hasMore()) {
            val key = reader.varint()
            val wire = (key and 0x07).toInt()
            val field = (key shr 3).toInt()
            if (wire == 2) {
                val len = reader.varint().toInt()
                val payload = reader.bytes(len)
                // field 1 = repeated OtpParameters
                if (field == 1) readParams(payload)?.let { out.add(it) }
            } else {
                reader.skip(wire)
            }
        }
        return out
    }

    /** 解析单条 OtpParameters */
    private fun readParams(b: ByteArray): Totp.Parsed? {
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = 1 // 1=SHA1 2=SHA256 3=SHA512
        var digitCount = 1 // 1=6 位 2=8 位
        var otpType = 2 // 1=HOTP 2=TOTP

        val r = Reader(b)
        while (r.hasMore()) {
            val key = r.varint()
            val wire = (key and 0x07).toInt()
            val field = (key shr 3).toInt()
            when (wire) {
                2 -> {
                    val len = r.varint().toInt()
                    val v = r.bytes(len)
                    when (field) {
                        1 -> secret = v
                        2 -> name = String(v, Charsets.UTF_8)
                        3 -> issuer = String(v, Charsets.UTF_8)
                    }
                }
                0 -> {
                    val v = r.varint().toInt()
                    when (field) {
                        4 -> algorithm = v
                        5 -> digitCount = v
                        6 -> otpType = v
                    }
                }
                else -> r.skip(wire)
            }
        }

        val sec = secret ?: return null
        if (sec.isEmpty()) return null
        // HOTP 是计数器型，本应用只做 TOTP
        if (otpType == 1) return null

        val secretB32 = Base32.encode(sec)
        val platform = Platforms.guess(if (issuer.isNotBlank()) issuer else name)
        val charset = if (platform.key == "steam") "STEAM" else "NUM"
        val algoName = when (algorithm) {
            2 -> "SHA256"
            3 -> "SHA512"
            else -> "SHA1"
        }

        return Totp.Parsed(
            platformKey = platform.key,
            label = if (issuer.isNotBlank()) issuer.trim() else platform.label,
            account = (if (name.contains(':')) name.substringAfter(':') else name).trim(),
            secret = secretB32,
            digits = if (charset == "STEAM") 5 else if (digitCount == 2) 8 else 6,
            period = platform.period,
            algo = if (charset == "STEAM") "SHA1" else algoName,
            charsetHint = charset
        )
    }

    /** URL-safe base64 解码（自动补 padding，容忍标准 base64 字符） */
    fun base64UrlDecode(input: String): ByteArray? {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '-', '+' -> sb.append('+')
                '_', '/' -> sb.append('/')
                '=', '\n', '\r', ' ', '\t' -> {}
                else -> if (c.isLetterOrDigit()) sb.append(c)
            }
        }
        val s = sb.toString()
        if (s.isEmpty()) return null
        val pad = (4 - s.length % 4) % 4
        val padded = s + "=".repeat(pad)
        return try {
            java.util.Base64.getDecoder().decode(padded)
        } catch (e: Exception) {
            null
        }
    }
}