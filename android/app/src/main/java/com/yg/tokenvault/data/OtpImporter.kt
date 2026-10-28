package com.yg.tokenvault.data

import com.yg.tokenvault.util.Base32

/**
 * 把「任意来源的一段文本」变成可入库的密钥列表。
 *
 * 输入可能是：
 *  - 扫码得到的单个 otpauth://totp/... 链接
 *  - Google Authenticator 导出的 otpauth-migration://offline?data=...
 *  - 网页里抓到的 HTML 片段（含多个 otpauth:// 链接，可能带 &amp; 转义）
 *  - 用户手输的一串 Base32（16 位以上）
 */
object OtpImporter {

    /** 一条解析结果 + 它来自哪种途径，用于给用户提示 */
    data class Item(val parsed: Totp.Parsed, val origin: Origin)

    enum class Origin { QR_CODE, MIGRATION, LINK, RAW_SECRET, WEB }

    private val MIGRATION_RE = Regex("""otpauth-migration://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
    private val OTPAUTH_RE = Regex("""otpauth://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)

    /**
     * 主入口：识别一段文本里的所有密钥。
     * @param origin 提示来源（扫码 / 网页 / 粘贴）
     */
    fun parse(text: String?, origin: Origin = Origin.LINK): List<Item> {
        if (text.isNullOrBlank()) return emptyList()
        val cleaned = unescape(text)
        val out = LinkedHashMap<String, Item>()

        // 1) Google Authenticator 批量导出
        for (m in MIGRATION_RE.findAll(cleaned)) {
            for (p in OtpMigration.parse(m.value)) {
                out.putIfAbsent(p.secret, Item(p, Origin.MIGRATION))
            }
        }

        // 2) 普通 otpauth:// 链接
        for (m in OTPAUTH_RE.findAll(cleaned)) {
            val p = Totp.parseOtpAuth(m.value) ?: continue
            out.putIfAbsent(p.secret, Item(p, origin))
        }

        // 3) 兜底：整段就是一个 Base32 密钥
        if (out.isEmpty()) {
            val candidate = cleaned.trim()
            if (looksLikeSecret(candidate)) {
                val normalized = Base32.normalize(candidate)
                val platform = Platforms.of("custom")
                out[normalized] = Item(
                    Totp.Parsed(
                        platformKey = platform.key,
                        label = platform.label,
                        account = "",
                        secret = normalized,
                        digits = platform.digits,
                        period = platform.period,
                        algo = platform.algo,
                        charsetHint = "NUM"
                    ),
                    Origin.RAW_SECRET
                )
            }
        }

        return out.values.toList()
    }

    /** 是否像一串 Base32 密钥（长度足够、只用 A-Z2-7、含字母） */
    fun looksLikeSecret(value: String): Boolean {
        val v = value.replace(" ", "").replace("-", "").replace("=", "")
        if (v.length < 16 || v.length > 128) return false
        if (!v.any { it.isLetter() }) return false
        return v.all { (it in 'A'..'Z') || (it in 'a'..'z') || (it in '2'..'7') }
    }

    /** 把 HTML / JSON 里的转义还原，否则 &amp; 会把参数切断 */
    private fun unescape(text: String): String = text
        .replace("&amp;", "&")
        .replace("&#38;", "&")
        .replace("\\/", "/")
        .replace("\\u0026", "&")
        .replace("\\n", "\n")
        .replace("\\\"", "\"")

    /** 把若干 Parsed 转成 TokenEntry（id 随机、按平台补参数） */
    fun toEntry(parsed: Totp.Parsed): TokenEntry {
        val platform = Platforms.of(parsed.platformKey)
        val name = parsed.label.ifBlank { platform.label }
        return TokenEntry(
            name = name,
            account = parsed.account,
            secret = parsed.secret,
            digits = parsed.digits,
            period = parsed.period,
            algo = parsed.algo,
            charset = parsed.charsetHint,
            platformKey = platform.key
        )
    }
}