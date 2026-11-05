package com.yg.tokenvault.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.tokenvault.VaultViewModel
import com.yg.tokenvault.data.DavCfg
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.ui.theme.ThemeMode
import com.yg.tokenvault.update.CheckResult
import com.yg.tokenvault.update.UpdateInfo
import com.yg.tokenvault.update.Updater
import kotlinx.coroutines.launch
import java.io.File

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
    onToggleBio: (Boolean) -> Unit = {},
    hasBg: Boolean = false,
    onPickBg: (Uri) -> Unit = {},
    onClearBg: () -> Unit = {},
    davCfg: DavCfg = DavCfg(),
    onDavSave: (DavCfg) -> Unit = {},
    onDavTest: (DavCfg, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onDavUpload: (DavCfg, String?, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onDavRestore: (DavCfg, String?, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    davHistory: List<com.yg.tokenvault.data.DavBackup> = emptyList(),
    onDavRefreshHistory: ((List<com.yg.tokenvault.data.DavBackup>) -> Unit) -> Unit = {},
    onDavRestoreNamed: (DavCfg, String, String?, (Boolean, String) -> Unit) -> Unit = { _, _, _, _ -> },
    onToast: (String) -> Unit = {}
) {
    val pal = LocalPalette.current
    val clipboard = LocalClipboardManager.current

    var showPinDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDav by remember { mutableStateOf(false) }

    // 文件形式的备份：导出先攒好文本再弹系统「保存到」，导入读文件后弹密码框
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickBg(uri)
    }

    // ── 关于 / 更新 ────────────────────────────
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val curVer = remember { Updater.currentVersion(ctx) }

    var updBusy by remember { mutableStateOf(false) }
    var updMsg by remember { mutableStateOf<String?>(null) }
    var updErr by remember { mutableStateOf(false) }
    var updTarget by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdDialog by remember { mutableStateOf(false) }
    var dlProgress by remember { mutableStateOf(-1f) }
    var dlText by remember { mutableStateOf("") }
    var readyApk by remember { mutableStateOf<File?>(null) }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // 导出：把加密文本写进用户选的文件（SAF，不需要存储权限）
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("打不开输出流")
        }.isSuccess
        onToast(if (ok) "备份已保存成文件" else "写入文件失败")
    }

    // 导入：先从文件读文本，再弹密码框
    val loadBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            onToast("文件里没有内容")
        } else {
            pendingImport = text
            showImportDialog = true
        }
    }

    val doCheck: () -> Unit = {
        if (!updBusy) {
            updBusy = true
            updMsg = null
            updErr = false
            scope.launch {
                when (val r = Updater.check(ctx)) {
                    is CheckResult.Newer -> {
                        updTarget = r.info
                        updMsg = "发现新版本 v${r.info.version}（来自 ${r.info.source}）"
                        readyApk = null
                        dlProgress = -1f
                        dlText = ""
                        showUpdDialog = true
                    }
                    is CheckResult.Latest -> updMsg = "已是最新版本 v${r.current}（来源 ${r.source}）"
                    is CheckResult.Failed -> {
                        updMsg = "检查失败：${r.message}"
                        updErr = true
                    }
                }
                updBusy = false
            }
        }
    }

    val doDownload: (UpdateInfo) -> Unit = { info ->
        if (dlProgress < 0f || readyApk != null) {
            readyApk = null
            dlProgress = 0f
            dlText = "正在连接下载源…"
            scope.launch {
                val res = Updater.download(ctx, info) { read, total ->
                    mainHandler.post {
                        dlProgress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else -1f
                        dlText = if (total > 0) {
                            "${read / 1048576} / ${total / 1048576} MB"
                        } else {
                            "${read / 1024} KB"
                        }
                    }
                }
                res.onSuccess { f ->
                    readyApk = f
                    dlProgress = 1f
                    dlText = "已下载完成（${f.length() / 1024 / 1024} MB）"
                }.onFailure { e ->
                    dlProgress = -1f
                    dlText = ""
                    updMsg = "下载失败：${e.message}"
                    updErr = true
                }
            }
        }
    }

    val doInstall: () -> Unit = {
        val f = readyApk
        if (f != null) {
            if (!Updater.canInstall(ctx)) {
                Updater.openInstallPermissionSettings(ctx)
                updMsg = "请在系统设置里允许「安装未知应用」，回到这里再点一次安装"
                updErr = false
            } else {
                Updater.install(ctx, f)
                showUpdDialog = false
            }
        }
    }

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
            Divider()
            ActionRow(
                title = "自定义背景",
                subtitle = if (hasBg) "已设置 · 点击换一张" else "挑一张本地图片当背景",
                icon = Icons.Outlined.Image,
                onClick = {
                    pickBg.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
            if (hasBg) {
                Divider()
                ActionRow(
                    title = "恢复默认背景",
                    subtitle = "移除已设置的背景图",
                    icon = Icons.Outlined.Close,
                    onClick = onClearBg
                )
            }
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
                subtitle = "存成 .json 文件，放网盘、发电脑都行",
                icon = Icons.Outlined.Upload,
                onClick = { showExportDialog = true }
            )
            Divider()
            ActionRow(
                title = "导入加密备份",
                subtitle = "选一个备份文件，输入当时设的密码",
                icon = Icons.Outlined.Download,
                onClick = { loadBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            )
            Divider()
            ActionRow(
                title = "从剪贴板导入",
                subtitle = "备份内容在剪贴板里时用这个",
                icon = Icons.Outlined.ContentPaste,
                onClick = { pendingImport = null; showImportDialog = true }
            )
            Divider()
            ActionRow(
                title = "WebDAV 云备份",
                subtitle = when {
                    davCfg.configured && davCfg.auto -> "已连接 · 自动备份开 · ${davCfg.url}"
                    davCfg.configured -> "已连接 · ${davCfg.url}"
                    else -> "同步到坚果云 / Nextcloud / Alist"
                },
                icon = Icons.Outlined.Cloud,
                onClick = { showDav = true }
            )
        }

        Spacer(Modifier.height(14.dp))
        GroupLabel("关于")
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "钥匣 KeyCoffer",
                        style = MaterialTheme.typography.titleMedium,
                        color = pal.text
                    )
                    Text(
                        text = "版本 $curVer · 数据纯本地",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.text3
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "所有密钥经 PBKDF2-SHA256（25 万次迭代）派生的 AES-256-GCM 密钥加密后保存在本机，" +
                    "验证码全程离线计算。只有你主动「检查更新」或使用内置浏览器登录时才会联网。",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = pal.text2
            )
            Divider()
            ActionRow(
                title = "GitHub 仓库",
                subtitle = "源码、更新日志与安装包都在这里",
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openUrl(Updater.REPO_URL) }
            )
            Divider()
            ActionRow(
                title = if (updBusy) "正在检查…" else "检查更新",
                subtitle = updMsg ?: "当前 v$curVer · 自动切换可用镜像",
                icon = Icons.Outlined.SystemUpdate,
                onClick = { doCheck() }
            )
            if (updMsg != null && updErr) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = updMsg!!,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 17.sp),
                    color = pal.danger
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "下载源会依次尝试 GitHub、gh-proxy、ghfast、ghproxy，哪个通用哪个。",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = pal.text3
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    // ── WebDAV 云备份 ───────────────────────────
    if (showDav) {
        DavSheet(
            cfg0 = davCfg,
            onDismiss = { showDav = false },
            onSave = onDavSave,
            onTest = onDavTest,
            onUpload = onDavUpload,
            onRestore = onDavRestore,
            history = davHistory,
            onRefreshHistory = { onDavRefreshHistory { } },
            onRestoreNamed = { name, pin, done -> onDavRestoreNamed(davCfg, name, pin, done) }
        )
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

    // ── 导出：设密码 → 系统「保存到」 ─────────────
    if (showExportDialog) {
        SecretDialog(
            title = "设置备份密码",
            hint = "备份文件用这个密码加密，导入时需要它",
            confirmLabel = "生成并保存",
            onDismiss = { showExportDialog = false },
            onConfirm = { pw ->
                val text = onExport(pw)
                showExportDialog = false
                if (text == null) {
                    onToast("生成备份失败")
                } else {
                    pendingExport = text
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(java.util.Date())
                    saveBackup.launch("KeyCoffer-$stamp.json")
                }
            }
        )
    }

    // ── 导入：文件里读到的文本，或剪贴板 ──────────
    if (showImportDialog) {
        val fromFile = pendingImport != null
        SecretDialog(
            title = "输入备份密码",
            hint = if (fromFile) "输入导出这份备份时设的密码" else "将从剪贴板读取备份内容",
            confirmLabel = "导入",
            onDismiss = { showImportDialog = false; pendingImport = null },
            onConfirm = { pw ->
                val src = pendingImport ?: clipboard.getText()?.text
                showImportDialog = false
                pendingImport = null
                if (!src.isNullOrBlank()) onImport(src, pw) else onToast("没有读到备份内容")
            }
        )
    }

    // ── 发现新版本 / 下载安装 ────────────────────
    val target = updTarget
    if (showUpdDialog && target != null) {
        UpdateDialog(
            info = target,
            curVer = curVer,
            progress = dlProgress,
            progressText = dlText,
            ready = readyApk != null,
            onDownload = { doDownload(target) },
            onInstall = { doInstall() },
            onDismiss = { showUpdDialog = false }
        )
    }
}

