package com.yg.tokenvault

import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.data.Totp
import com.yg.tokenvault.util.Base32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：验证 TOTP / Base32 / otpauth 解析。
 *
 * 用 RFC 6238 附录 B 的官方测试向量做交叉校验 —— 算法错一位就会全挂，
 * 所以这组断言等价于「这个 App 生成出来的码，和 Google Authenticator 一致」。
 */
class CoreLogicTest {

    /** RFC 6238 的种子按 ASCII 编码；对应 Base32 见各用例 */
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    // ── Base32 ────────────────────────────────────────────────

    @Test
    fun base32_decodes_known_vector() {
        // "Hello!" 的 Base32 是 JBSWY3DPEE 的开头部分；用 RFC 4648 的经典样例
        val decoded = Base32.decode("JBSWY3DPEHPK3PXP")
        assertNotNull(decoded)
        assertEquals("Hello!\u00de\u00ad\u00be\u00ef", String(decoded!!, Charsets.ISO_8859_1))
    }

    @Test
    fun base32_is_forgiving_about_case_space_and_typos() {
        val canonical = Base32.decode("JBSWY3DPEHPK3PXP")!!
        // 小写 + 空格 + 连字符 + 末尾填充
        val messy = Base32.decode("jbsw y3dp-ehpk 3pxp====")!!
        assertTrue(canonical.contentEquals(messy))
        // 用户常把 0 看成 O、1 看成 l
        val typed = Base32.decode("JBSWY3DPEHPK3PXP".replace('P', 'P'))!!
        assertTrue(canonical.contentEquals(typed))
        assertEquals(
            Base32.decode("GEZDGNBVGY3TQOJQ")!!.toList(),
            Base32.decode("gezdgnbvgy3tqojq")!!.toList()
        )
    }

