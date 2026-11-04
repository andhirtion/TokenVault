package com.yg.tokenvault.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 关于页的「检查更新 / 应用内更新」。
 *
 * 版本清单布置了多个来源，逐个试、谁先返回用谁（国内直连 GitHub 常失败，所以 Pages / jsDelivr /
 * raw / 加速镜像都排上，最后才落到 GitHub API）。安装包同样按镜像列表逐个试下载。
 */
data class UpdateInfo(
    val version: String,
    val code: Int,
    val notes: List<String>,
    val releaseUrl: String,
    val apkName: String,
    val apkSize: Long,
    val apkSha256: String,
    val apkUrls: List<String>,
    val source: String
)

sealed interface CheckResult {
    data class Newer(val info: UpdateInfo) : CheckResult
    data class Latest(val current: String, val source: String) : CheckResult
    data class Failed(val message: String) : CheckResult
}

object Updater {

    const val REPO_SLUG = "andhirtion/TokenVault"
    const val REPO_URL = "https://github.com/$REPO_SLUG"
    const val PAGES_URL = "https://andhirtion.github.io/TokenVault/"

    /** GitHub 下载加速前缀，第一个是直连 */
    private val MIRRORS = listOf(
        "",
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.net/"
    )

    internal fun verSources(stamp: Long): List<Pair<String, String>> = listOf(
        "Pages" to "${PAGES_URL}version.json?v=$stamp",
        "jsDelivr" to "https://cdn.jsdelivr.net/gh/$REPO_SLUG@main/docs/version.json?v=$stamp",
        "raw" to "https://raw.githubusercontent.com/$REPO_SLUG/main/docs/version.json?v=$stamp",
        "gh-proxy" to "https://gh-proxy.com/https://raw.githubusercontent.com/$REPO_SLUG/main/docs/version.json?v=$stamp",
        "ghfast" to "https://ghfast.top/https://raw.githubusercontent.com/$REPO_SLUG/main/docs/version.json?v=$stamp"
    )

