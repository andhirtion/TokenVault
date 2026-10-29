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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.VaultViewModel
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.ui.theme.ThemeMode

/**
 * 设置页：外观、安全、数据、关于。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: VaultViewModel.State,
    onTheme: (ThemeMode) -> Unit,
    onHideCode: (Boolean) -> Unit,
    onShowRing: (Boolean) -> Unit,
    onAutoLock: (Int) -> Unit,
    onClipClear: (Int) -> Unit,
    onChangePin: (String, String) -> Unit,
    onExport: (String) -> String?,
    onImport: (String, String) -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    bioEnabled: Boolean = false,
    bioAvailable: Boolean = false,
    onToggleBio: (Boolean) -> Unit = {}
) {
    val pal = LocalPalette.current
    val clipboard = LocalClipboardManager.current

    var showPinDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 130.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = pal.text
        )
        Text(
            text = "外观、安全与数据管理",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.text3
        )

        Spacer(Modifier.height(22.dp))

        // ── 外观 ────────────────────────────────
        GroupLabel("外观")
        Section {
            Text(
                text = "主题",
                style = MaterialTheme.typography.titleMedium,
                color = pal.text
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice(
                    label = "跟随系统",
                    icon = Icons.Outlined.SettingsBrightness,
                    selected = state.themeMode == ThemeMode.SYSTEM,
                    modifier = Modifier.weight(1f)
                ) {
                    onTheme(ThemeMode.SYSTEM)
                }
                ThemeChoice(
                    label = "深色",
                    icon = Icons.Outlined.DarkMode,
                    selected = state.themeMode == ThemeMode.DARK,
                    modifier = Modifier.weight(1f)
                ) {
                    onTheme(ThemeMode.DARK)
                }
                ThemeChoice(
                    label = "浅色",
                    icon = Icons.Outlined.LightMode,
                    selected = state.themeMode == ThemeMode.LIGHT,
                    modifier = Modifier.weight(1f)
                ) {
                    onTheme(ThemeMode.LIGHT)
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "显示剩余时间环",
                subtitle = "列表卡片右侧的倒计时圆环",
                checked = state.showRing,
                onCheckedChange = onShowRing
            )
        }

        Spacer(Modifier.height(14.dp))
        GroupLabel("安全")
        Section {
            ToggleRow(
                title = "指纹解锁",
                subtitle = when {
                    bioEnabled -> "指纹验证通过后直接进入，不用输入 PIN"
                    bioAvailable -> "用指纹或面容代替输入 PIN"
                    else -> "这台设备没有可用的指纹或面容"
                },
                checked = bioEnabled,
                onCheckedChange = { want -> if (bioAvailable) onToggleBio(want) }
            )
            Divider()
            ToggleRow(
                title = "隐藏验证码",
                subtitle = "列表默认显示为圆点，点开详情查看",
                checked = state.hideCode,
                onCheckedChange = onHideCode
            )
            Divider()
            OptionPickRow(
                title = "自动锁定",
                options = listOf("1 分钟" to 1, "3 分钟" to 3, "5 分钟" to 5, "永不" to 0),
                current = state.autoLockMin,
                onPick = onAutoLock
            )
            Divider()
            OptionPickRow(
                title = "剪贴板清除",
                options = listOf("15 秒" to 15, "30 秒" to 30, "60 秒" to 60),
                current = state.clipClearSec,
                onPick = onClipClear
            )
            Divider()
            ActionRow(
                title = "修改访问 PIN",
                subtitle = "更换后旧 PIN 立即失效",
                icon = Icons.Outlined.Key,
                onClick = { showPinDialog = true }
            )
            Divider()
            ActionRow(
                title = "立即锁定",
                subtitle = "清除内存中的密钥",
                icon = Icons.Outlined.Lock,
                onClick = onLock
            )
        }

        Spacer(Modifier.height(14.dp))
        GroupLabel("数据")
        Section {
            ActionRow(
                title = "导出加密备份",
                subtitle = "独立密码保护，可存到网盘或电脑",
                icon = Icons.Outlined.Upload,
                onClick = { showExportDialog = true }
            )
            Divider()
            ActionRow(
                title = "导入加密备份",
                subtitle = "从剪贴板读取备份内容",
                icon = Icons.Outlined.Download,
                onClick = { showImportDialog = true }
            )
        }

        Spacer(Modifier.height(14.dp))
        GroupLabel("关于")
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TokenVault",
                        style = MaterialTheme.typography.titleMedium,
                        color = pal.text
                    )
                    Text(
                        text = "版本 1.0.0 · 纯本地",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.text3
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "所有密钥经 PBKDF2-SHA256（25 万次迭代）派生的 AES-256-GCM 密钥加密后保存在本机，" +
                    "应用不申请网络权限，验证码全程离线计算。",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = pal.text2
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    // ── 修改 PIN ────────────────────────────────
    if (showPinDialog) {
        PinChangeDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { old, new ->
                onChangePin(old, new)
                showPinDialog = false
            }
        )
    }

    // ── 导出 ────────────────────────────────────
    if (showExportDialog) {
        SecretDialog(
            title = "设置备份密码",
            hint = "备份文件用这个密码加密，导入时需要它",
            confirmLabel = "生成并复制",
            onDismiss = { showExportDialog = false },
            onConfirm = { pw ->
                val text = onExport(pw)
                if (text != null) {
                    clipboard.setText(AnnotatedString(text))
                }
                showExportDialog = false
            }
        )
    }

    // ── 导入 ────────────────────────────────────
    if (showImportDialog) {
        SecretDialog(
            title = "输入备份密码",
            hint = "将从剪贴板读取备份内容",
            confirmLabel = "导入",
            onDismiss = { showImportDialog = false },
            onConfirm = { pw ->
                val clip = clipboard.getText()?.text
                if (!clip.isNullOrBlank()) onImport(clip, pw)
                showImportDialog = false
            }
        )
    }
}

// ── 复用小组件 ──────────────────────────────────

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
        color = LocalPalette.current.text3,
        modifier = Modifier.padding(start = 6.dp, bottom = 7.dp)
    )
}

@Composable
private fun Section(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(20.dp))
            .padding(15.dp),
        content = content
    )
}

@Composable
private fun Divider() {
    val pal = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp)
            .height(1.dp)
            .background(pal.border)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val pal = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = pal.text)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = pal.text3)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = pal.accent,
                uncheckedThumbColor = pal.text3,
                uncheckedTrackColor = pal.surfaceHi,
                uncheckedBorderColor = pal.border
            )
        )
    }
}

@Composable
private fun OptionPickRow(
    title: String,
    options: List<Pair<String, Int>>,
    current: Int,
    onPick: (Int) -> Unit
) {
    val pal = LocalPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = pal.text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { (label, value) ->
                val on = value == current
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = if (on) pal.accent else pal.text2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) pal.accent.copy(alpha = 0.15f) else pal.surfaceHi)
                        .border(
                            1.dp,
                            if (on) pal.accent.copy(alpha = 0.45f) else pal.border,
                            CircleShape
                        )
                        .androidClickable { onPick(value) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .androidClickable(onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(pal.surfaceHi),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = pal.text2,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = pal.text)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = pal.text3)
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = pal.text3
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) pal.accent.copy(alpha = 0.14f) else pal.surfaceHi)
            .border(
                1.dp,
                if (selected) pal.accent.copy(alpha = 0.5f) else pal.border,
                RoundedCornerShape(14.dp)
            )
            .androidClickable(onClick)
            .padding(vertical = 11.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) pal.accent else pal.text2,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
            color = if (selected) pal.accent else pal.text2
        )
    }
}

@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val pal = LocalPalette.current
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surface,
        titleContentColor = pal.text,
        textContentColor = pal.text2,
        title = { Text("修改访问 PIN") },
        text = {
            Column {
                PinField("当前 PIN", old) { old = it; err = null }
                Spacer(Modifier.height(9.dp))
                PinField("新 PIN（6 位）", new) { new = it; err = null }
                Spacer(Modifier.height(9.dp))
                PinField("再次输入新 PIN", again) { again = it; err = null }
                if (err != null) {
                    Spacer(Modifier.height(9.dp))
                    Text(text = err!!, color = pal.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    new.length < 4 -> err = "新 PIN 至少 4 位"
                    new != again -> err = "两次输入不一致"
                    else -> onConfirm(old, new)
                }
            }) { Text("保存", color = pal.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = pal.text3) }
        }
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    numeric: Boolean = true,
    onChange: (String) -> Unit
) {
    val pal = LocalPalette.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = {
            if (numeric) {
                if (it.length <= 12 && it.all(Char::isDigit)) onChange(it)
            } else {
                if (it.length <= 64) onChange(it)
            }
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = pal.accent,
            unfocusedBorderColor = pal.border,
            focusedLabelColor = pal.accent,
            unfocusedLabelColor = pal.text3,
            cursorColor = pal.accent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SecretDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val pal = LocalPalette.current
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surface,
        titleContentColor = pal.text,
        textContentColor = pal.text2,
        title = { Text(title) },
        text = {
            Column {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = pal.text3)
                Spacer(Modifier.height(10.dp))
                PinField("密码", pw, numeric = false) { pw = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (pw.length >= 4) onConfirm(pw) },
                enabled = pw.length >= 4
            ) { Text(confirmLabel, color = if (pw.length >= 4) pal.accent else pal.text3) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = pal.text3) }
        }
    )
}