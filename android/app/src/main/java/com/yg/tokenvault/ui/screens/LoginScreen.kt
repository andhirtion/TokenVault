package com.yg.tokenvault.ui.screens

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.yg.tokenvault.data.OtpImporter
import com.yg.tokenvault.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import org.json.JSONArray

/** 各平台「两步验证 / 应用验证器」设置页的直达地址 */
data class LoginShortcut(val label: String, val url: String)

val LOGIN_SHORTCUTS = listOf(
    LoginShortcut("Google", "https://myaccount.google.com/signinoptions/twosv"),
    LoginShortcut("Microsoft", "https://account.live.com/proofs/Manage"),
    LoginShortcut("GitHub", "https://github.com/settings/security/two_factor_authentication"),
    LoginShortcut("Steam", "https://store.steampowered.com/twofactor/manage"),
    LoginShortcut("Dropbox", "https://www.dropbox.com/account/security"),
    LoginShortcut("Amazon", "https://www.amazon.com/a/settings/approval"),
    LoginShortcut("Epic", "https://www.epicgames.com/account/password"),
    LoginShortcut("Discord", "https://discord.com/settings/security"),
    LoginShortcut("Telegram", "https://web.telegram.org/k/"),
    LoginShortcut("X", "https://x.com/settings/security_and_login_information")
)

/**
 * 内置浏览器登录页：在这里真实登录账号，走到「启用验证器」那一步，
 * 页面上的 otpauth:// 链接 / Base32 密钥 / 二维码图片会被自动抓下来。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    startUrl: String?,
    onDismiss: () -> Unit,
    onFound: (List<OtpImporter.Item>) -> Unit
) {
    val pal = LocalPalette.current
    var currentUrl by remember { mutableStateOf(startUrl ?: "") }
    var loading by remember { mutableStateOf(true) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var found by remember { mutableStateOf<List<OtpImporter.Item>>(emptyList()) }
    val seen = remember { HashSet<String>() }

    fun merge(items: List<OtpImporter.Item>) {
        if (items.isEmpty()) return
        val next = found.toMutableList()
        var changed = false
        for (it in items) {
            if (seen.add(it.parsed.secret)) {
                next.add(it)
                changed = true
            }
        }
        if (changed) found = next
    }

    Box(modifier = Modifier.fillMaxSize().background(pal.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pal.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .androidClickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = pal.text2,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (loading) "正在加载…" else hostOf(currentUrl),
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (found.isEmpty()) "登录后打开「身份验证器 / 两步验证」设置页"
                        else "已发现 ${found.size} 条密钥",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (found.isEmpty()) pal.text3 else pal.success,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .androidClickable { webViewRef.value?.reload() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        tint = pal.text2,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .androidClickable {
                            webViewRef.value?.evaluateJavascript(GRAB_JS, null)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "立即抓取",
                        tint = pal.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = pal.accent,
                    trackColor = Color.Transparent
                )
            }

            // 平台快捷入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pal.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LOGIN_SHORTCUTS.forEach { s ->
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = pal.text2,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pal.surfaceHi)
                            .androidClickable { webViewRef.value?.loadUrl(s.url) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            // 网页
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(0xFF000000.toInt())
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString =
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            val cm = CookieManager.getInstance()
                            cm.setAcceptThirdPartyCookies(this, true)
                            addJavascriptInterface(Bridge { payload ->
                                val items = parsePayload(payload)
                                if (items.isNotEmpty()) merge(items)
                            }, "TokenVaultBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    loading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    url?.let { currentUrl = it }
                                    view?.evaluateJavascript(GRAB_JS, null)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    // 点「无法扫描」给的可点链接，直接拦下来当密钥
                                    if (url.startsWith("otpauth://", true) ||
                                        url.startsWith("otpauth-migration://", true)
                                    ) {
                                        val items = OtpImporter.parse(url, OtpImporter.Origin.WEB)
                                        if (items.isNotEmpty()) merge(items)
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(startUrl ?: LOGIN_SHORTCUTS.first().url)
                            webViewRef.value = this
                        }
                    }
                )
            }
        }

        // 发现结果条
        AnimatedVisibility(
            visible = found.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(pal.surfaceHi, pal.surface)
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = pal.success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已捕获 ${found.size} 条密钥",
                            style = MaterialTheme.typography.titleMedium,
                            color = pal.text
                        )
                        Text(
                            text = found.take(3).joinToString("、") {
                                it.parsed.label.ifBlank { it.parsed.platformKey }
                            } + if (found.size > 3) " 等" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text(
                    text = "全部导入（${found.size}）",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(pal.accent, pal.accent2))
                        )
                        .androidClickable {
                            onFound(found)
                            onDismiss()
                        }
                        .padding(vertical = 13.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 抓取时转圈提示
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = pal.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // 页面是 SPA / 内容异步渲染，定期重扫
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            webViewRef.value?.evaluateJavascript(GRAB_JS, null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef.value = null
        }
    }
}

private fun hostOf(url: String): String = try {
    java.net.URI(url).host ?: url
} catch (e: Exception) {
    url
}

/** 给 WebView 的 JS 桥 */
private class Bridge(private val onPayload: (String) -> Unit) {
    @JavascriptInterface
    fun post(json: String) {
        onPayload(json)
    }
}

