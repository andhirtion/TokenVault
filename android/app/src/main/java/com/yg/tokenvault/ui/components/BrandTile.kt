package com.yg.tokenvault.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yg.tokenvault.data.Platform
import com.yg.tokenvault.util.IconStore

/**
 * 品牌方块。
 *
 * 默认是「品牌色圆角底 + 单色 glyph」；[Platform.colored] 为 true 的平台
 * （Google 四色 G、Microsoft 四色方块）改画官方配色：不铺底色、不 tint，原样输出。
 *
 * [iconName] 非空时用条目自己的图标（相册上传或联网抓取），铺满整个方块；
 * 否则退回平台内置 glyph。
 */
@Composable
fun BrandTile(
    platform: Platform,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    iconName: String? = null
) {
    val ctx = LocalContext.current
    val bitmap = remember(iconName) {
        if (iconName.isNullOrBlank()) null else IconStore.loadIcon(ctx, iconName)
    }

    val base = Color(platform.tileColor)
    val fg = Color(platform.fgColor)
    val darken = if (base.luminance() > 0.5f) 0.05f else 0.22f
    val brush = Brush.linearGradient(
        listOf(base, base.blend(Color.Black, darken))
    )
    val shape = RoundedCornerShape(size * 0.29f)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            // 官方配色图标自带形状，铺底色反而会在四角露出色边
            .then(if (platform.colored) Modifier else Modifier.background(brush)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = platform.label,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            Icon(
                painter = painterResource(platform.icon),
                contentDescription = platform.label,
                tint = if (platform.colored) Color.Unspecified else fg,
                modifier = Modifier.size(size * platform.iconScale)
            )
        }
    }
}

/** 简单的线性混色，避免引入额外依赖 */
private fun Color.blend(other: Color, ratio: Float): Color = Color(
    red = red + (other.red - red) * ratio,
    green = green + (other.green - green) * ratio,
    blue = blue + (other.blue - blue) * ratio,
    alpha = alpha + (other.alpha - alpha) * ratio
)