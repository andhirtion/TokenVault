package com.yg.tokenvault

import com.yg.tokenvault.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新模块的纯逻辑测试：版本号比较、version.json 解析、镜像源拼装。
 * 不联网、不碰 Android 框架（org.json 由 testImplementation 引入真实实现）。
 */
class UpdaterTest {

    // ────────────────────────── cmpVer ──────────────────────────

    @Test
    fun cmpVer_newerIsGreater() {
        assertEquals(-1, Updater.cmpVer("1.4.0", "1.4.1"))
        assertEquals(1, Updater.cmpVer("1.4.1", "1.4.0"))
        assertEquals(0, Updater.cmpVer("1.4.0", "1.4.0"))
    }

    @Test
    fun cmpVer_numericNotLexical() {
        // 字符串比较会把 "1.10" 排在 "1.9" 前面，数值比较不会
        assertEquals(1, Updater.cmpVer("1.10.0", "1.9.9"))
        assertEquals(-1, Updater.cmpVer("1.9.9", "1.10.0"))
        assertEquals(1, Updater.cmpVer("10.0.0", "9.99.99"))
    }

    @Test
    fun cmpVer_stripsPrefixAndSuffix() {
        assertEquals(0, Updater.cmpVer("v1.4.0", "1.4.0"))
        assertEquals(0, Updater.cmpVer("V1.4.0", "1.4.0"))
        assertEquals(0, Updater.cmpVer(" 1.4.0 ", "1.4.0"))
        assertEquals(0, Updater.cmpVer("2.0.0-beta", "2.0.0"))
        assertEquals(0, Updater.cmpVer("2.0.0+build7", "2.0.0"))
    }

    @Test
    fun cmpVer_missingSegmentCountsAsZero() {
        assertEquals(0, Updater.cmpVer("1.4", "1.4.0"))
        assertEquals(1, Updater.cmpVer("1.4.1", "1.4"))
        assertEquals(-1, Updater.cmpVer("1", "1.0.1"))
    }

    @Test
    fun cmpVer_blankIsZero() {
        assertEquals(0, Updater.cmpVer("", "0"))
        assertEquals(0, Updater.cmpVer("0", ""))
        assertEquals(1, Updater.cmpVer("0.0.1", ""))
    }

    // ────────────────────────── parseInfo：version.json 形态 ──────────────────────────

    private val versionJson = """
    {
      "app": "TokenVault",
      "name": "钥匣 KeyCoffer",
      "version": "1.4.1",
      "code": 9,
      "notes": ["第一条说明", "- 第二条带前缀", "  ", "第三条"],
      "repo": "https://github.com/andhirtion/TokenVault",
      "web": "https://andhirtion.github.io/TokenVault/",
      "release": "https://github.com/andhirtion/TokenVault/releases/tag/v1.4.1",
      "apk": {
        "name": "TokenVault-1.4.1.apk",
        "size": 2500000,
        "sha256": "abcdef0123456789",
        "urls": [
          "https://github.com/andhirtion/TokenVault/releases/download/v1.4.1/TokenVault-1.4.1.apk",
          "https://gh-proxy.com/https://github.com/andhirtion/TokenVault/releases/download/v1.4.1/TokenVault-1.4.1.apk"
        ]
      }
    }
    """.trimIndent()

    @Test
    fun parseInfo_versionJsonShape() {
        val info = Updater.parseInfo(versionJson, "Pages")
        assertNotNull(info)
        info!!
        assertEquals("1.4.1", info.version)
        assertEquals(9, info.code)
        assertEquals("TokenVault-1.4.1.apk", info.apkName)
        assertEquals(2500000L, info.apkSize)
        assertEquals("abcdef0123456789", info.apkSha256)
        assertEquals(2, info.apkUrls.size)
        assertEquals("Pages", info.source)
        assertEquals(
            "https://github.com/andhirtion/TokenVault/releases/tag/v1.4.1",
            info.releaseUrl
        )
    }

    @Test
    fun parseInfo_notesTrimmedAndBlankDropped() {
        val info = Updater.parseInfo(versionJson, "Pages")!!
        // 4 个元素里 "  " 被丢弃，"- 第二条带前缀" 去掉前缀
        assertEquals(listOf("第一条说明", "第二条带前缀", "第三条"), info.notes)
    }

    @Test
    fun parseInfo_notesCappedAtTwelve() {
        val arr = (1..20).joinToString(",") { "\"n$it\"" }
        val info = Updater.parseInfo("""{"version":"1.0.0","notes":[$arr]}""", "raw")!!
        assertEquals(12, info.notes.size)
        assertEquals("n12", info.notes.last())
    }

    // ────────────────────────── parseInfo：GitHub API 形态 ──────────────────────────

