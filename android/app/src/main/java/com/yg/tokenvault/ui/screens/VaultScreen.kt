package com.yg.tokenvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.VaultViewModel
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.ui.components.RingProgress
import com.yg.tokenvault.ui.components.TokenCard
import com.yg.tokenvault.ui.theme.Accent
import com.yg.tokenvault.ui.theme.Accent2
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * 令牌列表页：搜索、平台筛选、分组、空状态、悬浮添加按钮。
 */
@Composable
fun VaultScreen(
    state: VaultViewModel.State,
    now: Long,
    onSearch: (String) -> Unit,
    onFilter: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    var searching by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 顶部标题行 ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "令牌库",
                        style = MaterialTheme.typography.headlineMedium,
                        color = pal.text
                    )
                    Text(
                        text = if (state.entries.isEmpty()) "离线生成 · 仅存本机"
                        else "${state.entries.size} 个令牌 · 离线生成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.text3
                    )
                }
                if (!searching) {
                    Text(
                        text = "获取",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = pal.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pal.accent.copy(alpha = 0.14f))
                            .androidClickable(onImport)
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                IconGhostButton(
                    onClick = { searching = !searching; if (!searching) onSearch("") },
                    icon = if (searching) Icons.Outlined.Close else Icons.Filled.Search
                )
            }

            // ── 搜索框 ──────────────────────────────────
            AnimatedVisibility(visible = searching, enter = fadeIn(), exit = fadeOut()) {
                SearchField(
                    value = state.query,
                    onValueChange = onSearch,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // ── 平台筛选 chips ───────────────────────────
            if (state.entries.isNotEmpty()) {
                val used = state.usedPlatforms
                val showFav = state.entries.any { it.favorite }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    FilterChip(
                        label = "全部",
                        selected = state.filter == null,
                        onClick = { onFilter(null) }
                    )
                    if (showFav) {
                        FilterChip(
                            label = "收藏",
                            selected = state.filter == VaultViewModel.FILTER_FAV,
                            onClick = { onFilter(VaultViewModel.FILTER_FAV) },
                            icon = true
                        )
                    }
                    used.forEach { key ->
                        val p = Platforms.of(key)
                        FilterChip(
                            label = p.label,
                            selected = state.filter == key,
                            onClick = { onFilter(if (state.filter == key) null else key) }
                        )
                    }
                }
            }

            // ── 列表 ────────────────────────────────────
            val list = state.filtered
            if (list.isEmpty()) {
                EmptyState(
                    hasAny = state.entries.isNotEmpty(),
                    onAdd = onAdd,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val favs = list.filter { it.favorite }
                val rest = list.filter { !it.favorite }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (favs.isNotEmpty()) {
                        item { SectionLabel("收藏") }
                        items(favs, key = { it.id }) { e ->
                            EntryRow(e, now, state.hideCode, onOpen, onToggleFav)
                        }
                        if (rest.isNotEmpty()) item { SectionLabel("全部令牌") }
                    }
                    items(rest, key = { it.id }) { e ->
                        EntryRow(e, now, state.hideCode, onOpen, onToggleFav)
                    }
                }
            }
        }

        // ── 悬浮添加按钮 ────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 104.dp)
                .shadow(18.dp, RoundedCornerShape(20.dp), ambientColor = Accent, spotColor = Accent)
                .size(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Accent, Accent2),
                        start = Offset(0f, 0f),
                        end = Offset(150f, 150f)
                    )
                )
                .androidClickable(onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加令牌",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: com.yg.tokenvault.data.TokenEntry,
    now: Long,
    hideCode: Boolean,
    onOpen: (String) -> Unit,
    onToggleFav: (String) -> Unit
) {
    val result = entry.currentCode(now)
    TokenCard(
        entry = entry,
        code = result?.let { com.yg.tokenvault.data.Totp.format(it.code) } ?: "",
        remaining = result?.progress ?: 0f,
        hideCode = hideCode,
        onClick = { onOpen(entry.id) },
        onLongClick = { onToggleFav(entry.id) },
        onToggleFavorite = { onToggleFav(entry.id) }
    )
}

@Composable
private fun SectionLabel(text: String) {
    val pal = LocalPalette.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
        color = pal.text3,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 3.dp)
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: Boolean = false
) {
    val pal = LocalPalette.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) pal.accent.copy(alpha = 0.16f) else pal.surface)
            .border(
                width = 1.dp,
                color = if (selected) pal.accent.copy(alpha = 0.55f) else pal.border,
                shape = CircleShape
            )
            .androidClickable(onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = if (selected) pal.accent else pal.text3,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) pal.accent else pal.text2
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    val kb = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = pal.text3,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "搜索名称、账号或平台",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = pal.text3
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = pal.text,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(pal.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyState(
    hasAny: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(pal.surfaceHi)
                .border(1.dp, pal.border, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = pal.text3,
                modifier = Modifier.size(29.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (hasAny) "没有匹配的令牌" else "还没有令牌",
            style = MaterialTheme.typography.titleMedium,
            color = pal.text2
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (hasAny) "换个关键词或清除筛选试试"
            else "点右下角 + 添加第一个\n支持 Google、Microsoft、GitHub、Steam 等",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.text3,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (!hasAny) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pal.accent.copy(alpha = 0.15f))
                    .border(1.dp, pal.accent.copy(alpha = 0.45f), CircleShape)
                    .androidClickable(onAdd)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = pal.accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "添加令牌",
                    style = MaterialTheme.typography.labelLarge,
                    color = pal.accent
                )
            }
        }
    }
}

/** 顶部方框图标按钮 */
@Composable
fun IconGhostButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(12.dp))
            .androidClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = pal.text2,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 无涟漪点击的简写，避免每处都写 interactionSource */
@Composable
fun Modifier.androidClickable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        onClick = onClick
    )
}