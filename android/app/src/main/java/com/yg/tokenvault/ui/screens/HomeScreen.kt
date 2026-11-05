package com.yg.tokenvault.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.ui.components.AppLogo
import com.yg.tokenvault.ui.components.staggerIn
import com.yg.tokenvault.ui.components.tactile
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * 主界面（启动页）：四张卡片，选择后才进具体页面。
 *
 * 打开应用第一屏永远是最轻的 —— 列表渲染、实时动态码都留在各自的页里。
 * 顶部的两团光是纯装饰，跟着主题深浅自动调浓淡。
 */
@Composable
fun HomeScreen(
    total: Int,
    codes: Int,
    logins: Int,
    onCode: () -> Unit,
    onLogin: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit
) {
    val pal = LocalPalette.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 背景光晕（极慢呼吸，制造"活着"的感觉）──────────
        val breathe = rememberInfiniteTransition(label = "homeGlow")
        val glow by breathe.animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(5200, easing = com.yg.tokenvault.ui.components.AppEase),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        pal.accent.copy(alpha = 0.20f * glow),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.16f, h * 0.06f),
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = Offset(w * 0.16f, h * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        pal.accent2.copy(alpha = 0.13f * glow),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.92f, h * 0.30f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.92f, h * 0.30f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(26.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.staggerIn(0, stepMs = 0)
            ) {
                AppLogo(size = 54.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "钥匣",
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal.text,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (total == 0) "还没有条目 · 仅存本机" else "$total 个条目 · 仅存本机",
                        fontSize = 12.5.sp,
                        color = pal.text3
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeCard(
                    modifier = Modifier.weight(1f).staggerIn(1),
                    icon = Icons.Filled.Key,
                    tint = pal.accent,
                    title = "令牌",
                    sub = if (codes == 0) "动态码" else "$codes 个动态码",
                    onClick = onCode
                )
                HomeCard(
                    modifier = Modifier.weight(1f).staggerIn(2),
                    icon = Icons.Filled.Lock,
                    tint = pal.accent2,
                    title = "账号密码",
                    sub = if (logins == 0) "账号与备注" else "$logins 条记录",
                    onClick = onLogin
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeCard(
                    modifier = Modifier.weight(1f).staggerIn(3),
                    icon = Icons.Filled.Build,
                    tint = pal.warning,
                    title = "工具箱",
                    sub = "导入导出 · 扫码",
                    onClick = onTools
                )
                HomeCard(
                    modifier = Modifier.weight(1f).staggerIn(4),
                    icon = Icons.Filled.Settings,
                    tint = pal.text2,
                    title = "设置",
                    sub = "主题 · 指纹 · 云备份",
                    onClick = onSettings
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.staggerIn(5)
            ) {
                HomeTag("AES-256 本地加密")
                HomeTag("指纹 / 面容")
                HomeTag("WebDAV 同步")
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color,
    title: String,
    sub: String,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Column(
        modifier = modifier
            .height(134.dp)
            .tactile(onClick, scaleTo = 0.965f)
            .clip(RoundedCornerShape(22.dp))
            .background(pal.surface)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = if (pal.isDark) 0.34f else 0.42f),
                        pal.border
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = pal.text3.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal.text
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = sub,
            fontSize = 11.5.sp,
            color = pal.text3
        )
    }
}

@Composable
private fun HomeTag(text: String) {
    val pal = LocalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(pal.surface.copy(alpha = 0.8f))
            .border(1.dp, pal.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text = text, fontSize = 10.5.sp, color = pal.text3)
    }
}
