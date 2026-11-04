package com.yg.tokenvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.ui.components.AppLogo
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * 主界面（启动页）：四张卡片，选择后才进具体页面。
 *
 * 这样做的好处是把重活（列表渲染、实时动态码）挪到真正需要的时候，
 * 打开应用第一屏永远是最轻的。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Key,
                tint = pal.accent,
                title = "令牌",
                sub = if (codes == 0) "动态码" else "$codes 个动态码",
                onClick = onCode
            )
            HomeCard(
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Build,
                tint = pal.warning,
                title = "工具箱",
                sub = "导入导出 · 扫码",
                onClick = onTools
            )
            HomeCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Settings,
                tint = pal.text2,
                title = "设置",
                sub = "主题 · 指纹 · 云备份",
                onClick = onSettings
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeTag("AES-256 本地加密")
            HomeTag("指纹 / 面容")
            HomeTag("WebDAV 同步")
        }

        Spacer(Modifier.height(28.dp))
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
            .clip(RoundedCornerShape(22.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(22.dp))
            .androidClickable(onClick = onClick)
            .padding(16.dp)
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
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text = text, fontSize = 10.5.sp, color = pal.text3)
    }
}