package com.yg.tokenvault.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.data.Totp
import com.yg.tokenvault.ui.components.BrandTile
import com.yg.tokenvault.ui.components.RingProgress
import com.yg.tokenvault.ui.theme.BigCodeStyle
import com.yg.tokenvault.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/**
 * 令牌详情：大号验证码、剩余时间、复制、编辑、删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    entry: TokenEntry,
    now: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onCopied: (String) -> Unit
) {
    val pal = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    val result = entry.currentCode(now)
    val code = result?.code ?: ""
    val remaining = result?.progress ?: 0f
    val seconds = result?.remaining ?: 0

    var justCopied by remember { mutableStateOf(false) }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1200)
            justCopied = false
        }
    }

    val barProgress by animateFloatAsState(
        targetValue = remaining,
        animationSpec = tween(300),
        label = "bar"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.surface,
        contentColor = pal.text,
        scrimColor = Color.Black.copy(alpha = 0.58f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(pal.borderHi)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandTile(platform = entry.platform, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name.ifBlank { entry.platform.label },
                        style = MaterialTheme.typography.titleLarge,
                        color = pal.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.platform.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.text3
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .androidClickable { onToggleFav() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (entry.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "收藏",
                        tint = if (entry.favorite) pal.warning else pal.text3,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            if (entry.type == EntryType.CODE) {
                // 大号验证码
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(pal.surfaceHi.copy(alpha = 0.55f))
                        .border(1.dp, pal.border, RoundedCornerShape(20.dp))
                        .androidClickable {
                            if (code.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(code))
                                justCopied = true
                                onCopied(code)
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val groups = if (code.isEmpty()) listOf("———") else Totp.format(code).split(' ')
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        groups.forEach { g ->
                            Text(
                                text = g,
                                style = BigCodeStyle,
                                color = if (remaining <= 0.16f) pal.danger else pal.text
                            )
                        }
                    }
                    if (justCopied) {
                        Text(
                            text = "已复制",
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.success
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制",
                            tint = pal.text3,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 剩余时间条
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(pal.borderHi)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barProgress.coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            pal.accent,
                                            if (remaining <= 0.16f) pal.danger else pal.accent2
                                        )
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = "${seconds}s",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = pal.text3
                    )
                    Spacer(Modifier.width(8.dp))
                    RingProgress(
                        progress = remaining,
                        size = 26.dp,
                        stroke = 2.6.dp,
                        active = if (remaining <= 0.16f) pal.danger else pal.accent,
                        track = if (pal.isDark) Color(0x42FFFFFF) else Color(0x22202433)
                    )
                }

                Spacer(Modifier.height(22.dp))
            }

            // 信息表
            InfoRow("账号", entry.account.ifBlank { "—" })
            InfoRow("类型", if (entry.type == EntryType.CODE) "TOTP 动态验证码" else "密钥笔记")
            if (entry.type == EntryType.CODE) {
                InfoRow("算法", "${entry.algo} · ${entry.digits} 位 · ${entry.period}s")
                InfoRow("密钥", maskSecret(entry.secret))
            }
            if (entry.note.isNotBlank()) InfoRow("备注", entry.note)

            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActionButton(
                    label = "编辑",
                    icon = Icons.Outlined.Edit,
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                )
                ActionButton(
                    label = "删除",
                    icon = Icons.Outlined.DeleteOutline,
                    modifier = Modifier.weight(1f),
                    danger = true,
                    onClick = onDelete
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val pal = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = pal.text3,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = pal.text2,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val pal = LocalPalette.current
    val tint = if (danger) pal.danger else pal.text2
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pal.surfaceHi.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (danger) pal.danger.copy(alpha = 0.35f) else pal.border,
                RoundedCornerShape(14.dp)
            )
            .androidClickable(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** 密钥脱敏：ABCD EFGH ... WXYZ */
private fun maskSecret(s: String): String {
    if (s.length <= 8) return "•".repeat(s.length.coerceAtLeast(4))
    val head = s.take(4)
    val tail = s.takeLast(4)
    return "$head •••• •••• $tail"
}