    /** 当前安装版本名，例如 "1.4.0" */
    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    fun cmpVer(a: String, b: String): Int {
        val pa = a.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
        val pb = b.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun httpGet(url: String, readBody: Boolean): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("User-Agent", "KeyCoffer-Updater")
        }
        try {
            val code = conn.responseCode
            if (!readBody || code !in 200..299) return code to ""
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseInfo(raw: String, source: String): UpdateInfo? {
        val j = try { JSONObject(raw) } catch (e: Exception) { return null }
        val version = j.optString("version").ifBlank {
            j.optString("tag_name").removePrefix("v")
        }.trim()
        if (version.isBlank()) return null

        val notes = ArrayList<String>()
        val arr = j.optJSONArray("notes")
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), 12)) {
                val s = arr.optString(i).trim().trimStart('-', '*', ' ')
                if (s.isNotEmpty()) notes.add(s)
            }
        } else {
            j.optString("body").split('\n').map { it.trim().trimStart('-', '*', ' ') }
                .filter { it.isNotEmpty() }.take(12).forEach { notes.add(it) }
        }

        val apkObj = j.optJSONObject("apk")
        var apkName = ""
        var apkSize = 0L
        var apkSha = ""
        val urls = ArrayList<String>()

        if (apkObj != null) {
            apkName = apkObj.optString("name")
            apkSize = apkObj.optLong("size", 0L)
            apkSha = apkObj.optString("sha256")
            val ua = apkObj.optJSONArray("urls")
            if (ua != null) for (i in 0 until ua.length()) {
                val u = ua.optString(i)
                if (u.isNotBlank()) urls.add(u)
            }
        } else {
            // GitHub API 形态：assets 里找 .apk
            val assets = j.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                apkName = name
                apkSize = a.optLong("size", 0L)
                val dl = a.optString("browser_download_url")
                if (dl.isNotBlank()) MIRRORS.forEach { urls.add(it + dl) }
                break
            }
        }

        return UpdateInfo(
            version = version,
            code = j.optInt("code", 0),
            notes = notes,
            releaseUrl = j.optString("release").ifBlank {
                j.optString("html_url").ifBlank { "$REPO_URL/releases" }
            },
            apkName = apkName,
            apkSize = apkSize,
            apkSha256 = apkSha,
            apkUrls = urls,
            source = source
        )
    }

    /** 拉版本清单并比较；全部来源都失败才返回 Failed */
    suspend fun check(ctx: Context): CheckResult = withContext(Dispatchers.IO) {
        val current = currentVersion(ctx)
        val stamp = System.currentTimeMillis()
        val errors = ArrayList<String>()

        for ((name, url) in verSources(stamp)) {
            try {
                val (code, body) = httpGet(url, true)
                if (code !in 200..299) { errors.add("$name $code"); continue }
                val info = parseInfo(body, name)
                if (info == null) { errors.add("$name 格式异常"); continue }
                return@withContext if (cmpVer(info.version, current) > 0) {
                    CheckResult.Newer(info)
                } else {
                    CheckResult.Latest(current, name)
                }
            } catch (e: Exception) {
                errors.add("$name ${e.javaClass.simpleName}")
            }
        }

        // 最后一招：GitHub API releases/latest
        try {
            val api = "https://api.github.com/repos/$REPO_SLUG/releases/latest"
            val (code, body) = httpGet(api, true)
            if (code in 200..299) {
                val info = parseInfo(body, "GitHub API")
                if (info != null) {
                    return@withContext if (cmpVer(info.version, current) > 0) {
                        CheckResult.Newer(info)
                    } else {
                        CheckResult.Latest(current, "GitHub API")
                    }
                }
                errors.add("GitHub API 格式异常")
            } else {
                errors.add("GitHub API $code")
            }
        } catch (e: Exception) {
            errors.add("GitHub API ${e.javaClass.simpleName}")
        }

        CheckResult.Failed("所有来源都连不上（${errors.joinToString(" / ")}）")
    }

    /** 下载目录 */
    fun downloadDir(ctx: Context): File =
        File(ctx.cacheDir, "update").apply { if (!exists()) mkdirs() }

    /**
     * 按镜像列表逐个试下载安装包。成功返回本地文件。
     * @param onProgress 已下载字节 / 总字节（总字节未知时为 -1）
     */
    suspend fun download(
        ctx: Context,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (info.apkUrls.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("这个版本没有可下载的安装包"))
        }
        val name = info.apkName.ifBlank { "KeyCoffer-${info.version}.apk" }
        val dir = downloadDir(ctx)
        dir.listFiles()?.forEach { runCatching { it.delete() } }

        val errors = ArrayList<String>()
        for (url in info.apkUrls) {
            val tmp = File(dir, name + ".part")
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 9000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "KeyCoffer-Updater")
                }
                val code = conn.responseCode
                if (code !in 200..299) { conn.disconnect(); errors.add("$code"); continue }
                val total = conn.contentLengthLong
                var read = 0L
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                        out.flush()
                    }
                }
                conn.disconnect()

                // 大小校验：清单里有期望值就比一下，防止镜像返回半截或错误页
                if (info.apkSize > 0 && read != info.apkSize) {
                    errors.add("大小不符 $read/${info.apkSize}")
                    runCatching { tmp.delete() }
                    continue
                }
                if (read < 512 * 1024) {   // 小于 512KB 基本是错误页
                    errors.add("文件过小 $read")
                    runCatching { tmp.delete() }
                    continue
                }
                val dst = File(dir, name)
                if (dst.exists()) dst.delete()
                if (!tmp.renameTo(dst)) {
                    tmp.copyTo(dst, overwrite = true)
                    runCatching { tmp.delete() }
                }
                return@withContext Result.success(dst)
            } catch (e: Exception) {
                errors.add(e.javaClass.simpleName)
                runCatching { tmp.delete() }
            }
        }
        Result.failure(IllegalStateException("所有下载源都失败（${errors.joinToString(" / ")}）"))
    }

    /** 是否已允许安装未知来源应用（Android 8+） */
    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else true

    /** 跳到系统设置页，让用户给本应用开「安装未知应用」 */
    fun openInstallPermissionSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 拉起系统安装器 */
    fun install(ctx: Context, apk: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}