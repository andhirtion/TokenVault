package com.yg.tokenvault

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.OtpImporter
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.ui.components.AppEase
import com.yg.tokenvault.ui.components.AppLogo
import com.yg.tokenvault.ui.components.tactile
import com.yg.tokenvault.ui.screens.DetailSheet
import com.yg.tokenvault.ui.screens.EditorSheet
import com.yg.tokenvault.ui.screens.HomeScreen
import com.yg.tokenvault.ui.screens.ImportSheet
import com.yg.tokenvault.ui.screens.LockScreen
import com.yg.tokenvault.ui.screens.LoginScreen
import com.yg.tokenvault.ui.screens.WebHubScreen
import com.yg.tokenvault.security.BiometricVault
import com.yg.tokenvault.ui.screens.ScanScreen
import com.yg.tokenvault.ui.screens.SettingsScreen
import com.yg.tokenvault.ui.screens.ToolsScreen
import com.yg.tokenvault.ui.screens.VaultScreen
import com.yg.tokenvault.ui.screens.androidClickable
import com.yg.tokenvault.ui.theme.LocalPalette
import com.yg.tokenvault.util.IconStore
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
    // 地球按钮先落到「网页登录」主页，选站点后再进内置浏览器
    var showWebHub by remember { mutableStateOf(false) }
    var webUrl by remember { mutableStateOf<String?>(null) }
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

    // 指纹解锁状态：首帧就同步读一次，避免"要等一轮才变成 true"导致自动弹窗被跳过
    val hostActivity = appContext as? androidx.fragment.app.FragmentActivity
    var bioOk by remember { mutableStateOf(BiometricVault.canUse(appContext)) }
    var bioOn by remember { mutableStateOf(BiometricVault.isEnabled(appContext)) }
    androidx.compose.runtime.LaunchedEffect(state.unlocked, state.hasVault) {
        bioOk = BiometricVault.canUse(appContext)
        bioOn = BiometricVault.isEnabled(appContext)
    }

    // 指纹优先：已开启指纹时，落到锁屏就直接弹系统生物识别，PIN 只作兜底
    var bioAutoAsked by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(bioOn, bioOk, state.unlocked, state.hasVault) {
        if (state.unlocked) {
            bioAutoAsked = false
            return@LaunchedEffect
        }
        if (bioAutoAsked) return@LaunchedEffect
        if (!state.hasVault || !bioOn || !bioOk) return@LaunchedEffect
        val act = hostActivity ?: return@LaunchedEffect
        bioAutoAsked = true
        // 等窗口真正拿到焦点再弹，否则系统会静默丢弃这次认证
        delay(650)
        if (state.unlocked) return@LaunchedEffect
        BiometricVault.unlock(
            activity = act,
            onPin = { pin -> if (pin != null) vm.unlock(pin) },
            onNotice = { msg ->
                vm.toast(msg)
                bioOn = BiometricVault.isEnabled(appContext)
            }
        )
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

        // 自定义背景：随 hasBg / bgTick 变化重新加载
        val bgBitmap = remember(state.hasBg, state.bgTick) {
            if (state.hasBg) IconStore.loadBackground(appContext) else null
        }

        // WebDAV 配置：保存后 +1 触发重读
        var davRev by remember { mutableStateOf(0) }
        val davNow = remember(davRev) { vm.davCfg() }
        // 可恢复的备份列表（远端 + 本地记账合并）
        var davHistory by remember { mutableStateOf<List<com.yg.tokenvault.data.DavBackup>>(emptyList()) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pal.bg)
        ) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 压一层主题底色，保证前景文字始终读得清
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pal.bg.copy(alpha = if (pal.isDark) 0.62f else 0.58f))
                )
            }

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
                        AnimatedContent(
                            targetState = state.tab,
                            transitionSpec = {
                                val forward = targetState.ordinal >= initialState.ordinal
                                val shift = if (forward) 1 else -1
                                (fadeIn(tween(230)) +
                                    slideInHorizontally(tween(280, easing = AppEase)) { it / 14 * shift })
                                    .togetherWith(
                                        fadeOut(tween(140)) +
                                            slideOutHorizontally(tween(220)) { -it / 14 * shift }
                                    )
                            },
                            label = "tabSwitch"
                        ) { tab ->
                            when (tab) {
                            VaultViewModel.Tab.HOME -> HomeScreen(
                                total = state.entries.size,
                                codes = state.entries.count { it.type == EntryType.CODE },
                                logins = state.entries.count { it.type == EntryType.LOGIN },
                                onCode = {
                                    vm.setFilter(VaultViewModel.FILTER_CODE)
                                    vm.setTab(VaultViewModel.Tab.VAULT)
                                },
                                onLogin = {
                                    vm.setFilter(VaultViewModel.FILTER_LOGIN)
                                    vm.setTab(VaultViewModel.Tab.VAULT)
                                },
                                onTools = { vm.setTab(VaultViewModel.Tab.TOOLS) },
                                onSettings = { vm.setTab(VaultViewModel.Tab.SETTINGS) }
                            )

                            VaultViewModel.Tab.VAULT -> VaultScreen(
                                state = state,
                                now = now,
                                onSearch = vm::setQuery,
                                onFilter = vm::setFilter,
                                onOpen = { id -> detailId = id },
                                onToggleFav = vm::toggleFavorite,
                                onAdd = { editorId = NEW_ENTRY },
                                onImport = { showImport = true },
                                onWebLogin = { showWebHub = true }
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
                                },
                                hasBg = state.hasBg,
                                onPickBg = { uri -> vm.setBackground(uri) },
                                onClearBg = { vm.clearBackground() },
                                davCfg = davNow,
                                onDavSave = { cfg ->
                                    vm.davSave(cfg)
                                    davRev++
                                },
                                onDavTest = { cfg, done -> vm.davTest(cfg, done) },
                                onDavUpload = { cfg, pin, done ->
                                    vm.davUpload(cfg, pin) { ok, text ->
                                        davHistory = vm.davLocalHistory()
                                        done(ok, text)
                                    }
                                },
                                onDavRestore = { cfg, pin, done -> vm.davRestore(cfg, pin, done) },
                                davHistory = davHistory,
                                onDavRefreshHistory = { cfg, cb ->
                                    // 用面板里当下的表单值去列，用户刚改完地址不用先保存也能刷出来
                                    vm.davList(cfg) { list -> davHistory = list; cb(list) }
                                },
                                onDavRestoreNamed = { cfg, name, pin, done ->
                                    vm.davRestoreNamed(cfg, name, pin) { ok, text ->
                                        davHistory = vm.davLocalHistory()
                                        done(ok, text)
                                    }
                                },
                                onToast = vm::toast
                            )
                            }
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
                enter = fadeIn(tween(160, easing = AppEase)) +
                    slideInVertically(tween(260, easing = AppEase)) { it / 3 } +
                    scaleIn(tween(260, easing = AppEase), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 4 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            16.dp,
                            RoundedCornerShape(14.dp),
                            ambientColor = pal.accent,
                            spotColor = pal.accent
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.surfaceHi)
                        .border(1.dp, pal.accent.copy(alpha = 0.26f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(pal.accent)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = toastText ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = pal.text
                        )
                    }
                }
            }
        }

        // ── 获取密钥：选择方式 → 扫码 / 图片 / 剪贴板 ───────
        if (showImport) {
            ImportSheet(
                onDismiss = { showImport = false },
                onText = { text -> vm.importAnyText(text, OtpImporter.Origin.LINK) },
                onScan = { showScan = true },
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

        if (showWebHub) {
            WebHubScreen(
                onBack = { showWebHub = false },
                onOpen = { url ->
                    webUrl = url
                    showWebHub = false
                    showLogin = true
                }
            )
        }

        if (showLogin) {
            LoginScreen(
                startUrl = webUrl,
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
                onGenerateSecret = { vm.generateSecret(20) },
                onGeneratePassword = { vm.generatePassword(20) }
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
                label = "首页",
                icon = Icons.Filled.Home,
                selected = tab == VaultViewModel.Tab.HOME,
                modifier = Modifier.weight(1f)
            ) { onTab(VaultViewModel.Tab.HOME) }

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
    val bg by animateColorAsState(
        targetValue = if (selected) pal.accent.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(220),
        label = "tabBg"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) pal.accent else pal.text3,
        animationSpec = tween(220),
        label = "tabTint"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabLift"
    )
    Box(
        modifier = modifier
            .height(46.dp)
            .tactile(onClick, scaleTo = 0.94f)
            .clip(RoundedCornerShape(16.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        scaleX = 1f + lift * 0.06f
                        scaleY = 1f + lift * 0.06f
                    }
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