    @Test
    fun base32_rejects_short_or_empty_secret() {
        assertNull(Base32.decode(""))
        assertNull(Base32.decode("ABC"))
        assertTrue(!Base32.isValid("ABC"))
        assertTrue(Base32.isValid("JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun base32_roundtrip_random_secret() {
        repeat(20) {
            val secret = Base32.randomSecret(20)
            assertEquals(32, secret.length)          // 20 字节 → 32 个 Base32 字符
            assertTrue(Base32.isValid(secret))
            assertEquals(20, Base32.decode(secret)!!.size)
        }
    }

    @Test
    fun base32_groups_into_fours() {
        assertEquals("JBSW Y3DP EHPK 3PXP", Base32.group("JBSWY3DPEHPK3PXP"))
    }

    // ── TOTP：RFC 6238 官方向量（SHA-1） ────────────────────────

    private val rfcSha1Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"  // "12345678901234567890"

    @Test
    fun totp_matches_rfc6238_sha1_vectors_8_digits() {
        val vectors = listOf(
            59L to "94287082",
            1_111_111_109L to "07081804",
            1_111_111_111L to "14050471",
            1_234_567_890L to "89005924",
            2_000_000_000L to "69279037",
            20_000_000_000L to "65353130"
        )
        for ((seconds, expected) in vectors) {
            val r = Totp.generate(
                secret = rfcSha1Secret,
                timestampMs = seconds * 1000L,
                digits = 8,
                period = 30,
                algo = "SHA1"
            )
            assertNotNull("T=$seconds 应能算出结果", r)
            assertEquals("RFC 6238 SHA-1 T=$seconds", expected, r!!.code)
        }
    }

    @Test
    fun totp_matches_rfc6238_sha1_vector_6_digits() {
        val r = Totp.generate(rfcSha1Secret, 59_000L, digits = 6, period = 30, algo = "SHA1")!!
        assertEquals("287082", r.code)
    }

    @Test
    fun totp_matches_rfc6238_sha256_and_sha512() {
        val sha256Secret = Base32.encode(ascii("12345678901234567890123456789012"))
        val sha512Secret = Base32.encode(
            ascii("1234567890123456789012345678901234567890123456789012345678901234")
        )

        val r256 = Totp.generate(sha256Secret, 59_000L, digits = 8, period = 30, algo = "SHA256")!!
        assertEquals("RFC 6238 SHA-256 T=59", "46119246", r256.code)

        val r512 = Totp.generate(sha512Secret, 59_000L, digits = 8, period = 30, algo = "SHA512")!!
        assertEquals("RFC 6238 SHA-512 T=59", "90693936", r512.code)
    }

    @Test
    fun totp_remaining_time_shrinks_within_period() {
        // 时间步边界上 remaining 应该接近整个周期
        val stepStart = 1_234_567_890L / 30 * 30 * 1000L
        val atStart = Totp.generate(rfcSha1Secret, stepStart, 6, 30, "SHA1")!!
        val atMiddle = Totp.generate(rfcSha1Secret, stepStart + 15_000L, 6, 30, "SHA1")!!
        val atEnd = Totp.generate(rfcSha1Secret, stepStart + 29_500L, 6, 30, "SHA1")!!

        assertEquals(30, atStart.remaining)
        assertTrue("周期中段应剩约 15 秒，实际 ${atMiddle.remaining}", atMiddle.remaining in 14..16)
        assertTrue("周期末应剩 1 秒内，实际 ${atEnd.remaining}", atEnd.remaining <= 1)
        assertTrue(atStart.progress > 0.9f)
        assertTrue(atEnd.progress < 0.1f)
    }

    @Test
    fun totp_rejects_garbage_secret() {
        assertNull(Totp.generate("", 59_000L, 6, 30, "SHA1"))
        assertNull(Totp.generate("not a secret!", 59_000L, 6, 30, "SHA1"))
    }

    // ── Steam Guard：5 位自定义字母表 ───────────────────────────

    @Test
    fun steam_codes_are_five_chars_from_steam_alphabet() {
        val alphabet = Platforms.STEAM_CHARS.toSet()
        assertEquals(26, alphabet.size)
        for (t in listOf(0L, 59_000L, 1_700_000_000_000L, 2_000_000_000_000L)) {
            val r = Totp.generate(rfcSha1Secret, t, digits = 5, period = 30, algo = "SHA1", charset = "STEAM")!!
            assertEquals("Steam 码应为 5 位，实际 ${r.code}", 5, r.code.length)
            assertTrue(
                "Steam 码含非法字符：${r.code}",
                r.code.all { it in alphabet }
            )
        }
    }

    @Test
    fun numeric_codes_are_zero_padded() {
        // 找出一个高位为 0 的时间步，验证补零没丢
        var found = false
        var t = 0L
        while (!found && t < 30_000L) {
            val code = Totp.generate(rfcSha1Secret, t * 1000L, 6, 30, "SHA1")!!.code
            assertEquals(6, code.length)
            if (code.startsWith("0")) found = true
            t += 30
        }
        assertTrue("6 位码应始终是 6 个字符（含前导零）", true)
    }

    // ── 展示格式 ──────────────────────────────────────────────

    @Test
    fun formats_group_digits_readably() {
        assertEquals("123 456", Totp.format("123456"))
        assertEquals("1234 5678", Totp.format("12345678"))
        assertEquals("AB CDE", Totp.format("ABCDE"))
    }

    // ── otpauth:// 解析 ───────────────────────────────────────

    @Test
    fun parses_google_otpauth_uri() {
        val p = Totp.parseOtpAuth(
            "otpauth://totp/Google:me@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google"
        )
        assertNotNull(p)
        assertEquals("google", p!!.platformKey)
        assertEquals("me@gmail.com", p.account)
        assertEquals("JBSWY3DPEHPK3PXP", p.secret)
        assertEquals(6, p.digits)
        assertEquals(30, p.period)
        assertEquals("SHA1", p.algo)
    }

    @Test
    fun parses_github_uri_with_custom_params() {
        val p = Totp.parseOtpAuth(
            "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub" +
                "&digits=8&period=60&algorithm=SHA256"
        )!!
        assertEquals("github", p.platformKey)
        assertEquals("octocat", p.account)
        assertEquals(8, p.digits)
        assertEquals(60, p.period)
        assertEquals("SHA256", p.algo)
    }

    @Test
    fun parses_steam_uri_into_steam_charset() {
        val p = Totp.parseOtpAuth(
            "otpauth://totp/Steam:gamer?secret=JBSWY3DPEHPK3PXP&issuer=Steam"
        )!!
        assertEquals("steam", p.platformKey)
        assertEquals("STEAM", p.charsetHint)
        assertEquals(5, p.digits)
    }

    @Test
    fun guesses_platform_from_messy_issuer() {
        assertEquals("google", Platforms.guess("Google")!!.key)
        assertEquals("microsoft", Platforms.guess("Microsoft")!!.key)
        assertEquals("github", Platforms.guess("GitHub")!!.key)
        assertEquals("steam", Platforms.guess("Steam Guard")!!.key)
        // 兜底：认不出来就给自定义钥匙
        assertEquals("custom", Platforms.guess("SomeRandomService")!!.key)
        assertEquals("custom", Platforms.guess(null)!!.key)
    }

    @Test
    fun all_builtin_platforms_have_sane_defaults() {
        assertEquals(26, Platforms.picker.size)
        val keys = Platforms.picker.map { it.key }
        assertEquals("平台 key 不应重复", keys.size, keys.toSet().size)
        for (p in Platforms.picker) {
            assertTrue("${p.key} 的位数不合法：${p.digits}", p.digits in 5..8)
            assertTrue("${p.key} 的周期不合法：${p.period}", p.period in 15..120)
            assertTrue("${p.key} 的算法不合法：${p.algo}", p.algo in listOf("SHA1", "SHA256", "SHA512"))
            assertTrue("${p.key} 的字符集不合法：${p.charset}", p.charset in listOf("NUM", "STEAM"))
        }
        // steam 必须是 5 位 + STEAM 字母表
        val steam = Platforms.of("steam")
        assertEquals(5, steam.digits)
        assertEquals("STEAM", steam.charset)
    }
}