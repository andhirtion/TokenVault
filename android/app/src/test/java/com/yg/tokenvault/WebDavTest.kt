package com.yg.tokenvault

import com.yg.tokenvault.data.DavCfg
import com.yg.tokenvault.data.WebDavClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * WebDAV 客户端的协议单测。
 *
 * 不用真网盘，也不依赖 com.sun.net.httpserver（Android 单测的 bootclasspath 里没有它）：
 * 直接用 ServerSocket 起一个只会说 HTTP/1.1 的假 WebDAV，
 * 记录它收到的方法 / 路径 / 认证头 / body，再断言客户端的行为。
 *
 * 重点覆盖本次修的 bug：测连必须用 OPTIONS（标准方法），
 * 不能用 PROPFIND —— 后者会被 HttpURLConnection 的方法白名单直接拒掉，
 * 真机上表现为 `ProtocolException: Expected one of [OPTIONS, GET, …] but was PROPFIND`。
 */
class WebDavTest {

    // ── 假服务器 ─────────────────────────────────────────────────

    private data class Req(val method: String, val path: String, val auth: String, val body: String)
    private data class Resp(
        val code: Int,
        val body: String = "",
        val headers: Map<String, String> = emptyMap(),
    )

    private lateinit var socket: ServerSocket
    private var port = 0
    private var running = true

    private val methods = mutableListOf<String>()
    private val paths = mutableListOf<String>()
    private var lastBody = ""
    private var lastAuth = ""

    private var optCode = 200
    private var putCode = 201
    private var nested409 = false

    /** PROPFIND 返回的 multistatus 原文，用例按需替换 */
    private var propfindXml = ""

    /** 按路径存文件：主文件与历史快照各占一个键，GET 才能一对一读回来 */
    private val files = HashMap<String, String>()

    @Before
    fun start() {
        methods.clear(); paths.clear(); lastBody = ""; lastAuth = ""
        optCode = 200; putCode = 201; nested409 = false; files.clear(); propfindXml = ""
        running = true

        socket = ServerSocket(0)
        port = socket.localPort
        thread(isDaemon = true) {
            while (running) {
                val s = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                try {
                    serve(s)
                } catch (_: Exception) {
                    // 单个连接出错不影响后续用例
                } finally {
                    runCatching { s.close() }
                }
            }
        }
    }

    @After
    fun stop() {
        running = false
        runCatching { socket.close() }
    }

