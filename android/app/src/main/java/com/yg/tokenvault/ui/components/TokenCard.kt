package com.yg.tokenvault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.ui.theme.CodeStyle
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * 列表里的令牌卡片。点击开详情，长按快捷收藏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TokenCard(
    entry: TokenEntry,
    code: String,
    remaining: Float,
    hideCode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    val interaction = MutableInteractionSource()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandTile(platform = entry.platform, size = 42.dp)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name.ifBlank { entry.platform.label },
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = pal.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "已收藏",
                        tint = pal.warning,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = entry.account.ifBlank {
                    if (entry.type == EntryType.NOTE) "密钥笔记" else entry.platform.label
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = pal.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(10.dp))

        if (entry.type == EntryType.NOTE) {
            Text(
                text = "笔记",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = pal.text3
            )
        } else {
            CodeBlock(
                code = code,
                hidden = hideCode,
                style = CodeStyle,
                color = codeColor(remaining, pal.text, pal.danger),
                gap = 7.dp
            )
            Spacer(Modifier.width(10.dp))
            RingProgress(
                progress = remaining,
                size = 24.dp,
                stroke = 2.4.dp,
                active = ringColor(remaining, pal.accent, pal.danger),
                track = if (pal.isDark) Color(0x42FFFFFF) else Color(0x22202433)
            )
        }
    }
}

/** 验证码分组显示：避免用空格字符撑宽，改成按组排布 */
@Composable
fun CodeBlock(
    code: String,
    hidden: Boolean,
    style: TextStyle,
    color: Color,
    gap: Dp,
    modifier: Modifier = Modifier
) {
    val groups = when {
        hidden -> listOf("•".repeat(if (code.length >= 8) 4 else 3), "•".repeat(if (code.length >= 8) 4 else 3))
        code.isBlank() -> listOf("———")
        else -> code.split(' ')
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        groups.forEach { g ->
            Text(text = g, style = style, color = color, maxLines = 1)
        }
    }
}

/** 剩余时间越少，验证码越红 */
private fun codeColor(remaining: Float, normal: Color, urgent: Color): Color =
    if (remaining <= 0.16f) urgent else normal

private fun ringColor(remaining: Float, normal: Color, urgent: Color): Color = when {
    remaining <= 0.12f -> urgent
    remaining <= 0.3f -> Color(0xFFFFB020)
    else -> normal
}