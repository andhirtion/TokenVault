package com.yg.tokenvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.util.Base32
import com.yg.tokenvault.ui.components.BrandTile
import com.yg.tokenvault.ui.theme.Accent
import com.yg.tokenvault.ui.theme.LocalPalette

/**
 * 添加 / 编辑令牌。
 *
 * [existing] 非空表示编辑；[initialSecret] 用于「从剪贴板粘贴 otpauth」这类预填场景。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorSheet(
    existing: TokenEntry?,
    onDismiss: () -> Unit,
    onSave: (TokenEntry) -> Unit,
    onDelete: (String) -> Unit,
    onGenerateSecret: () -> String
) {
    val pal = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var type by remember { mutableStateOf(existing?.type ?: EntryType.CODE) }
    var platformKey by remember { mutableStateOf(existing?.platformKey ?: "google") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var account by remember { mutableStateOf(existing?.account ?: "") }
    var secret by remember { mutableStateOf(existing?.secret ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var digits by remember { mutableStateOf(existing?.digits ?: 6) }
    var period by remember { mutableStateOf(existing?.period ?: 30) }
    var algo by remember { mutableStateOf(existing?.algo ?: "SHA1") }
    var showAdvanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isSteam = platformKey == "steam"
    val secretOk = secret.isNotBlank() && Base32.isValid(secret)

    fun commit() {
        val cleanSecret = Base32.normalize(secret)
        if (type == EntryType.CODE) {
            if (!Base32.isValid(cleanSecret)) {
                error = "密钥不是有效的 Base32（至少 16 位）"
                return
            }
        }
        if (name.isBlank() && account.isBlank() && type == EntryType.CODE) {
            error = "给这个令牌起个名字吧"
            return
        }
        val entry = (existing ?: TokenEntry()).copy(
            type = type,
            platformKey = platformKey,
            name = name.trim().ifBlank { Platforms.of(platformKey).label },
            account = account.trim(),
            secret = cleanSecret,
            digits = if (isSteam) 5 else digits,
            period = period,
            algo = algo,
            charset = if (isSteam) "STEAM" else "NUM",
            note = note.trim()
        )
        onSave(entry)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.surface,
        contentColor = pal.text,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(pal.borderHi)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = if (existing == null) "添加令牌" else "编辑令牌",
                style = MaterialTheme.typography.titleLarge,
                color = pal.text,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // 类型切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeTab("动态验证码", type == EntryType.CODE) { type = EntryType.CODE }
                TypeTab("密钥笔记", type == EntryType.NOTE) { type = EntryType.NOTE }
            }

            Spacer(Modifier.height(16.dp))

            // 平台选择
            SectionTitle("平台")
            Spacer(Modifier.height(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                maxItemsInEachRow = 6
            ) {
                Platforms.picker.forEach { p ->
                    val on = p.key == platformKey
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .androidClickable {
                                platformKey = p.key
                                if (p.key == "steam") {
                                    digits = 5
                                    algo = "SHA1"
                                }
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (on) 2.dp else 0.dp,
                                    color = if (on) Accent else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(if (on) 1.5.dp else 3.dp)
                        ) {
                            BrandTile(platform = p, size = 38.dp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = p.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 0.sp
                            ),
                            color = if (on) pal.accent else pal.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Field(
                label = "名称",
                value = name,
                onChange = { name = it; error = null },
                placeholder = Platforms.of(platformKey).label
            )

            Spacer(Modifier.height(11.dp))

            Field(
                label = "账号 / 邮箱",
                value = account,
                onChange = { account = it; error = null },
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email
            )

            if (type == EntryType.CODE) {
                Spacer(Modifier.height(11.dp))

                Field(
                    label = "密钥（Base32）",
                    value = secret,
                    onChange = { secret = it; error = null },
                    placeholder = "JBSWY3DPEHPK3PXP",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = "粘贴",
                                tint = pal.text3,
                                modifier = Modifier
                                    .size(18.dp)
                                    .androidClickable {
                                        val clip = clipboard.getText()?.text ?: return@androidClickable
                                        val t = clip.trim()
                                        // 粘贴整条 otpauth:// 时自动拆字段
                                        val parsed = com.yg.tokenvault.data.Totp.parseOtpAuth(t)
                                        if (parsed != null) {
                                            secret = parsed.secret
                                            platformKey = parsed.platformKey
                                            if (name.isBlank()) name = parsed.label
                                            if (account.isBlank()) account = parsed.account
                                            digits = parsed.digits
                                            period = parsed.period
                                            algo = parsed.algo
                                        } else {
                                            secret = Base32.normalize(t)
                                        }
                                        error = null
                                    }
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (secret.isNotBlank()) {
                        Text(
                            text = if (secretOk) "密钥格式有效" else "密钥格式无效",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                            color = if (secretOk) pal.success else pal.danger
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "随机生成",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = pal.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .androidClickable {
                                secret = onGenerateSecret()
                                error = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 高级参数
                Text(
                    text = if (showAdvanced) "收起高级参数" else "高级参数（${algo} / $digits 位 / ${period}s）",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = pal.text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .androidClickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 6.dp)
                )

                if (showAdvanced) {
                    Spacer(Modifier.height(6.dp))
                    if (isSteam) {
                        Text(
                            text = "Steam 固定为 SHA1 / 5 位 / 30 秒，无需调整",
                            style = MaterialTheme.typography.bodyMedium,
                            color = pal.text3
                        )
                    } else {
                        OptionRow("算法", listOf("SHA1", "SHA256", "SHA512"), algo) { algo = it }
                        Spacer(Modifier.height(8.dp))
                        OptionRow("位数", listOf("6", "8"), digits.toString()) { digits = it.toInt() }
                        Spacer(Modifier.height(8.dp))
                        OptionRow("周期", listOf("30", "60"), period.toString()) { period = it.toInt() }
                    }
                }
            }

            Spacer(Modifier.height(11.dp))

            Field(
                label = "备注",
                value = note,
                onChange = { note = it },
                placeholder = "可选",
                singleLine = false
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.danger
                )
            }

            Spacer(Modifier.height(20.dp))

            // 保存按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Accent, com.yg.tokenvault.ui.theme.Accent2)
                        )
                    )
                    .androidClickable { commit() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "保存",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, pal.danger.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .androidClickable { onDelete(existing.id) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = pal.danger,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "删除这个令牌",
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.danger
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = LocalPalette.current.text3,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun TypeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
            else androidx.compose.ui.text.font.FontWeight.Medium
        ),
        color = if (selected) pal.accent else pal.text2,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) pal.accent.copy(alpha = 0.15f) else pal.surfaceHi)
            .border(
                1.dp,
                if (selected) pal.accent.copy(alpha = 0.5f) else pal.border,
                CircleShape
            )
            .androidClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun OptionRow(
    label: String,
    options: List<String>,
    current: String,
    onPick: (String) -> Unit
) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = pal.text2,
            modifier = Modifier.width(52.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { o ->
                val on = o == current
                Text(
                    text = o,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = if (on) pal.accent else pal.text2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) pal.accent.copy(alpha = 0.15f) else pal.surfaceHi)
                        .androidClickable { onPick(o) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val pal = LocalPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = pal.text3) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        trailingIcon = trailing,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = pal.text),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = pal.accent,
            unfocusedBorderColor = pal.border,
            focusedLabelColor = pal.accent,
            unfocusedLabelColor = pal.text3,
            cursorColor = pal.accent,
            focusedContainerColor = pal.surfaceHi.copy(alpha = 0.4f),
            unfocusedContainerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}