// ── 更新对话框 ──────────────────────────────────

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    curVer: String,
    progress: Float,
    progressText: String,
    ready: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val pal = LocalPalette.current
    val downloading = progress >= 0f && !ready

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = pal.surface,
        titleContentColor = pal.text,
        textContentColor = pal.text2,
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "当前 v$curVer → 最新 v${info.version}" +
                        if (info.apkSize > 0) " · 安装包 ${info.apkSize / 1048576} MB" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.text3
                )

                if (info.notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    info.notes.take(6).forEach { line ->
                        Text(
                            text = "· ${line.removePrefix("-").removePrefix("*").trim()}",
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                            color = pal.text2,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                if (progress >= 0f) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = pal.accent,
                        trackColor = pal.surfaceHi
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                        color = pal.text3
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "下载源会依次尝试 GitHub / gh-proxy / ghfast / ghproxy，哪个通用哪个。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = pal.text3
                )
            }
        },
        confirmButton = {
            if (ready) {
                TextButton(onClick = onInstall) {
                    Text("安装", color = pal.accent, fontWeight = FontWeight.SemiBold)
                }
            } else {
                TextButton(onClick = onDownload, enabled = !downloading) {
                    Text(
                        text = if (downloading) "下载中…" else "立即更新",
                        color = if (downloading) pal.text3 else pal.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) {
                Text(if (downloading) "请稍候" else "以后再说", color = pal.text3)
            }
        }
    )
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