/**
 * 把 JS 抓回来的 JSON 变成解析结果。
 * 每项要么是 { "v": "otpauth://..." }，要么是 { "img": "data:image/png;base64,..." }。
 */
private fun parsePayload(json: String): List<OtpImporter.Item> {
    val out = ArrayList<OtpImporter.Item>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("v", "")
            if (text.isNotEmpty()) {
                out.addAll(OtpImporter.parse(text, OtpImporter.Origin.WEB))
              continue
            }
            val img = o.optString("img", "")
            if (img.isNotEmpty()) {
                decodeQrDataUrl(img)?.let { decoded ->
                    out.addAll(OtpImporter.parse(decoded, OtpImporter.Origin.QR_CODE))
                }
            }
        }
    } catch (e: Throwable) {
        // 忽略抓取过程中的解析异常
    }
    return out
}

/** 页面里的二维码图片 → 位图 → ZXing 解码 */
private fun decodeQrDataUrl(dataUrl: String): String? = com.yg.tokenvault.util.QrDecoder.fromDataUrl(dataUrl)

/**
 * 注入脚本：把页面里所有可能的密钥捞出来。
 * 覆盖三条路：字面量 otpauth 链接、像 Base32 的文本、页面里的二维码图片。
 */
private const val GRAB_JS = """
(function(){
  try {
    var found = [];
    function pushText(v, src){
      if (!v) return;
      v = String(v);
      if (v.length < 8 || v.length > 4096) return;
      found.push({ v: v, src: src });
    }

    // 1) 整页 HTML 里的 otpauth 链接
    var html = document.documentElement ? document.documentElement.outerHTML : '';
    var re1 = /otpauth(?:-migration)?:\/\/[^\s"'<>\\]+/g, m1;
    while ((m1 = re1.exec(html)) !== null) pushText(m1[0], 'html');
    html = null;

    // 2) 输入框 / 代码块 / 隐藏域里的密钥
    var els = document.querySelectorAll('input,textarea,code,pre,kbd,samp,span,div,b,strong,td,li');
    var limit = Math.min(els.length, 4000);
    for (var i = 0; i < limit; i++) {
      var el = els[i];
      var v = el.value || el.textContent || '';
      if (!v) continue;
      v = String(v).trim();
      if (v.length >= 8 && v.length < 300 && v.indexOf('otpauth') >= 0) {
        var re2 = /otpauth(?:-migration)?:\/\/[^\s"'<>\\]+/g, m2;
        while ((m2 = re2.exec(v)) !== null) pushText(m2[0], 'text');
        continue;
      }
      // 形如 ABCD2345EFGH6789 的 Base32 串
      if (v.length >= 16 && v.length <= 128 && /^[A-Z2-7\s]+$/.test(v) && /[A-Z]/.test(v)) {
        pushText(v.replace(/\s+/g, ''), 'secret');
      }
    }

    // 3) 页面上的二维码图片，画到 canvas 取 dataURL 交给原生解码
    var imgs = document.querySelectorAll('img,canvas,svg image');
    var maxImg = Math.min(imgs.length, 30);
    for (var j = 0; j < maxImg; j++) {
      try {
        var el2 = imgs[j];
        var w = el2.naturalWidth || el2.width || el2.clientWidth || 0;
        var h = el2.naturalHeight || el2.height || el2.clientHeight || 0;
        if (!w || !h) {
          var r = el2.getBoundingClientRect ? el2.getBoundingClientRect() : null;
          if (r) { w = r.width; h = r.height; }
        }
        if (w < 80 || h < 80 || w > 1400 || h > 1400) continue;
        var c = document.createElement('canvas');
        c.width = Math.round(w);
        c.height = Math.round(h);
        var ctx = c.getContext('2d');
        if (!ctx) continue;
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, c.width, c.height);
        ctx.drawImage(el2, 0, 0, c.width, c.height);
        var url = c.toDataURL('image/png');
        if (url && url.length > 400) found.push({ img: url, src: 'qr' });
      } catch (e) { }
    }

    if (found.length && window.TokenVaultBridge) {
      TokenVaultBridge.post(JSON.stringify(found.slice(0, 40)));
    }
  } catch (e) { }
})();
"""