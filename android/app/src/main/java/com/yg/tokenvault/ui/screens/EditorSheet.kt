package com.yg.tokenvault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.ui.components.BrandTile
import com.yg.tokenvault.ui.theme.Accent
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.util.Base32
import com.yg.tokenvault.util.IconStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onGenerateSecret: () -> String,
    onGeneratePassword: () -> String = { "" }
) {
    val pal = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var type by remember { mutableStateOf(existing?.type ?: EntryType.CODE) }
    var platformKey by remember { mutableStateOf(existing?.platformKey ?: "google") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var account by remember { mutableStateOf(existing?.account ?: "") }
    var secret by remember { mutableStateOf(existing?.secret ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var digits by remember { mutableStateOf(existing?.digits ?: 6) }
    var period by remember { mutableStateOf(existing?.period ?: 30) }
    var algo by remember { mutableStateOf(existing?.algo ?: "SHA1") }
    var showAdvanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pfOpen by remember { mutableStateOf(false) }

    // 条目自定义图标：上传本地图片，或按账号里的域名联网抓取
    var iconPath by remember { mutableStateOf(existing?.iconPath ?: "") }
    var iconBusy by remember { mutableStateOf(false) }
    var iconMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val pickIcon = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        iconBusy = true
        iconMsg = "正在处理图片…"
        scope.launch {
            val saved = withContext(Dispatchers.IO) { IconStore.saveIconFromUri(ctx, uri) }
            iconBusy = false
            if (saved != null) {
                iconPath = saved
                iconMsg = "图标已更新"
            } else {
                iconMsg = "这张图片读不出来，换一张试试"
            }
        }
    }

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
        if (type == EntryType.LOGIN && account.isBlank() && password.isBlank()) {
            error = "账号和密码至少填一项"
            return
        }
        val entry = (existing ?: TokenEntry()).copy(
            type = type,
            platformKey = platformKey,
            name = name.trim().ifBlank { Platforms.of(platformKey).label },
            account = account.trim(),
            secret = cleanSecret,
            password = password,
            digits = if (isSteam) 5 else digits,
            period = period,
            algo = algo,
            charset = if (isSteam) "STEAM" else "NUM",
            note = note.trim(),
            iconPath = iconPath
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
                TypeTab("账号密码", type == EntryType.LOGIN) { type = EntryType.LOGIN }
                TypeTab("密钥笔记", type == EntryType.NOTE) { type = EntryType.NOTE }
            }

            Spacer(Modifier.height(16.dp))

            // 平台选择：默认折叠成一行，点开才展开（内置 4 个 + 自定义压尾）
            SectionTitle("平台")
            Spacer(Modifier.height(9.dp))
            val curPlatform = Platforms.of(platformKey)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(pal.surfaceHi.copy(alpha = if (pfOpen) 0.85f else 0.5f))
                    .border(
                        width = 1.dp,
                        color = if (pfOpen) Accent.copy(alpha = 0.8f) else pal.border,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .androidClickable { pfOpen = !pfOpen }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandTile(platform = curPlatform, size = 32.dp, iconName = iconPath)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (Platforms.isCustom(platformKey) && name.isNotBlank())
                            "自定义 · $name" else curPlatform.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = pal.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pfOpen) "收起" else "点击更换（内置 4 个 + 自定义）",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = pal.text3
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (pfOpen) "收起平台列表" else "展开平台列表",
                    tint = pal.text2,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (pfOpen) 180f else 0f)
                )
            }

            AnimatedVisibility(visible = pfOpen) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Platforms.picker.forEach { p ->
                            val on = p.key == platformKey
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .androidClickable {
                                        platformKey = p.key
                                        if (p.key == Platforms.KEY_STEAM) {
                                            digits = 5
                                            algo = "SHA1"
                                        }
                                        pfOpen = false
                                        error = null
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (on) 2.dp else 0.dp,
                                            color = if (on) Accent else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(if (on) 1.5.dp else 3.5.dp)
                                ) {
                                    BrandTile(
                                        platform = p,
                                        size = 40.dp,
                                        iconName = if (on) iconPath else null
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = p.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp
                                    ),
                                    color = if (on) pal.accent else pal.text3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "只用这四个常用平台；其它服务选「自定义」，下面的图标区可以传自己的图",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = pal.text3
                    )
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

            Spacer(Modifier.height(14.dp))

            // 图标：上传本地图片 / 按账号域名联网抓取 / 重置为平台内置
            SectionTitle("图标")
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(pal.surfaceHi.copy(alpha = 0.5f))
                    .border(1.dp, pal.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandTile(platform = Platforms.of(platformKey), size = 46.dp, iconName = iconPath)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = iconMsg ?: if (iconPath.isBlank()) "用平台内置图标" else "已用自定义图标",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                        color = if (iconBusy) pal.accent else pal.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MiniAction("上传图片") {
                            pickIcon.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        MiniAction(if (iconBusy) "处理中…" else "联网抓取") {
                            if (!iconBusy) {
                                val host = IconStore.guessHost(account, name)
                                if (host.isNullOrBlank()) {
                                    iconMsg = "先在「账号」里填邮箱或域名"
                                } else {
                                    iconBusy = true
                                    iconMsg = "正在抓取 $host …"
                                    scope.launch {
                                        val saved = withContext(Dispatchers.IO) {
                                            IconStore.fetchIconFromHost(ctx, host)
                                        }
                                        iconBusy = false
                                        if (saved != null) {
                                            iconPath = saved
                                            iconMsg = "已抓取 $host 的图标"
                                        } else {
                                            iconMsg = "没抓到 $host，可手动上传"
                                        }
                                    }
                                }
                            }
                        }
                        if (iconPath.isNotBlank()) {
                            MiniAction("重置") {
                                IconStore.deleteIcon(ctx, iconPath)
                                iconPath = ""
                                iconMsg = null
                            }
                        }
                    }
                }
            }

            if (type == EntryType.LOGIN) {
                Spacer(Modifier.height(11.dp))

                Field(
                    label = "密码",
                    value = password,
                    onChange = { password = it; error = null },
                    placeholder = "登录密码 / 应用专用密码",
                    masked = !showPassword,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff
                                else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "隐藏" else "显示",
                                tint = pal.text3,
                                modifier = Modifier
                                    .size(19.dp)
                                    .androidClickable { showPassword = !showPassword }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "随机",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                color = pal.accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .androidClickable {
                                        password = onGeneratePassword()
                                        showPassword = true
                                        error = null
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                )
            }

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
    masked: Boolean = false,
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
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
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

/** 图标区用的小胶囊按钮 */
@Composable
private fun MiniAction(text: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = pal.accent,
        modifier = Modifier
            .clip(CircleShape)
            .background(pal.accent.copy(alpha = 0.12f))
            .androidClickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}