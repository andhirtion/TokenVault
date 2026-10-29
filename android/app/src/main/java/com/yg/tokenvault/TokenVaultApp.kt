package com.yg.tokenvault

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yg.tokenvault.data.OtpImporter
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.ui.components.AppLogo
import com.yg.tokenvault.ui.screens.DetailSheet
import com.yg.tokenvault.ui.screens.EditorSheet
import com.yg.tokenvault.ui.screens.ImportSheet
import com.yg.tokenvault.ui.screens.LockScreen
import com.yg.tokenvault.ui.screens.LoginScreen
import com.yg.tokenvault.security.BiometricVault
import com.yg.tokenvault.ui.screens.ScanScreen
import com.yg.tokenvault.ui.screens.SettingsScreen
import com.yg.tokenvault.ui.screens.ToolsScreen
import com.yg.tokenvault.ui.screens.VaultScreen
import com.yg.tokenvault.ui.screens.androidClickable
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.util.QrDecoder
import com.yg.tokenvault.ui.theme.TokenVaultTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val NEW_ENTRY = "__new__"

/**
 * 应用根：锁屏 → 主界面（三个 tab）+ 详情 / 编辑弹层 + 轻提示。
 */
@Composable
fun TokenVaultApp(vm: VaultViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    var detailId by remember { mutableStateOf<String?>(null) }
    var editorId by remember { mutableStateOf<String?>(null) }
    var toastText by remember { mutableStateOf<String?>(null) }

    // 获取密钥链路
    var showImport by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }

    // 从相册选二维码图片（免权限）
    val appContext = LocalContext.current
    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                QrDecoder.fromBitmap(BitmapFactory.decodeStream(input))
            }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            vm.toast("这张图片里没识别到二维码")
        } else {
            showScan = false
            vm.importAnyText(text, OtpImporter.Origin.QR_CODE)
        }
    }
    val pickAlbum: () -> Unit = {
        albumLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // 指纹解锁状态
    val hostActivity = appContext as? androidx.fragment.app.FragmentActivity
    var bioOn by remember { mutableStateOf(false) }
    var bioOk by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.unlocked, state.hasVault) {
        bioOk = BiometricVault.canUse(appContext)
        bioOn = BiometricVault.isEnabled(appContext)
    }

    // 消息流 → 轻提示
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            toastText = msg
        }
    }
    androidx.compose.runtime.LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(2000)
            toastText = null
        }
    }

    // 自动锁定：从后台回到前台时按设置判断
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.autoLockMin) {
        var stoppedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val limit = state.autoLockMin
                    if (limit > 0 && stoppedAt > 0 &&
                        System.currentTimeMillis() - stoppedAt > limit * 60_000L
                    ) {
                        vm.lock()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TokenVaultTheme(mode = state.themeMode) {
        val pal = LocalPalette.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pal.bg)
        ) {
            if (!state.unlocked) {
                LockScreen(
                    hasVault = state.hasVault,
                    onCreate = { pin -> vm.createVault(pin) },
                    onUnlock = { pin -> vm.unlock(pin) },
                    onBiometric = if (hostActivity != null && bioOn && bioOk) {
                        {
                            BiometricVault.unlock(
                                activity = hostActivity,
                                onPin = { pin -> if (pin != null) vm.unlock(pin) },
                                onNotice = { msg ->
                                    vm.toast(msg)
                                    bioOn = BiometricVault.isEnabled(appContext)
                                }
                            )
                        }
                    } else null
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (state.tab) {
                            VaultViewModel.Tab.VAULT -> VaultScreen(
                                state = state,
                                now = now,
                                onSearch = vm::setQuery,
                                onFilter = vm::setFilter,
                                onOpen = { id -> detailId = id },
                                onToggleFav = vm::toggleFavorite,
                                onAdd = { editorId = NEW_ENTRY },
                                onImport = { showImport = true }
                            )

                            VaultViewModel.Tab.TOOLS -> ToolsScreen(
                                onImport = { showImport = true }
                            )

                            VaultViewModel.Tab.SETTINGS -> SettingsScreen(
                                state = state,
                                onTheme = vm::setTheme,
                                onHideCode = vm::setHideCode,
                                onShowRing = vm::setShowRing,
                                onAutoLock = vm::setAutoLock,
                                onClipClear = vm::setClipClear,
                                onChangePin = { old, new ->
                                    if (vm.changePin(old, new)) {
                                        // 旧 PIN 已被封进指纹安全区，改完后作废，需重新开启
                                        BiometricVault.disable(appContext)
                                        bioOn = false
                                    }
                                },
                                onExport = { pw -> vm.exportBackup(pw) },
                                onImport = { text, pw -> vm.importBackup(text, pw) },
                                bioEnabled = bioOn,
                                bioAvailable = bioOk,
                                onToggleBio = { want ->
                                    val act = hostActivity
                                    if (want) {
                                        val pin = vm.currentPin()
                                        when {
                                            act == null -> vm.toast("当前界面无法调起指纹")
                                            pin == null -> vm.toast("请用 PIN 解锁后再开启指纹")
                                            else -> BiometricVault.enable(act, pin) { ok, msg ->
                                                if (ok) vm.toast("指纹解锁已开启")
                                                else if (msg != null) vm.toast(msg)
                                                bioOn = BiometricVault.isEnabled(appContext)
                                            }
                                        }
                                    } else {
                                        BiometricVault.disable(appContext)
                                        bioOn = false
                                        vm.toast("指纹解锁已关闭")
                                    }
                                },
                                onLock = {
                                    detailId = null
                                    editorId = null
                                    vm.lock()
                                }
                            )
                        }
                    }

                    BottomBar(
                        tab = state.tab,
                        onTab = vm::setTab
                    )
                }
            }

            // 轻提示
            AnimatedVisibility(
                visible = toastText != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(pal.surfaceHi)
                        .border(1.dp, pal.border, RoundedCornerShape(13.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = toastText ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.text
                    )
                }
            }
        }

        // ── 获取密钥：选择方式 → 扫码 / 登录 ───────────
        if (showImport) {
            ImportSheet(
                onDismiss = { showImport = false },
                onText = { text -> vm.importAnyText(text, OtpImporter.Origin.LINK) },
                onScan = { showScan = true },
                onWebLogin = { showLogin = true },
                onToast = { msg -> vm.toast(msg) }
            )
        }

        if (showScan) {
            ScanScreen(
                onDismiss = { showScan = false },
                onDecoded = { text ->
                    showScan = false
                    vm.importAnyText(text, OtpImporter.Origin.QR_CODE)
                },
                onPickAlbum = pickAlbum
            )
        }

        if (showLogin) {
            LoginScreen(
                startUrl = null,
                onDismiss = { showLogin = false },
                onFound = { items ->
                    showLogin = false
                    vm.importItems(items)
                }
            )
        }

        // ── 详情 ────────────────────────────────
        val detailEntry: TokenEntry? = detailId?.let { id -> state.entries.firstOrNull { it.id == id } }
        if (detailEntry != null) {
            DetailSheet(
                entry = detailEntry,
                now = now,
                onDismiss = { detailId = null },
                onEdit = {
                    editorId = detailEntry.id
                    detailId = null
                },
                onDelete = {
                    vm.deleteEntry(detailEntry.id)
                    detailId = null
                },
                onToggleFav = { vm.toggleFavorite(detailEntry.id) },
                onCopied = { vm.toast("验证码已复制") }
            )
        }

        // ── 编辑 / 新建 ──────────────────────────
        val editingEntry: TokenEntry? = when (editorId) {
            null -> null
            NEW_ENTRY -> TokenEntry(platformKey = "google", name = "", account = "")
            else -> state.entries.firstOrNull { it.id == editorId }
        }
        if (editingEntry != null) {
            EditorSheet(
                existing = if (editorId == NEW_ENTRY) null else editingEntry,
                onDismiss = { editorId = null },
                onSave = { entry ->
                    vm.saveEntry(entry)
                    editorId = null
                },
                onDelete = { id ->
                    vm.deleteEntry(id)
                    editorId = null
                },
                onGenerateSecret = { vm.generateSecret(20) }
            )
        }
    }
}

