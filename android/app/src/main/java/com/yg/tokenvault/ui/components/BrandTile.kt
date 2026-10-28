package com.yg.tokenvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yg.tokenvault.data.Platform

/**
 * 品牌方块：品牌色调的圆角底 + 单色图标。
 * 比直接贴彩色 logo 在深色界面上更整齐，也更接近现代认证器的观感。
 */
@Composable
fun BrandTile(
    platform: Platform,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val base = Color(platform.tileColor)
    val fg = Color(platform.fgColor)
    val darken = if (base.luminance() > 0.5f) 0.05f else 0.22f
    val brush = Brush.linearGradient(
        listOf(base, base.blend(Color.Black, darken))
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.29f))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(platform.icon),
            contentDescription = platform.label,
            tint = fg,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

/** 简单的线性混色，避免引入额外依赖 */
private fun Color.blend(other: Color, ratio: Float): Color = Color(
    red = red + (other.red - red) * ratio,
    green = green + (other.green - green) * ratio,
    blue = blue + (other.blue - blue) * ratio,
    alpha = alpha + (other.alpha - alpha) * ratio
)