    private val apiJson = """
    {
      "tag_name": "v1.5.0",
      "name": "KeyCoffer v1.5.0",
      "html_url": "https://github.com/andhirtion/TokenVault/releases/tag/v1.5.0",
      "body": "## 更新\n- 支持新平台\n- 修了几个 bug",
      "assets": [
        {"name": "notes.txt", "size": 100, "browser_download_url": "https://example.com/notes.txt"},
        {"name": "TokenVault-1.5.0.apk", "size": 2600000,
         "browser_download_url": "https://github.com/andhirtion/TokenVault/releases/download/v1.5.0/TokenVault-1.5.0.apk"}
      ]
    }
    """.trimIndent()

    @Test
    fun parseInfo_githubApiShape() {
        val info = Updater.parseInfo(apiJson, "GitHub API")
        assertNotNull(info)
        info!!
        assertEquals("1.5.0", info.version)              // tag_name 去掉 v
        assertEquals("TokenVault-1.5.0.apk", info.apkName) // 跳过非 apk 资产
        assertEquals(2600000L, info.apkSize)
        assertEquals(
            "https://github.com/andhirtion/TokenVault/releases/tag/v1.5.0",
            info.releaseUrl
        )
    }

    @Test
    fun parseInfo_githubApiMirrorUrls() {
        val info = Updater.parseInfo(apiJson, "GitHub API")!!
        // 直连 + 3 个加速前缀 = 4 条
        assertEquals(4, info.apkUrls.size)
        assertTrue(info.apkUrls[0].startsWith("https://github.com/"))
        assertTrue(info.apkUrls[1].startsWith("https://gh-proxy.com/https://github.com/"))
        assertTrue(info.apkUrls.all { it.endsWith("/TokenVault-1.5.0.apk") })
    }

    @Test
    fun parseInfo_githubApiBodyAsNotes() {
        val info = Updater.parseInfo(apiJson, "GitHub API")!!
        // body 里的空行与 "## 更新" 也会进来，但 "##" 不是 - * 前缀所以保留
        assertTrue(info.notes.contains("支持新平台"))
        assertTrue(info.notes.contains("修了几个 bug"))
        assertTrue(info.notes.size <= 12)
    }

    // ────────────────────────── parseInfo：异常输入 ──────────────────────────

    @Test
    fun parseInfo_rejectsGarbage() {
        assertNull(Updater.parseInfo("", "raw"))
        assertNull(Updater.parseInfo("not json at all", "raw"))
        assertNull(Updater.parseInfo("<html>404</html>", "raw"))
    }

    @Test
    fun parseInfo_rejectsMissingVersion() {
        assertNull(Updater.parseInfo("""{"app":"TokenVault"}""", "raw"))
        assertNull(Updater.parseInfo("""{"version":""}""", "raw"))
        assertNull(Updater.parseInfo("""{"tag_name":"v"}""", "raw"))
    }

    @Test
    fun parseInfo_withoutApkStillValid() {
        // 只有版本号、没有 apk 对象时应能解析（只提示新版本，不给下载）
        val info = Updater.parseInfo("""{"version":"1.9.0"}""", "Pages")
        assertNotNull(info)
        assertEquals("1.9.0", info!!.version)
        assertEquals(0, info.apkUrls.size)
        assertEquals("https://github.com/andhirtion/TokenVault/releases", info.releaseUrl)
    }

    // ────────────────────────── 版本源 ──────────────────────────

    @Test
    fun verSources_fiveOrderedWithStamp() {
        val list = Updater.verSources(1234L)
        assertEquals(5, list.size)
        assertEquals("Pages", list[0].first)
        assertEquals(
            "https://andhirtion.github.io/TokenVault/version.json?v=1234",
            list[0].second
        )
        assertEquals(listOf("Pages", "jsDelivr", "raw", "gh-proxy", "ghfast"), list.map { it.first })
        assertTrue(list.all { it.second.endsWith("version.json?v=1234") })
    }

    @Test
    fun verSources_mirrorsWrapRawGithub() {
        val list = Updater.verSources(9L)
        assertTrue(list[2].second.startsWith("https://raw.githubusercontent.com/andhirtion/TokenVault/main/docs/"))
        assertTrue(list[3].second.startsWith("https://gh-proxy.com/https://raw.githubusercontent.com/"))
        assertTrue(list[4].second.startsWith("https://ghfast.top/https://raw.githubusercontent.com/"))
    }

    // ────────────────────────── 常量 ──────────────────────────

    @Test
    fun constantsPointToThisRepo() {
        assertEquals("andhirtion/TokenVault", Updater.REPO_SLUG)
        assertEquals("https://github.com/andhirtion/TokenVault", Updater.REPO_URL)
        assertEquals("https://andhirtion.github.io/TokenVault/", Updater.PAGES_URL)
    }
}