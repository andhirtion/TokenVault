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

/**
 * 云端的一次备份。
 *
 * [name] 是文件名（`backup.json` 是主文件，`backup-20261105-124500.json` 是历史快照）；
 * [main] 标记主文件 —— 界面上单独一行「当前版本」。
 */
data class DavBackup(
    val name: String,
    val at: Long = 0L,
    val size: Long = 0L,
    val main: Boolean = false
)

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
    private const val K_HIST = "history"
    private const val HIST_MAX = 40

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

    // ── 历史备份（本地记账，服务器列不出来时兜底）──────

    fun history(ctx: Context): List<DavBackup> {
        val raw = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(K_HIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = o.optString("name", "")
                if (n.isBlank()) null
                else DavBackup(n, o.optLong("at", 0L), o.optLong("size", 0L), n == "backup.json")
            }
        }.getOrDefault(emptyList())
    }

    /** 记一次成功上传，返回更新后的列表（新的在前，只留最近 40 条） */
    fun addHistory(ctx: Context, item: DavBackup): List<DavBackup> {
        val merged = (listOf(item) + history(ctx).filter { it.name != item.name })
            .sortedByDescending { it.at }
            .take(HIST_MAX)
        writeHistory(ctx, merged)
        return merged
    }

    fun clearHistory(ctx: Context) = writeHistory(ctx, emptyList())

    private fun writeHistory(ctx: Context, list: List<DavBackup>) {
        val arr = org.json.JSONArray()
        list.forEach { b ->
            arr.put(
                org.json.JSONObject()
                    .put("name", b.name)
                    .put("at", b.at)
                    .put("size", b.size)
            )
        }
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(K_HIST, arr.toString())
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
        /** [savedAs] 是这次实际落盘的文件名，用来记历史 */
        data class Ok(val text: String = "", val savedAs: String = "") : Out
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
                code in 200..299 -> {
                    val primary = fileNameOf(cfg)
                    // 顺手留一版历史快照；这份失败不影响本次备份
                    val snap = snapName(primary)
                    val snapOk = runCatching {
                        put(dirUrlOf(target(cfg)) + encSeg(snap), cfg, body) in 200..299
                    }.getOrDefault(false)
                    val savedAs = if (snapOk) snap else primary
                    touchIndex(cfg, DavBackup(savedAs, System.currentTimeMillis(), body.size.toLong(), false))
                    Out.Ok("已上传 $size KB", savedAs = savedAs)
                }
                code == 401 -> Out.Err("账号或应用密码不对（401）")
                code == 403 -> Out.Err("没有写入权限（403），应用密码可能是只读的")
                code == 507 -> Out.Err("网盘空间不足（507）")
                code == 409 -> {
                    // 目录建不出来（服务器禁 MKCOL / 反射被挡）：把文件放根目录，能用优先
                    val flat = cfg.copy(path = flatPath(cfg))
                    val code2 = put(target(flat), cfg, body)
                    if (code2 in 200..299) {
                        touchIndex(
                            flat,
                            DavBackup(flatPath(cfg), System.currentTimeMillis(), body.size.toLong(), true)
                        )
                        Out.Ok(
                            "已上传 $size KB（服务器不允许建目录，已存到根目录 ${flatPath(cfg)}）",
                            savedAs = flatPath(cfg)
                        )
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

    /**
     * 列备份历史：服务器能 PROPFIND 就用远端真实列表；
     * 不能（或反射被挡、返回 405/501）就读我们上传时自己写的 `index.json`。
     */
    fun list(cfg: DavCfg): List<DavBackup> {
        if (base(cfg).isEmpty()) return emptyList()
        val byProp = runCatching { propfind(dirUrlOf(target(cfg)), cfg) }.getOrNull().orEmpty()
        if (byProp.isNotEmpty()) return byProp
        return readIndex(cfg)
    }

    /** 恢复指定名字的那一份；名字是主文件就走配置路径，其余按同目录拼 */
    fun restoreNamed(cfg: DavCfg, name: String): Out {
        if (base(cfg).isEmpty()) return Out.Err("先填服务器地址")
        val primary = fileNameOf(cfg)
        return try {
            val url = if (name == primary) target(cfg) else dirUrlOf(target(cfg)) + encSeg(name)
            readOne(url, cfg) ?: Out.Err("云端的 $name 已经不在了")
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

    private fun fileNameOf(cfg: DavCfg): String = flatPath(cfg)

    /** 历史快照名：backup.json → backup-20261105-124500.json */
    private fun snapName(primary: String): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val stem = primary.substringBeforeLast('.', primary)
        return "$stem-$stamp.json"
    }

    /** 文件 URL → 它所在目录的 URL（保留结尾斜杠） */
    private fun dirUrlOf(fileUrl: String): String =
        fileUrl.substringBeforeLast('/', "") + "/"

    /** 同目录下的清单文件名：服务器不支持 PROPFIND 时靠它列历史 */
    private const val INDEX_NAME = "index.json"

    private fun indexUrl(cfg: DavCfg): String = dirUrlOf(target(cfg)) + INDEX_NAME

    /** 读我们自己的清单（不会抛异常；拿不到就返回空表） */
    private fun readIndex(cfg: DavCfg): List<DavBackup> {
        val text = runCatching {
            val c = open(indexUrl(cfg), cfg, "GET")
            val code = c.responseCode
            val body = if (code in 200..299) {
                c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else null
            c.disconnect()
            body
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONObject(text).optJSONArray("items") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name")
                if (name.isBlank()) return@mapNotNull null
                DavBackup(
                    name = name,
                    at = o.optLong("at"),
                    size = o.optLong("size"),
                    main = o.optBoolean("main")
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 把本次上传记进清单（尽力而为，失败不影响备份本身） */
    private fun touchIndex(cfg: DavCfg, item: DavBackup) {
        runCatching {
            val merged = (listOf(item) + readIndex(cfg).filter { it.name != item.name })
                .sortedByDescending { it.at }
                .take(40)
            val arr = org.json.JSONArray()
            merged.forEach {
                arr.put(
                    org.json.JSONObject()
                        .put("name", it.name)
                        .put("at", it.at)
                        .put("size", it.size)
                        .put("main", it.main)
                )
            }
            val body = org.json.JSONObject().put("items", arr).toString().toByteArray(Charsets.UTF_8)
            put(indexUrl(cfg), cfg, body)
        }
    }

    private const val PROPFIND_BODY =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
            "<d:getlastmodified/><d:getcontentlength/>" +
            "</d:prop></d:propfind>"

    private val RE_RESP = Regex(
        "<(?:\\w+:)?response\\b.*?</(?:\\w+:)?response>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val RE_HREF = Regex("<(?:\\w+:)?href>(.*?)</(?:\\w+:)?href>", RegexOption.IGNORE_CASE)
    private val RE_LEN = Regex("<(?:\\w+:)?getcontentlength>(\\d+)", RegexOption.IGNORE_CASE)
    private val RE_MOD = Regex("<(?:\\w+:)?getlastmodified>(.*?)</(?:\\w+:)?", RegexOption.IGNORE_CASE)

    /** 列目录（Depth:1），只挑出属于本应用的文件：主文件 + 同前缀的历史快照 */
    private fun propfind(dir: String, cfg: DavCfg): List<DavBackup>? {
        val c = open(dir, cfg, "PROPFIND").apply {
            setRequestProperty("Depth", "1")
            setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            doOutput = true
        }
        c.outputStream.use { it.write(PROPFIND_BODY.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            return null
        }
        val xml = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        c.disconnect()
        return parseListing(xml, fileNameOf(cfg))
    }

    private fun parseListing(xml: String, primary: String): List<DavBackup> {
        val stem = primary.substringBeforeLast('.', primary)
        val items = RE_RESP.findAll(xml).mapNotNull { m ->
            val block = m.value
            val href = RE_HREF.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            if (href.endsWith("/")) return@mapNotNull null            // 目录自身
            val name = runCatching {
                java.net.URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
            }.getOrDefault(href.substringAfterLast('/'))
            val match = name == primary || (name.startsWith("$stem-") && name.endsWith(".json"))
            if (!match) return@mapNotNull null
            val size = RE_LEN.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val at = RE_MOD.find(block)?.groupValues?.get(1)?.let { parseDavTime(it) } ?: 0L
            DavBackup(name, at, size, name == primary)
        }.toList()
        return items.sortedWith(compareByDescending<DavBackup> { it.main }.thenByDescending { it.at })
    }

    private fun parseDavTime(raw: String): Long = runCatching {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            .parse(raw.trim())?.time ?: 0L
    }.getOrDefault(0L)

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