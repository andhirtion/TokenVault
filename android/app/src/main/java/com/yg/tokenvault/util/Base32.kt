package com.yg.tokenvault.util

/**
 * RFC 4648 Base32 编解码（TOTP 密钥用）。
 *
 * 容错：忽略空格、连字符、等号与换行；0→O、1→L 自动纠正；
 * 小写自动转大写。这是导入真实密钥时最常见的几类手抄错误。
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** 合法密钥的最短长度：16 个 Base32 字符 = 80 bit，再短就不可接受了 */
    private const val MIN_LENGTH = 16
    private val INDEX = IntArray(128) { -1 }.also { arr ->
        ALPHABET.forEachIndexed { i, c -> arr[c.code] = i }
    }

    fun normalize(input: String): String = buildString {
        for (raw in input) {
            val c = when (raw) {
                '0' -> 'O'
                '1' -> 'L'
                else -> raw.uppercaseChar()
            }
            if (c in ALPHABET) append(c)
        }
    }

    fun isValid(input: String): Boolean {
        val n = normalize(input)
        return n.length >= MIN_LENGTH
    }

    fun decode(input: String): ByteArray? {
        val s = normalize(input)
        // 太短的一律当无效密钥 —— 否则 "ABC" 这种输入会解出几个字节，
        // 用户以为存进去了，实际生成的码永远对不上
        if (s.length < MIN_LENGTH) return null

        val out = java.io.ByteArrayOutputStream(s.length * 5 / 8 + 1)
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = INDEX[c.code]
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** 生成 n 字节随机密钥的 Base32 文本（默认 20 字节 = 160 bit，RFC 4226 推荐） */
    fun randomSecret(bytes: Int = 20): String {
        val raw = ByteArray(bytes).also { java.security.SecureRandom().nextBytes(it) }
        return encode(raw)
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    /** 每 4 位加空格，便于阅读 */
    fun group(text: String, size: Int = 4): String =
        text.chunked(size).joinToString(" ")
}