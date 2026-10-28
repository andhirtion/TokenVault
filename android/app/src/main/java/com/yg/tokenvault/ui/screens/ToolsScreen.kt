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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.data.Totp
import com.yg.tokenvault.ui.components.RingProgress
import com.yg.tokenvault.ui.theme.CodeStyle
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.util.Base32
import java.security.SecureRandom

/**
 * 安全工具页：随机口令、Base32 密钥、TOTP 快速校验。
 */
@Composable
fun ToolsScreen(
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalPalette.current
    val rnd = remember { SecureRandom() }

    // 口令
    var length by remember { mutableStateOf(18f) }
    var useLower by remember { mutableStateOf(true) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigit by remember { mutableStateOf(true) }
    var useSymbol by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }

    fun regen() {
        password = buildPassword(rnd, length.toInt(), useLower, useUpper, useDigit, useSymbol)
    }

    // Base32
    var secretLen by remember { mutableStateOf(20f) }
    var secret by remember { mutableStateOf(Base32.randomSecret(20)) }

    // 快速校验
    var probeInput by remember { mutableStateOf("") }
    var probeNow by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            probeNow = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val probeResult = remember(probeInput, probeNow) {
        if (Base32.isValid(probeInput)) Totp.generate(probeInput) else null
    }

    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 130.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text(text = "安全工具", style = MaterialTheme.typography.headlineMedium, color = pal.text)
        Text(
            text = "全部本地生成，不联网",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.text3
        )

        Spacer(Modifier.height(20.dp))

        // ── 获取密钥 ────────────────────────────
        ToolCard("获取密钥") {
            Text(
                text = "扫码 / 登录账号 / 相册图片 / 剪贴板，四种方式把已有的两步验证密钥搬进来",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.text2
            )
            Spacer(Modifier.height(13.dp))
            PrimaryButton("选择获取方式") { onImport() }
        }

        Spacer(Modifier.height(14.dp))

        // ── 随机口令 ────────────────────────────
        ToolCard("随机口令") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (password.isBlank()) "点生成开始" else
                        if (password.length > 26) password.take(26) + "…" else password,
                    style = CodeStyle.copy(fontSize = 17.sp),
                    color = if (password.isBlank()) pal.text3 else pal.text,
                    modifier = Modifier.weight(1f)
                )
                MiniIcon(Icons.Outlined.Refresh) { regen() }
                Spacer(Modifier.width(7.dp))
                MiniIcon(Icons.Outlined.ContentCopy, enabled = password.isNotBlank()) {
                    clipboard.setText(AnnotatedString(password))
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "长度 ${length.toInt()}",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = pal.text2
            )
            Slider(
                value = length,
                onValueChange = { length = it },
                valueRange = 8f..48f,
                steps = 39,
                colors = SliderDefaults.colors(
                    thumbColor = pal.accent,
                    activeTrackColor = pal.accent,
                    inactiveTrackColor = pal.borderHi
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ToggleChip("a-z", useLower) { useLower = !useLower }
                ToggleChip("A-Z", useUpper) { useUpper = !useUpper }
                ToggleChip("0-9", useDigit) { useDigit = !useDigit }
                ToggleChip("!@#", useSymbol) { useSymbol = !useSymbol }
            }

            Spacer(Modifier.height(13.dp))

            PrimaryButton("生成口令") { regen() }
        }

        Spacer(Modifier.height(14.dp))

        // ── Base32 密钥 ─────────────────────────
        ToolCard("Base32 密钥") {
            Text(
                text = Base32.group(secret, 4),
                style = CodeStyle.copy(fontSize = 15.sp, letterSpacing = 0.6.sp),
                color = pal.text
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "长度 ${secretLen.toInt()} 位",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = pal.text2
            )
            Slider(
                value = secretLen,
                onValueChange = { secretLen = it },
                valueRange = 16f..40f,
                steps = 23,
                colors = SliderDefaults.colors(
                    thumbColor = pal.accent,
                    activeTrackColor = pal.accent,
                    inactiveTrackColor = pal.borderHi
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniIcon(Icons.Outlined.Refresh) {
                    secret = Base32.randomSecret(secretLen.toInt())
                }
                PrimaryButton(
                    label = "生成并复制",
                    modifier = Modifier.weight(1f)
                ) {
                    secret = Base32.randomSecret(secretLen.toInt())
                    clipboard.setText(AnnotatedString(secret))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 快速校验 ────────────────────────────
        ToolCard("验证码试算") {
            Text(
                text = "粘贴 Base32 密钥，直接看当前验证码（不会保存）",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.text3
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = probeInput,
                onValueChange = { probeInput = it.uppercase().filter { c -> c.isLetterOrDigit() || c == ' ' } },
                placeholder = { Text("JBSWY3DPEHPK3PXP", color = pal.text3) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = pal.text,
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = pal.accent,
                    unfocusedBorderColor = pal.border,
                    cursorColor = pal.accent
                ),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = probeResult?.let { Totp.format(it.code) } ?: "——— ———",
                    style = CodeStyle.copy(fontSize = 24.sp),
                    color = if (probeResult == null) pal.text3 else pal.text,
                    modifier = Modifier.weight(1f)
                )
                if (probeResult != null) {
                    RingProgress(
                        progress = probeResult.progress,
                        size = 26.dp,
                        stroke = 2.6.dp,
                        active = pal.accent,
                        track = if (pal.isDark) Color(0x42FFFFFF) else Color(0x22202433)
                    )
                    Spacer(Modifier.width(10.dp))
                    MiniIcon(Icons.Outlined.ContentCopy) {
                        clipboard.setText(AnnotatedString(probeResult.code))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ── 小组件 ──────────────────────────────────────

@Composable
private fun ToolCard(title: String, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pal.surface)
            .border(1.dp, pal.border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = pal.text3
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MiniIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(pal.surfaceHi)
            .androidClickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) pal.text2 else pal.text3.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
        ),
        color = if (on) pal.accent else pal.text3,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) pal.accent.copy(alpha = 0.15f) else pal.surfaceHi)
            .border(
                1.dp,
                if (on) pal.accent.copy(alpha = 0.45f) else pal.border,
                CircleShape
            )
            .androidClickable(onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

@Composable
private fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(pal.accent.copy(alpha = 0.16f))
            .border(1.dp, pal.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
            .androidClickable(onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = pal.accent)
    }
}

/** 口令生成：保证四类字符各至少一个 */
private fun buildPassword(
    rnd: SecureRandom,
    len: Int,
    lower: Boolean,
    upper: Boolean,
    digit: Boolean,
    symbol: Boolean
): String {
    val l = "abcdefghijkmnopqrstuvwxyz"
    val u = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val d = "23456789"
    val s = "!@#$%^&*()-_=+[]{};:,.?"
    val pools = buildList {
        if (lower) add(l)
        if (upper) add(u)
        if (digit) add(d)
        if (symbol) add(s)
    }
    if (pools.isEmpty()) return ""
    val all = pools.joinToString("")
    val chars = StringBuilder()
    // 每类先来一个，保证必含
    pools.forEach { p -> chars.append(p[rnd.nextInt(p.length)]) }
    while (chars.length < len) chars.append(all[rnd.nextInt(all.length)])
    // 打乱
    val arr = chars.toString().toCharArray()
    for (i in arr.indices.reversed()) {
        val j = rnd.nextInt(i + 1)
        val t = arr[i]; arr[i] = arr[j]; arr[j] = t
    }
    return String(arr).take(len)
}