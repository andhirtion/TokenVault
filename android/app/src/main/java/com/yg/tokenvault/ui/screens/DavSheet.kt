package com.yg.tokenvault.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.data.DavBackup
import com.yg.tokenvault.data.DavCfg
import com.yg.tokenvault.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebDAV 云备份面板。
 *
 * 一条 GET/PUT 就能完成备份与恢复，所以只支持「单个 JSON 文件」这种最朴素的用法 ——
 * 坚果云、Nextcloud、Alist、rclone serve webdav 全部适用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DavSheet(
    cfg0: DavCfg,
    onDismiss: () -> Unit,
    onSave: (DavCfg) -> Unit,
    onTest: (DavCfg, (Boolean, String) -> Unit) -> Unit,
    onUpload: (DavCfg, String?, (Boolean, String) -> Unit) -> Unit,
    onRestore: (DavCfg, String?, (Boolean, String) -> Unit) -> Unit,
    history: List<DavBackup> = emptyList(),
    onRefreshHistory: (DavCfg, () -> Unit) -> Unit = { _, done -> done() },
    onRestoreNamed: (String, String?, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> }
) {
    val pal = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var url by remember { mutableStateOf(cfg0.url) }
    var user by remember { mutableStateOf(cfg0.user) }
    var pass by remember { mutableStateOf(cfg0.pass) }
    var path by remember { mutableStateOf(cfg0.path.ifBlank { "KeyCoffer/backup.json" }) }
    var auto by remember { mutableStateOf(cfg0.auto) }
    var pwd by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf(cfg0.lastMsg) }
    var err by remember { mutableStateOf(false) }

    val cur: () -> DavCfg = { DavCfg(url = url, user = user, pass = pass, path = path, auto = auto) }

    // 打开面板就把历史拉一遍（用表单里的当前值，用户刚改地址也能立刻生效）
    LaunchedEffect(Unit) { onRefreshHistory(cur()) { } }

    fun finish(ok: Boolean, text: String) {
        msg = text
        err = !ok
        busy = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WebDAV 云备份",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = pal.text
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "备份加密后再上传，云端只有密文",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = pal.text3
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(pal.surfaceHi)
                        .androidClickable(onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = pal.text3,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            DavField("服务器地址", url, "https://dav.jianguoyun.com/dav/", false) { url = it }
            DavField("账号", user, "you@example.com", false) { user = it }
            DavField("应用密码", pass, "坚果云在「安全 → 应用密码」生成", true) { pass = it }
            DavField("远端路径", path, "KeyCoffer/backup.json", false) { path = it }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "坚果云填 https://dav.jianguoyun.com/dav/，账号是登录邮箱，" +
                    "密码必须用「应用密码」而不是网页登录密码。",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(lineHeight = 17.sp),
                color = pal.text3
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.surfaceHi)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自动备份", fontSize = 14.sp, color = pal.text)
                    Text(
                        "改动后 8 秒内静默上传",
                        fontSize = 11.5.sp,
                        color = pal.text3
                    )
                }
                Switch(
                    checked = auto,
                    onCheckedChange = { auto = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = pal.accent
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            DavField("备份密码（可留空）", pwd, "留空则用解锁 PIN", true) { pwd = it }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DavButton(
                    text = if (busy == "test") "测试中…" else "测试连接",
                    primary = false,
                    enabled = busy.isEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    busy = "test"
                    onSave(cur())
                    onTest(cur()) { ok, text -> finish(ok, text) }
                }
                DavButton(
                    text = if (busy == "up") "上传中…" else "立即备份",
                    primary = true,
                    enabled = busy.isEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    busy = "up"
                    onSave(cur())
                    onUpload(cur(), pwd.ifBlank { null }) { ok, text -> finish(ok, text) }
                }
                DavButton(
                    text = if (busy == "down") "读取中…" else "覆盖最新",
                    primary = false,
                    enabled = busy.isEmpty(),
                    modifier = Modifier.weight(0.9f)
                ) {
                    busy = "down"
                    onSave(cur())
                    onRestore(cur(), pwd.ifBlank { null }) { ok, text -> finish(ok, text) }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (err) pal.danger.copy(alpha = 0.10f) else pal.surfaceHi
                    )
                    .border(
                        1.dp,
                        if (err) pal.danger.copy(alpha = 0.28f) else pal.border,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy.isNotEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.8.dp,
                        color = pal.accent
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = msg.ifBlank { if (cfg0.configured) "点「测试连接」先确认能不能通" else "还没有配置" },
                        fontSize = 12.sp,
                        color = if (err) pal.danger else pal.text2
                    )
                    if (cfg0.lastAt > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "上次同步 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                .format(Date(cfg0.lastAt)),
                            fontSize = 10.5.sp,
                            color = pal.text3
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 历史版本：选哪一次恢复 ──────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "历史版本 · 只列云端现有的",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal.text,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (busy == "hist") "刷新中…" else "刷新",
                    fontSize = 12.sp,
                    color = if (busy == "hist") pal.text3 else pal.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .androidClickable {
                            if (busy.isEmpty()) {
                                busy = "hist"
                                onRefreshHistory(cur()) { busy = "" }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            if (history.isEmpty()) {
                Text(
                    text = "云端没有备份记录。先点「立即备份」，之后每次上传都会在同一目录留一版快照（最多保留 40 条）。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = pal.text3
                )
            } else {
                history.take(12).forEach { h ->
                    DavHistRow(
                        item = h,
                        enabled = busy.isEmpty(),
                        onPick = {
                            busy = "down"
                            onSave(cur())
                            onRestoreNamed(h.name, pwd.ifBlank { null }) { ok, text -> finish(ok, text) }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "备份内容用 AES-256-GCM 加密后才上传，云端看不到任何条目名、账号或密码。",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                ),
                color = pal.text3
            )
        }
    }
}

/** 历史版本的一行：时间 / 大小 + 恢复 */
@Composable
private fun DavHistRow(item: DavBackup, enabled: Boolean, onPick: () -> Unit) {
    val pal = LocalPalette.current
    val label = when {
        item.main -> "当前版本"
        item.at > 0 -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.at))
        else -> item.name.removeSuffix(".json")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pal.surfaceHi)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 13.sp, color = pal.text)
            val sub = buildString {
                if (item.size > 0) append("%.1f KB".format(item.size / 1024.0))
                if (item.size > 0) append(" · ")
                append(item.name)
            }
            Text(text = sub, fontSize = 10.5.sp, color = pal.text3)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(pal.accent.copy(alpha = if (enabled) 0.14f else 0.06f))
                .let { if (enabled) it.androidClickable(onPick) else it }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "覆盖",
                fontSize = 12.sp,
                color = if (enabled) pal.accent else pal.text3
            )
        }
    }
}

@Composable
private fun DavField(
    label: String,
    value: String,
    hint: String,
    secret: Boolean,
    onChange: (String) -> Unit
) {
    val pal = LocalPalette.current
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = pal.text2
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(hint, fontSize = 12.5.sp, color = pal.text3) },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                color = pal.text
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text
            ),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = pal.accent,
                unfocusedBorderColor = pal.border,
                focusedContainerColor = pal.surfaceHi,
                unfocusedContainerColor = pal.surfaceHi,
                cursorColor = pal.accent
            )
        )
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun DavButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                when {
                    !enabled -> pal.surfaceHi
                    primary -> pal.accent
                    else -> pal.surfaceHi
                }
            )
            .border(
                1.dp,
                if (primary && enabled) Color.Transparent else pal.border,
                RoundedCornerShape(13.dp)
            )
            .androidClickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> pal.text3
                primary -> Color.White
                else -> pal.text2
            }
        )
    }
}