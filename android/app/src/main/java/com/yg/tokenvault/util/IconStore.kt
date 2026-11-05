package com.yg.tokenvault.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地图标与背景图仓库。
 *
 * 图标存 `filesDir/icons/e_<时间戳>.png`（256×256 居中裁方），条目只记文件名；
 * 背景存 `filesDir/background.jpg`（最长边 1600，压到 JPGE 88）。
 *
 * 全部文件都在 app 私有目录，不进备份明文、不上传，卸载即消失。
 */
object IconStore {

    private const val ICON_DIR = "icons"
    private const val ICON_SIZE = 256
    private const val BG_FILE = "background.jpg"
    private const val BG_MAX = 1600

    // ── 图标 ──────────────────────────────────────────────────

    fun iconDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, ICON_DIR).apply { if (!exists()) mkdirs() }

    fun iconFile(ctx: Context, name: String?): File? {
        if (name.isNullOrBlank()) return null
        val f = File(iconDir(ctx), name)
        return if (f.exists() && f.length() > 0) f else null
    }

    fun loadIcon(ctx: Context, name: String?): Bitmap? {
        val f = iconFile(ctx, name) ?: return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    fun deleteIcon(ctx: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { File(iconDir(ctx), name).delete() }
    }

    /** 从相册 / 文件选择器拿到的 URI 存成图标，返回文件名；失败返回 null */
    fun saveIconFromUri(ctx: Context, uri: Uri): String? {
        val bmp = decodeScaled(ctx, uri, ICON_SIZE * 2) ?: return null
        return saveIconBitmap(ctx, bmp)
    }

    fun saveIconBitmap(ctx: Context, src: Bitmap): String? = runCatching {
        val square = centerSquare(src, ICON_SIZE)
        val name = "e_${System.currentTimeMillis()}.png"
        File(iconDir(ctx), name).outputStream().use {
            square.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        if (square !== src) square.recycle()
        name
    }.getOrNull()

    /**
     * 从账号里猜域名：优先 `xxx@host` 的 host 部分，否则找第一个形如 `a.b` 的串。
     * 返回小写域名，猜不到返回 null。
     */
    fun guessHost(account: String, name: String): String? {
        val a = account.trim()
        val at = a.indexOf('@')
        if (at in 1 until a.length - 1) {
            val host = a.substring(at + 1).trim().trimEnd('.', '/')
            if (host.contains('.') && !host.contains(' ')) return host.lowercase()
        }
        val rx = Regex("([a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)+)", RegexOption.IGNORE_CASE)
        for (src in listOf(a, name)) {
            rx.find(src)?.let { m ->
                val host = m.groupValues[1].lowercase()
                // 排除纯数字版本号之类
                if (host.contains('.') && host.any { it.isLetter() }) return host
            }
        }
        return null
    }

    /**
     * 把用户手填的「域名 / 网址」归一化成 host。
     * 支持 `github.com`、`https://github.com/login?x=1`、`github.com:443`、`me@mail.qq.com`。
     * 认不出是域名就返回 null。
     */
    fun parseHost(raw: String?): String? {
        var s = (raw ?: return null).trim()
        if (s.isEmpty()) return null
        s = s.substringBefore('#').substringBefore('?')
        s = s.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        s = s.substringBefore('/')
        s = s.substringAfterLast('@')
        s = s.substringBefore(':')
        s = s.trim().trimEnd('.', ',', '，', '。').lowercase()
        s = s.removePrefix("www.")
        if (s.isEmpty() || s.contains(' ') || s.length > 120) return null
        val isIp = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(s)
        if (!isIp && !s.contains('.')) return null
        if (s.none { it.isLetterOrDigit() }) return null
        return s
    }

    /** 抓取结果：成功给文件名；失败给试了几个源、最后卡在哪 */
    data class IconFetch(val saved: String?, val tried: Int, val detail: String)

    /**
     * 联网抓站点图标：先站点根 favicon.ico，再 DuckDuckGo、Google 的图标接口。
     * 任意一个成功就落盘并返回文件名。
     */
    fun fetchIconFromHost(ctx: Context, host: String): String? =
        fetchIconDetailed(ctx, host).saved

    /** 同上，但把失败原因带出来，便于界面上说清楚为什么没抓到 */
    fun fetchIconDetailed(ctx: Context, rawHost: String): IconFetch {
        val h = parseHost(rawHost) ?: return IconFetch(null, 0, "域名格式不对")
        val sources = listOf(
            "https://$h/favicon.ico",
            "https://icons.duckduckgo.com/ip3/$h.ico",
            "https://www.google.com/s2/favicons?domain=$h&sz=128"
        )
        var tried = 0
        var last = "没有响应"
        for (u in sources) {
            tried++
            val (bmp, err) = httpBitmapResult(u)
            if (bmp == null) {
                last = err ?: "没有响应"
                continue
            }
            if (bmp.width < 16 || bmp.height < 16) {
                bmp.recycle()
                last = "图标太小（${bmp.width}px）"
                continue
            }
            val name = saveIconBitmap(ctx, bmp)
            bmp.recycle()
            if (name != null) return IconFetch(name, tried, "已抓取 $h")
            last = "存盘失败"
        }
        return IconFetch(null, tried, "试了 $tried 个源都没拿到（$last）")
    }

    private fun httpBitmap(url: String): Bitmap? = httpBitmapResult(url).first

    private fun httpBitmapResult(url: String): Pair<Bitmap?, String?> = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KeyCoffer")
            setRequestProperty("Accept", "image/*,*/*")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            Pair(null, "HTTP $code")
        } else {
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            if (bmp == null) Pair(null, "不是图片") else Pair(bmp, null)
        }
    } catch (e: java.net.SocketTimeoutException) {
        Pair(null, "超时")
    } catch (e: java.net.UnknownHostException) {
        Pair(null, "域名解析失败")
    } catch (e: java.net.SocketException) {
        Pair(null, "连接被重置")
    } catch (e: Exception) {
        Pair(null, e.javaClass.simpleName)
    }

    // ── 背景 ──────────────────────────────────────────────────

    fun bgFile(ctx: Context): File = File(ctx.applicationContext.filesDir, BG_FILE)

    fun hasBackground(ctx: Context): Boolean =
        bgFile(ctx).let { it.exists() && it.length() > 0 }

    fun saveBackground(ctx: Context, uri: Uri): Boolean {
        val bmp = decodeScaled(ctx, uri, BG_MAX) ?: return false
        val ok = runCatching {
            bgFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }.isSuccess
        bmp.recycle()
        return ok
    }

    fun clearBackground(ctx: Context) {
        runCatching { bgFile(ctx).delete() }
    }

    /** 按屏幕尺寸采样解码，避免背景图把内存撑爆 */
    fun loadBackground(ctx: Context, reqW: Int = 1200, reqH: Int = 2600): Bitmap? {
        val f = bgFile(ctx)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        if (reqW > 0 && reqH > 0) {
            while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
                sample *= 2
            }
        }
        return runCatching {
            BitmapFactory.decodeFile(
                f.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull()
    }

    // ── 通用 ──────────────────────────────────────────────────

    /** 按上限做两段式解码（先读尺寸算采样比），避免大图 OOM */
    private fun decodeScaled(ctx: Context, uri: Uri, max: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / (sample * 2) >= max && h / (sample * 2) >= max) sample *= 2
        return runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    /** 居中裁成正方形并缩放到 size */
    fun centerSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = if (side == src.width && side == src.height) src
        else Bitmap.createBitmap(src, x, y, side, side)
        if (cropped.width == size && cropped.height == size) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (scaled !== cropped && cropped !== src) cropped.recycle()
        return scaled
    }
}