    private fun serve(s: Socket) {
        val input = s.getInputStream()
        val first = readLine(input) ?: return
        val parts = first.split(' ')
        val method = parts.getOrElse(0) { "" }
        val path = parts.getOrElse(1) { "/" }.substringBefore('?')

        var auth = ""
        var len = 0
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i <= 0) continue
            when (h.substring(0, i).trim().lowercase()) {
                "authorization" -> auth = h.substring(i + 1).trim()
                "content-length" -> len = h.substring(i + 1).trim().toIntOrNull() ?: 0
            }
        }

        val body = if (len > 0) {
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val n = input.read(buf, off, len - off)
                if (n < 0) break
                off += n
            }
            String(buf, 0, off, Charsets.UTF_8)
        } else {
            ""
        }

        methods += method
        paths += path
        lastAuth = auth
        if (method == "PUT") lastBody = body

        val resp: Resp = when (method) {
            "OPTIONS" -> Resp(
                optCode,
                headers = mapOf("DAV" to "1, 2", "Allow" to "OPTIONS, GET, HEAD, PUT, MKCOL"),
            )
            "MKCOL" -> Resp(201)
            "PUT" -> if (nested409 && path == "/dav/KeyCoffer/backup.json") {
                Resp(409)
            } else {
                if (putCode in 200..299) files[path] = body
                Resp(putCode)
            }
            "GET" -> files[path]?.let { Resp(200, it) } ?: Resp(404)
            "PROPFIND" -> if (propfindXml.isBlank()) Resp(405) else Resp(207, propfindXml)
            else -> Resp(405)
        }
        write(s, resp)
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (prev == 13 && b == 10) {
                sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    private fun write(s: Socket, resp: Resp) {
        val bytes = resp.body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ${resp.code} ${reason(resp.code)}\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            resp.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Connection: close\r\n\r\n")
        }
        val out = s.getOutputStream()
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        if (bytes.isNotEmpty()) out.write(bytes)
        out.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        207 -> "Multi-Status"
        507 -> "Insufficient Storage"
        else -> "Status"
    }

    private fun cfg(path: String = "KeyCoffer/backup.json") =
        DavCfg(url = "http://127.0.0.1:$port/dav/", user = "u", pass = "p", path = path)

    // ── 地址归一化 ────────────────────────────────────────────────

    @Test
    fun base_addsSchemeAndSlash() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/",
            WebDavClient.base(DavCfg(url = "dav.jianguoyun.com/dav", user = "a", pass = "b"))
        )
        assertEquals(
            "http://127.0.0.1:8778/dav/",
            WebDavClient.base(DavCfg(url = "http://127.0.0.1:8778/dav/", user = "a", pass = "b"))
        )
        assertEquals("", WebDavClient.base(DavCfg()))
    }

    @Test
    fun target_percentEncodesEachSegment() {
        val t = WebDavClient.target(
            DavCfg(url = "https://x.y/dav/", user = "a", pass = "b", path = "钥匣/备份 1.json")
        )
        assertTrue(t.startsWith("https://x.y/dav/"))
        assertTrue("空格要编码成 %20：$t", t.contains("%20"))
        assertFalse("不能留裸空格：$t", t.contains(" "))
        assertFalse("不能出现加号：$t", t.contains("+"))
    }

    // ── 测连 ─────────────────────────────────────────────────────

    @Test
    fun test_usesOptionsAndReportsDav() {
        val out = WebDavClient.test(cfg())
        assertTrue("应当成功：$out", out is WebDavClient.Out.Ok)
        val text = (out as WebDavClient.Out.Ok).text
        assertTrue("要提到状态码：$text", text.contains("200"))
        assertTrue("要认出 WebDAV：$text", text.contains("支持 WebDAV"))
        assertEquals("测连必须用 OPTIONS", "OPTIONS", methods.first())
        assertTrue("要带 Basic 认证：$lastAuth", lastAuth.startsWith("Basic "))
    }

    @Test
    fun test_401_saysCredentialWrong() {
        optCode = 401
        val out = WebDavClient.test(cfg())
        assertTrue(out is WebDavClient.Out.Err)
        assertTrue((out as WebDavClient.Out.Err).msg.contains("401"))
    }

    @Test
    fun test_404_saysPathWrong() {
        optCode = 404
        val out = WebDavClient.test(cfg())
        assertTrue(out is WebDavClient.Out.Err)
        assertTrue((out as WebDavClient.Out.Err).msg.contains("404"))
    }

    // ── 上传 ─────────────────────────────────────────────────────

    @Test
    fun upload_putsBodyToConfiguredPath() {
        val json = """{"app":"TokenVault","count":1}"""
        val out = WebDavClient.upload(cfg(), json)
        assertTrue("应当成功：$out", out is WebDavClient.Out.Ok)
        assertTrue((out as WebDavClient.Out.Ok).text.startsWith("已上传"))
        assertEquals("服务端收到的 body 要一致", json, files["/dav/KeyCoffer/backup.json"])
        // 第一个 PUT 必须是主文件（之前还会有 MKCOL）
        val firstPut = paths[methods.indexOfFirst { it == "PUT" }]
        assertEquals("主文件要写配置里的路径", "/dav/KeyCoffer/backup.json", firstPut)
        assertTrue("必须发出 PUT", methods.contains("PUT"))
        // 顺手留的历史快照：同目录、带时间戳
        assertTrue(
            "应当留一版历史快照：$paths",
            paths.any { it.startsWith("/dav/KeyCoffer/backup-") && it.endsWith(".json") }
        )
        assertEquals("主文件内容要正确落盘", json, files["/dav/KeyCoffer/backup.json"])
        // 上传后还会写一份清单（index.json），服务器不支持 PROPFIND 时靠它列历史
        assertTrue("应当写下 index.json：$paths", files.containsKey("/dav/KeyCoffer/index.json"))
    }

    @Test
    fun upload_409_fallsBackToRootAndSaysSo() {
        nested409 = true
        val out = WebDavClient.upload(cfg(), """{"count":2}""")
        assertTrue("应当成功：$out", out is WebDavClient.Out.Ok)
        val text = (out as WebDavClient.Out.Ok).text
        assertTrue("要说清存到哪了：$text", text.contains("根目录"))
        assertTrue("回退到根目录：$paths", paths.contains("/dav/backup.json"))
    }

    @Test
    fun upload_401_saysCredentialWrong() {
        putCode = 401
        val out = WebDavClient.upload(cfg(), "{}")
        assertTrue(out is WebDavClient.Out.Err)
        assertTrue((out as WebDavClient.Out.Err).msg.contains("401"))
    }

    // ── 下载 ─────────────────────────────────────────────────────

    @Test
    fun download_404_saysNoBackup() {
        val out = WebDavClient.download(cfg())
        assertTrue(out is WebDavClient.Out.Err)
        assertTrue((out as WebDavClient.Out.Err).msg.contains("云端还没有备份文件"))
    }

    @Test
    fun download_readsBackWhatWasUploaded() {
        val json = """{"app":"TokenVault","entries":[{"n":"a"}]}"""
        WebDavClient.upload(cfg(), json)
        val out = WebDavClient.download(cfg())
        assertTrue("应当成功：$out", out is WebDavClient.Out.Ok)
        assertEquals(json, (out as WebDavClient.Out.Ok).text)
    }

    @Test
    fun download_fallsBackToRootWhenConfiguredPathMissing() {
        // 文件只在根目录（服务器不让建目录的那条路）
        nested409 = true
        WebDavClient.upload(cfg(), """{"app":"TokenVault"}""")
        val out = WebDavClient.download(cfg())
        assertTrue("应当读回来：$out", out is WebDavClient.Out.Ok)
        assertEquals("""{"app":"TokenVault"}""", (out as WebDavClient.Out.Ok).text)
    }

    // ── 历史版本 ─────────────────────────────────────────────────

    @Test
    fun list_parsesPropfindMultistatus() {
        propfindXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/KeyCoffer/</d:href>
                <d:propstat><d:prop><d:getlastmodified>Wed, 05 Nov 2026 04:00:00 GMT</d:getlastmodified></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/KeyCoffer/backup.json</d:href>
                <d:propstat><d:prop>
                  <d:getlastmodified>Wed, 05 Nov 2026 04:00:00 GMT</d:getlastmodified>
                  <d:getcontentlength>384</d:getcontentlength>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/KeyCoffer/backup-20261105-110000.json</d:href>
                <d:propstat><d:prop>
                  <d:getlastmodified>Wed, 05 Nov 2026 03:00:00 GMT</d:getlastmodified>
                  <d:getcontentlength>352</d:getcontentlength>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/KeyCoffer/readme.txt</d:href>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val list = WebDavClient.list(cfg())
        assertEquals("只留主文件 + 自己的快照：$list", 2, list.size)
        assertEquals("backup.json", list.first().name)
        assertTrue("主文件排第一", list.first().main)
        assertEquals(384L, list.first().size)
        assertEquals("backup-20261105-110000.json", list.last().name)
        assertTrue("时间要解析出来", list.last().at > 0L)
    }

    @Test
    fun list_returnsEmptyWhenServerRefusesPropfind() {
        // propfindXml 为空 → 405：调用方会回落到本地记账
        assertTrue(WebDavClient.list(cfg()).isEmpty())
    }

    @Test
    fun restoreNamed_readsTheGivenSnapshot() {
        WebDavClient.upload(cfg(), """{"count":3}""")
        val snap = paths.first { it.contains("backup-") }.substringAfterLast('/')
        val out = WebDavClient.restoreNamed(cfg(), snap)
        assertTrue("应当读到快照：$out", out is WebDavClient.Out.Ok)
        assertEquals("""{"count":3}""", (out as WebDavClient.Out.Ok).text)
    }

    // ── 空配置 ───────────────────────────────────────────────────

    @Test
    fun blankUrl_isRejectedBeforeAnyRequest() {
        val out = WebDavClient.test(DavCfg())
        assertTrue(out is WebDavClient.Out.Err)
        assertTrue((out as WebDavClient.Out.Err).msg.contains("服务器地址"))
        assertTrue("不该发出任何请求", methods.isEmpty())
    }
}
