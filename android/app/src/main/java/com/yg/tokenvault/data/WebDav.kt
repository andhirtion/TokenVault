package com.yg.tokenvault.data

import android.content.Context
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
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw)
    }

    private fun open(url: String, cfg: DavCfg, method: String): HttpURLConnection {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", authHeader(cfg))
            setRequestProperty("User-Agent", UA)
        }
        applyMethod(c, method)
        return c
    }

    /** HttpURLConnection 只认这几个方法，其余（PROPFIND / MKCOL）它直接抛 ProtocolException */
    private val STANDARD_METHODS = setOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE")

    /**
     * 把方法名落到连接上。
     *
     * `HttpURLConnection.setRequestMethod` 有一份白名单，PROPFIND / MKCOL 会被它拒掉；
     * 但真正发请求时读的是实现类里那个 `method` 字符串字段 —— 改字段就能发出去。
     * Android 的实现把方法名放在父类 `java.net.HttpURLConnection` 上，
     * JDK 的 sun 实现放在自己的私有字段上，所以沿类层次逐个试。
     * 全失效时退回 POST —— 至少不会崩，调用方按状态码给提示。
     */
    private fun applyMethod(c: HttpURLConnection, method: String) {
        if (method in STANDARD_METHODS) {
            c.requestMethod = method
            return
        }
        var cls: Class<*>? = c.javaClass
        while (cls != null) {
            val hit = runCatching {
                val f = cls.getDeclaredField("method")
                if (f.type == String::class.java) {
                    f.isAccessible = true
                    f.set(c, method)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (hit && runCatching { c.requestMethod == method }.getOrDefault(false)) return
            cls = cls.superclass
        }
        c.requestMethod = "POST"
    }

    /**
     * 连通性测试：对根目录发 OPTIONS。
     *
     * 不用 PROPFIND 测：它不属于 HttpURLConnection 的标准方法白名单，
     * 在部分系统上会直接抛 `ProtocolException: Expected one of [OPTIONS, GET, …] but was PROPFIND`。
     * OPTIONS 是标准方法，WebDAV 服务器一定会答，并且带 `DAV:` 与 `Allow:` 头。
     */
    fun test(cfg: DavCfg): Out {
        val b = base(cfg)
        if (b.isEmpty()) return Out.Err("先填服务器地址")
        return try {
            val c = open(b, cfg, "OPTIONS")
            val code = c.responseCode
            val dav = c.getHeaderField("DAV")
            val allow = c.getHeaderField("Allow")
            c.disconnect()
            when (code) {
                in 200..299 -> {
                    val tail = buildString {
                        if (!dav.isNullOrBlank()) append(" · 支持 WebDAV $dav")
                        if (allow != null && allow.contains("PROPFIND", true) && dav.isNullOrBlank()) {
                            append(" · 支持 WebDAV")
                        }
                    }
                    Out.Ok("连接成功（HTTP $code$tail）")
                }
                401 -> Out.Err("账号或应用密码不对（401）")
                403 -> Out.Err("没有权限（403），检查应用密码是否只读")
                404 -> Out.Err("路径不存在（404），服务器地址通常以 /dav/ 结尾")
                405 -> Out.Err("这层路径不接受 WebDAV（405），地址可能需要以 /dav/ 结尾")
                else -> Out.Err("服务器返回 HTTP $code")
            }
        } catch (e: Exception) {
            Out.Err(netErr(e))
        }
    }

    /** 上传：先尽力逐级建目录，再 PUT 覆盖；服务器不让建目录就退回根目录 */
    fun upload(cfg: DavCfg, json: String): Out {
        if (base(cfg).isEmpty()) return Out.Err("先填服务器地址")
        val dirErr = ensureDirs(cfg)
        val body = json.toByteArray(Charsets.UTF_8)
        val size = "%.1f".format(body.size / 1024.0)
        return try {
            val code = put(target(cfg), cfg, body)
            when {
                code in 200..299 -> Out.Ok("已上传 $size KB")
                code == 401 -> Out.Err("账号或应用密码不对（401）")
                code == 403 -> Out.Err("没有写入权限（403），应用密码可能是只读的")
                code == 507 -> Out.Err("网盘空间不足（507）")
                code == 409 -> {
                    // 目录建不出来（服务器禁 MKCOL / 反射被挡）：把文件放根目录，能用优先
                    val flat = cfg.copy(path = flatPath(cfg))
                    val code2 = put(target(flat), cfg, body)
                    if (code2 in 200..299) {
                        Out.Ok("已上传 $size KB（服务器不允许建目录，已存到根目录 ${flatPath(cfg)}）")
                    } else {
                        Out.Err(dirHint(cfg, dirErr))
                    }
                }
                else -> Out.Err(
                    "上传失败（HTTP $code）" + if (dirErr != null) " · $dirErr" else ""
                )
            }
        } catch (e: Exception) {
            Out.Err(netErr(e))
        }
    }

    private fun put(url: String, cfg: DavCfg, body: ByteArray): Int {
        val c = open(url, cfg, "PUT").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        c.disconnect()
        return code
    }

    /** 远端路径的最后一段，用来在不能建目录时存到根目录 */
    private fun flatPath(cfg: DavCfg): String =
        cfg.path.trim().trimStart('/').substringAfterLast('/').ifBlank { "backup.json" }

    /** 409 时的可操作提示：把「建目录」换成用户自己能做的事 */
    private fun dirHint(cfg: DavCfg, dirErr: String?): String {
        val path = cfg.path.trim().trimStart('/')
        val dir = if (path.contains('/')) path.substringBeforeLast('/') else ""
        if (dir.isEmpty()) return "根目录不可写（409）"
        val extra = if (dirErr != null) " · 自动建目录失败：$dirErr" else ""
        return "目录 $dir 不存在且建不出来（409）。把远端路径改成 backup.json 直接放根目录，" +
            "或先在网盘里手动建好 $dir 文件夹$extra"
    }

    /** 下载：返回备份 JSON 文本；配置里的路径没有就试根目录同名文件 */
    fun download(cfg: DavCfg): Out {
        if (base(cfg).isEmpty()) return Out.Err("先填服务器地址")
        return try {
            var out = readOne(target(cfg), cfg)
            if (out == null) {
                val flat = flatPath(cfg)
                if (cfg.path.trim().trimStart('/') != flat) out = readOne(target(cfg.copy(path = flat)), cfg)
            }
            out ?: Out.Err("云端还没有备份文件")
        } catch (e: Exception) {
            Out.Err(netErr(e))
        }
    }

    /** 读一条；404 返回 null（让调用方去试根目录），其它错误直接给出 Out.Err */
    private fun readOne(url: String, cfg: DavCfg): Out? {
        val c = open(url, cfg, "GET")
        val code = c.responseCode
        val out: Out? = when {
            code in 200..299 -> Out.Ok(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            code == 404 -> null
            code == 401 -> Out.Err("账号或应用密码不对（401）")
            code == 403 -> Out.Err("没有读取权限（403）")
            else -> Out.Err("下载失败（HTTP $code）")
        }
        c.disconnect()
        return out
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
            // 201 新建成功；405 已存在；301/302 已存在且带斜杠；409 父级还没建好（下一步会再试）
            if (code in listOf(200, 201, 204, 301, 302, 405, 409)) continue
            if (code == 401) return "账号或应用密码不对（401）"
            if (code == 403) return "没有建目录的权限（403）"
            if (code in listOf(400, 501)) return "服务器不支持自动建目录（HTTP $code）"
            return "创建目录失败（HTTP $code）"
        }
        return null
    }

    private fun netErr(e: Exception): String = when {
        e is java.net.SocketTimeoutException -> "连接超时，检查网络或服务器地址"
        e is java.net.UnknownHostException -> "域名解析失败，检查服务器地址"
        e is javax.net.ssl.SSLException -> "证书校验失败（自签证书需换 https 正规域名）"
        e is java.net.ConnectException -> "连不上服务器，检查地址与端口"
        e is java.net.ProtocolException -> "这个服务器不接受该请求方法（${e.message ?: "ProtocolException"}）"
        else -> "网络错误：${e.javaClass.simpleName} ${e.message ?: ""}".trim()
    }
}