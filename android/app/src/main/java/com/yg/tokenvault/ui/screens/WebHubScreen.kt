package com.yg.tokenvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.data.Platform
import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.ui.components.BrandTile
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * 「网页登录」的主页。
 *
 * 顶栏那个地球按钮不再直接开浏览器，而是先落到这里：
 * 选一个站点（或自己填网址）→ 才进入 [LoginScreen] 真实登录并抓取密钥。
 */
@Composable
fun WebHubScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val pal = LocalPalette.current
    var custom by remember { mutableStateOf("") }

    fun openCustom() {
        var u = custom.trim()
        if (u.isBlank()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        onOpen(u)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.bg)
            .statusBarsPadding()
    ) {
        // ── 顶栏 ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.surfaceHi)
                    .androidClickable(onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = pal.text2,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "网页登录",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.text
                )
                Text(
                    text = "选一个站点，登录后自动抓取密钥",
                    fontSize = 11.5.sp,
                    color = pal.text3
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // ── 说明 ──────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(pal.accent.copy(alpha = 0.10f))
                    .padding(14.dp)
            ) {
                Text(
                    text = "怎么用",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.accent
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "打开站点 → 登录你的账号 → 进到「两步验证 / 身份验证器」设置页。" +
                        "页面上出现的 otpauth 链接、密钥串或二维码都会被自动识别，返回后勾选导入即可。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = pal.text2
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── 站点网格 ──────────────────────
            Text(
                text = "常用站点",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = pal.text3
            )
            Spacer(Modifier.height(8.dp))

            LOGIN_SHORTCUTS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { sc ->
                        SiteCard(
                            shortcut = sc,
                            modifier = Modifier.weight(1f)
                        ) { onOpen(sc.url) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(10.dp))

            // ── 自定义网址 ────────────────────
            Text(
                text = "其它站点",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = pal.text3
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("例如 account.proton.me", fontSize = 12.5.sp, color = pal.text3)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    color = pal.text
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = pal.accent,
                    unfocusedBorderColor = pal.border,
                    focusedContainerColor = pal.surfaceHi,
                    unfocusedContainerColor = pal.surfaceHi,
                    cursorColor = pal.accent
                )
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (custom.isBlank()) {
                            Brush.horizontalGradient(listOf(pal.surfaceHi, pal.surfaceHi))
                        } else {
                            Brush.horizontalGradient(listOf(pal.accent, pal.accent2))
                        }
                    )
                    .androidClickable { if (custom.isNotBlank()) openCustom() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "打开这个网址",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (custom.isBlank()) pal.text3 else Color.White
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "登录在你手机上的内置浏览器里进行，账号密码不会经过 App 的处理逻辑；" +
                    "抓到的只有密钥本身，存进本机加密库。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = pal.text3
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SiteCard(
    shortcut: LoginShortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    val brand: Platform? = brandOf(shortcut.label)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(15.dp))
            .androidClickable(onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (brand != null) {
            BrandTile(platform = brand, size = 32.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.surfaceHi),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.label.take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.text2
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hostOf(shortcut.url),
                fontSize = 10.sp,
                color = pal.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = pal.text3,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 只有内置的四个平台有品牌图标，其余用首字母 */
private fun brandOf(label: String): Platform? = when (label.lowercase()) {
    "google" -> Platforms.of(Platforms.KEY_GOOGLE)
    "microsoft" -> Platforms.of(Platforms.KEY_MICROSOFT)
    "github" -> Platforms.of(Platforms.KEY_GITHUB)
    "steam" -> Platforms.of(Platforms.KEY_STEAM)
    else -> null
}

private fun hostOf(url: String): String =
    url.removePrefix("https://").removePrefix("http://").substringBefore('/')
