package com.yg.tokenvault

import com.yg.tokenvault.data.OtpImporter
import com.yg.tokenvault.data.OtpMigration
import com.yg.tokenvault.util.Base32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「自动获取密钥」链路的解析单测。
 *
 * 覆盖：
 *  - otpauth-migration:// 的 protobuf 解析（Google Authenticator 导出串）
 *  - 混合文本里同时识别 otpauth / migration / 裸 Base32
 *  - 按 secret 去重、垃圾文本拒绝
 */
class OtpImportTest {

    // ── 极简 protobuf 编码器（只为构造测试数据）────────────

    private fun varint(v: Long): ByteArray {
        var x = v
        val out = ArrayList<Byte>()
        while (true) {
            val b = (x and 0x7F).toInt()
            x = x ushr 7
            if (x == 0L) {
                out.add(b.toByte())
                break
            }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }

    private fun fieldBytes(field: Int, data: ByteArray): ByteArray =
        varint(((field shl 3) or 2).toLong()) + varint(data.size.toLong()) + data

    private fun fieldVarint(field: Int, v: Long): ByteArray =
        varint(((field shl 3) or 0).toLong()) + varint(v)

    /** 构造一条 OtpParameters */
    private fun params(
        secret: ByteArray,
        name: String = "",
        issuer: String = "",
        algorithm: Int = 1,
        digits: Int = 1,
        type: Int = 2
    ): ByteArray {
        var b = fieldBytes(1, secret)
        if (name.isNotEmpty()) b += fieldBytes(2, name.toByteArray())
        if (issuer.isNotEmpty()) b += fieldBytes(3, issuer.toByteArray())
        b += fieldVarint(4, algorithm.toLong())
        b += fieldVarint(5, digits.toLong())
        b += fieldVarint(6, type.toLong())
        return b
    }

    /** 把若干条 OtpParameters 包成 MigrationPayload 的 base64url */
    private fun migrationUri(vararg items: ByteArray): String {
        var payload = ByteArray(0)
        for (it in items) payload += fieldBytes(1, it)
        payload += fieldVarint(2, 1L) // version
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        return "otpauth-migration://offline?data=$b64"
    }

    private val secret20 = ByteArray(20) { (it * 7 + 3).toByte() }
    private val secret20b32 = Base32.encode(secret20)

    // ── otpauth-migration ──────────────────────────────────

    @Test
    fun migration_parsesTwoAccounts() {
        val uri = migrationUri(
            params(secret20, name = "me@mail.com", issuer = "GitHub"),
            params(secret20, name = "steamuser", issuer = "Steam")
        )
        val list = OtpMigration.parse(uri)
        assertEquals(2, list.size)

        assertEquals("github", list[0].platformKey)
        assertEquals("GitHub", list[0].label)
        assertEquals("me@mail.com", list[0].account)
        assertEquals(secret20b32, list[0].secret)
        assertEquals(6, list[0].digits)
        assertEquals("SHA1", list[0].algo)
        assertEquals("NUM", list[0].charsetHint)

        assertEquals("steam", list[1].platformKey)
        assertEquals(5, list[1].digits)
        assertEquals("STEAM", list[1].charsetHint)
    }

    @Test
    fun migration_readsAlgorithmAndDigits() {
        val uri = migrationUri(
            params(secret20, name = "Gmail:me@mail.com", issuer = "Google", algorithm = 2, digits = 2)
        )
        val one = OtpMigration.parse(uri).single()
        assertEquals("SHA256", one.algo)
        assertEquals(8, one.digits)
        assertEquals("me@mail.com", one.account) // 前缀 label 被剥掉
        assertEquals("google", one.platformKey)
    }

    @Test
    fun migration_skipsHotp() {
        val uri = migrationUri(params(secret20, name = "legacy", issuer = "Custom", type = 1))
        assertTrue(OtpMigration.parse(uri).isEmpty())
    }

    @Test
    fun migration_toleratesPaddingAndNonMigrationInput() {
        // 带 padding 的 base64 也要能解
        var payload = fieldBytes(1, params(secret20, name = "a@b.c", issuer = "Dropbox"))
        payload += fieldVarint(2, 1L)
        val b64 = java.util.Base64.getUrlEncoder().encodeToString(payload) // 带 '='
        assertEquals(1, OtpMigration.parse("otpauth-migration://offline?data=$b64").size)

        assertTrue(OtpMigration.parse("otpauth://totp/x?secret=AAA").isEmpty())
        assertTrue(OtpMigration.parse("").isEmpty())
        assertTrue(OtpMigration.parse("otpauth-migration://offline?data=!!!").isEmpty())
    }

    // ── OtpImporter 混合识别 ───────────────────────────────

    @Test
    fun importer_mixedTextGetsAllThreeKinds() {
        val text = """
            这是从网页复制的一段：
            otpauth://totp/GitHub:me@mail.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30
            还有个导出串 ${migrationUri(params(secret20, name = "u", issuer = "Steam"))}
            裸密钥：jbsw y3dp ehpk 3pxp
        """.trimIndent()

        val items = OtpImporter.parse(text)
        // 裸密钥与 otpauth 的 secret 相同（JBSWY3DPEHPK3PXP），按 secret 去重后剩 2 条
        assertEquals(2, items.size)
        assertEquals(setOf("github", "steam"), items.map { it.parsed.platformKey }.toSet())
    }

    @Test
    fun importer_dedupesSameSecret() {
        val text = """
            otpauth://totp/A:a@b.c?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
            otpauth://totp/A:a@b.c?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
            otpauth://totp/A:a@b.c?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
        """.trimIndent()
        assertEquals(1, OtpImporter.parse(text).size)
    }

    @Test
    fun importer_handlesRawSecretOnly() {
        val items = OtpImporter.parse("JBSWY3DPEHPK3PXP")
        assertEquals(1, items.size)
        assertEquals("JBSWY3DPEHPK3PXP", items[0].parsed.secret)
        // 整段就是裸密钥时走 RAW_SECRET 兜底通道
        assertEquals(OtpImporter.Origin.RAW_SECRET, items[0].origin)
    }

    @Test
    fun importer_unescapesHtmlEntities() {
        val text = "otpauth://totp/A:a@b.c?secret=JBSWY3DPEHPK3PXP&amp;issuer=Google"
        val one = OtpImporter.parse(text).single()
        assertEquals("google", one.parsed.platformKey)
        assertNotNull(one.parsed.secret)
    }

    @Test
    fun importer_rejectsGarbage() {
        assertTrue(OtpImporter.parse("").isEmpty())
        assertTrue(OtpImporter.parse(null).isEmpty())
        assertTrue(OtpImporter.parse("你好，这是一段普通文字，没有任何密钥").isEmpty())
        assertTrue(OtpImporter.parse("12345678901234567890").isEmpty())
    }

    @Test
    fun importer_toEntryCarriesPlatformDefaults() {
        val parsed = OtpImporter.parse(migrationUri(params(secret20, name = "u", issuer = "Microsoft")))
            .single().parsed
        val entry = OtpImporter.toEntry(parsed)
        assertEquals("microsoft", entry.platformKey)
        assertEquals(secret20b32, entry.secret)
        assertEquals(6, entry.digits)
        assertEquals(30, entry.period)
        assertEquals("Microsoft", entry.name)
    }
}