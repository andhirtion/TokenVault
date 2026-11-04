package com.yg.tokenvault.data

import android.content.Context
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * WebDAV 云备份配置。
 *
 * 密码是云盘那边生成的「应用密码」（坚果云 / Nextcloud / Alist 都这种），
 * 不是网盘登录密码。它只存在 app 私有 SharedPreferences 里，不进备份、不上传。
 */
data class DavCfg(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val path: String = "KeyCoffer/backup.json",
    val auto: Boolean = false,
    val lastAt: Long = 0L,
    val lastMsg: String = ""
) {
    val configured: Boolean get() = url.isNotBlank() && user.isNotBlank()
}

/** WebDAV 配置的本地存取（app 私有目录，卸载即消失） */
object DavStore {

    private const val PREF = "tokenvault_dav_v1"
    private const val K_URL = "url"
    private const val K_USER = "user"
    private const val K_PASS = "pass"
    private const val K_PATH = "path"
    private const val K_AUTO = "auto"
    private const val K_AT = "lastAt"
    private const val K_MSG = "lastMsg"

    fun load(ctx: Context): DavCfg {
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return DavCfg(
            url = p.getString(K_URL, "") ?: "",
            user = p.getString(K_USER, "") ?: "",
            pass = p.getString(K_PASS, "") ?: "",
            path = p.getString(K_PATH, "KeyCoffer/backup.json") ?: "KeyCoffer/backup.json",
            auto = p.getBoolean(K_AUTO, false),
            lastAt = p.getLong(K_AT, 0L),
            lastMsg = p.getString(K_MSG, "") ?: ""
        )
    }

    fun save(ctx: Context, cfg: DavCfg) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(K_URL, cfg.url.trim())
            .putString(K_USER, cfg.user.trim())
            .putString(K_PASS, cfg.pass)
            .putString(K_PATH, cfg.path.trim().ifBlank { "KeyCoffer/backup.json" })
            .putBoolean(K_AUTO, cfg.auto)
            .apply()
    }

    fun mark(ctx: Context, at: Long, msg: String) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(K_AT, at)
            .putString(K_MSG, msg)
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .clear()
            .apply()
    }
}

/**
 * 极简 WebDAV 客户端：只做本应用需要的事 —— PROPFIND 测连、MKCOL 建目录、PUT / GET 传文件。
 *
 * 不带任何第三方依赖，全部走 HttpURLConnection；调用方负责放到 IO 线程。
 */
object WebDavClient {

    private const val UA = "KeyCoffer-Android"

    sealed interface Out {
        data class Ok(val text: String = "") : Out
        data class Err(val msg: String) : Out
    }

    /** 归一化服务器地址：补协议、补结尾斜杠 */
    fun base(cfg: DavCfg): String {
        var u = cfg.url.trim()
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        if (!u.endsWith("/")) u += "/"
        return u
    }

    /** 完整目标文件 URL，路径按段做百分号编码（中文目录名也能用） */
    fun target(cfg: DavCfg): String {
        val p = cfg.path.trim().trimStart('/').ifBlank { "KeyCoffer/backup.json" }
        val enc = p.split('/').joinToString("/") { seg ->
            if (seg.isEmpty()) seg else encSeg(seg)
        }
        return base(cfg) + enc
    }

    private fun encSeg(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun authHeader(cfg: DavCfg): String {
        val raw = "${cfg.user}:${cfg.pass}".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun open(url: String, cfg: DavCfg, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", authHeader(cfg))
            setRequestProperty("User-Agent", UA)
        }

    private const val PROPFIND_BODY =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<propfind xmlns=\"DAV:\"><prop><resourcetype/></prop></propfind>"

    /** 连通性测试：对根目录发 PROPFIND Depth:0 */
    fun test(cfg: DavCfg): Out {
        val b = base(cfg)
        if (b.isEmpty()) return Out.Err("先填服务器地址")
        return try {
            val c = open(b, cfg, "PROPFIND").apply {
                doOutput = true
                setRequestProperty("Depth", "0")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            }
            c.outputStream.use { it.write(PROPFIND_BODY.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            c.disconnect()
            when (code) {
                in 200..299 -> Out.Ok("连接成功（HTTP $code）")
                401 -> Out.Err("账号或应用密码不对（401）")
                403 -> Out.Err("没有权限（403），检查应用密码是否只读")
                404 -> Out.Err("路径不存在（404），服务器地址通常以 /dav/ 结尾")
                405 -> Out.Err("服务器不接受 WebDAV 请求（405）")
                else -> Out.Err("服务器返回 HTTP $code")
            }
        } catch (e: Exception) {
            Out.Err(netErr(e))
        }
    }

    /** 上传：自动逐级建目录，再 PUT 覆盖 */
    fun upload(cfg: DavCfg, json: String): Out {
        if (base(cfg).isEmpty()) return Out.Err("先填服务器地址")
        ensureDirs(cfg)?.let { return Out.Err(it) }
        return try {
            val body = json.toByteArray(Charsets.UTF_8)
            val c = open(target(cfg), cfg, "PUT").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Length", body.size.toString())
            }
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) {
                Out.Ok("已上传 ${"%.1f".format(body.size / 1024.0)} KB")
            } else {
                Out.Err("上传失败（HTTP $code）")
            }
        } catch (e: Exception) {
            Out.Err(netErr(e))
        }
    }

    /** 下载：返回备份 JSON 文本 */
    fun download(cfg: DavCfg): Out {
        if (base(cfg).isEmpty()) return Out.Err("先填服务器地址")
        return try {
            val c = open(target(cfg), cfg, "GET")
            val code = c.responseCode
            val out = if (code in 200..299) {
                val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Out.Ok(text)
            } else if (code == 404) {
                Out.Err("云端还没有备份文件")
            } else if (code == 401) {
                Out.Err("账号或应用密码不对（401）")
            } else {
                Out.Err("下载失败（HTTP $code）")
            }
            c.disconnect()
            out
        } catch (e: Exception) {
            Out.Err(netErr(e))
        }
    }

    /** 逐级 MKCOL；返回 null 表示目录都就绪 */
    private fun ensureDirs(cfg: DavCfg): String? {
        val segs = cfg.path.trim().trimStart('/').split('/').dropLast(1).filter { it.isNotBlank() }
        if (segs.isEmpty()) return null
        var acc = base(cfg)
        for (s in segs) {
            acc += encSeg(s) + "/"
            val code = try {
                val c = open(acc, cfg, "MKCOL")
                val rc = c.responseCode
                c.disconnect()
                rc
            } catch (e: Exception) {
                return netErr(e)
            }
            // 201 新建成功；405 已存在；301/302 已存在且带斜杠
            if (code !in listOf(200, 201, 204, 301, 302, 405)) {
                return "创建目录失败（HTTP $code）"
            }
        }
        return null
    }

    private fun netErr(e: Exception): String = when {
        e is java.net.SocketTimeoutException -> "连接超时，检查网络或服务器地址"
        e is java.net.UnknownHostException -> "域名解析失败，检查服务器地址"
        e is javax.net.ssl.SSLException -> "证书校验失败（自签证书需换 https 正规域名）"
        e is java.net.ConnectException -> "连不上服务器，检查地址与端口"
        else -> "网络错误：${e.javaClass.simpleName} ${e.message ?: ""}".trim()
    }
}