/**
 * 底部导航：悬浮的胶囊，三个 tab。
 */
@Composable
private fun BottomBar(
    tab: VaultViewModel.Tab,
    onTab: (VaultViewModel.Tab) -> Unit
) {
    val pal = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(pal.surface)
                .border(1.dp, pal.border, RoundedCornerShape(21.dp))
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                label = "令牌",
                icon = Icons.Filled.Key,
                selected = tab == VaultViewModel.Tab.VAULT,
                modifier = Modifier.weight(1f)
            ) { onTab(VaultViewModel.Tab.VAULT) }

            TabItem(
                label = "工具",
                icon = Icons.Filled.Build,
                selected = tab == VaultViewModel.Tab.TOOLS,
                modifier = Modifier.weight(1f)
            ) { onTab(VaultViewModel.Tab.TOOLS) }

            TabItem(
                label = "设置",
                icon = Icons.Filled.Settings,
                selected = tab == VaultViewModel.Tab.SETTINGS,
                modifier = Modifier.weight(1f)
            ) { onTab(VaultViewModel.Tab.SETTINGS) }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = LocalPalette.current
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) pal.accent.copy(alpha = 0.14f) else Color.Transparent)
            .androidClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) pal.accent else pal.text3,
                modifier = Modifier.size(17.dp)
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = pal.accent
                )
            }
        }
    }
}