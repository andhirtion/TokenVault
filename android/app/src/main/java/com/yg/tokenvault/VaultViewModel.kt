package com.yg.tokenvault

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.tokenvault.data.DavBackup
import com.yg.tokenvault.data.DavCfg
import com.yg.tokenvault.data.DavStore
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.OtpImporter
import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.data.Totp
import com.yg.tokenvault.data.VaultRepository
import com.yg.tokenvault.data.WebDavClient
import com.yg.tokenvault.ui.theme.ThemeMode
import com.yg.tokenvault.util.IconStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全部界面状态的唯一来源。
 *
 * 密钥留在 [VaultRepository] 里，ViewModel 只负责状态流转与设置项。
 */
class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VaultRepository(app)
    private val settings = app.getSharedPreferences("tokenvault_settings", Context.MODE_PRIVATE)

    enum class Tab { HOME, VAULT, TOOLS, SETTINGS }

    data class State(
        val hasVault: Boolean = false,
        val unlocked: Boolean = false,
        val entries: List<TokenEntry> = emptyList(),
        val query: String = "",
        val filter: String? = null,
        val tab: Tab = Tab.HOME,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val hideCode: Boolean = false,
        val showRing: Boolean = true,
        val autoLockMin: Int = 3,
        val clipClearSec: Int = 30,
        val busy: Boolean = false,
        /** 是否设置了自定义背景 */
        val hasBg: Boolean = false,
        /** 背景变更计数：变化时主题层重新加载背景图 */
        val bgTick: Int = 0
    ) {
        val filtered: List<TokenEntry>
            get() {
                var list = entries
                filter?.let { f ->
                    list = when (f) {
                        FILTER_FAV -> list.filter { it.favorite }
                        FILTER_LOGIN -> list.filter { it.type == EntryType.LOGIN }
                        FILTER_CODE -> list.filter { it.type == EntryType.CODE }
                        FILTER_NOTE -> list.filter { it.type == EntryType.NOTE }
                        else -> list.filter { it.platformKey == f }
                    }
                }
                val q = query.trim()
                if (q.isNotEmpty()) {
                    list = list.filter {
                        it.name.contains(q, true) ||
                            it.account.contains(q, true) ||
                            it.platform.label.contains(q, true) ||
                            it.note.contains(q, true)
                    }
                }
                return list
            }

        /** 出现过令牌的平台，用于顶部筛选 chips */
        val usedPlatforms: List<String>
            get() = entries.map { it.platformKey }.distinct()
    }

    private val _state = MutableStateFlow(
        State(
            hasVault = repo.hasVault(),
            themeMode = loadTheme(),
            hideCode = settings.getBoolean(K_HIDE, false),
            showRing = settings.getBoolean(K_RING, true),
            autoLockMin = settings.getInt(K_AUTOLOCK, 3),
            clipClearSec = settings.getInt(K_CLIP, 30),
            hasBg = IconStore.hasBackground(app)
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** 单独抽出来，避免每秒刷新整棵状态树 */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var ticker: Job? = null

    /**
     * 仅在已解锁会话内保留 PIN 明文，用于把 PIN 封进生物识别安全区。
     * 锁屏即清空，不落盘、不进状态树。
     */
    private var sessionPin: String? = null

    fun currentPin(): String? = sessionPin

    // ── 解锁流程 ──────────────────────────────────────────────

    fun createVault(pin: String): Boolean {
        val ok = repo.create(pin.toCharArray())
        if (ok) {
            sessionPin = pin
            _state.value = _state.value.copy(hasVault = true, unlocked = true, entries = emptyList())
            startTicker()
            toast("保险箱已创建")
        } else {
            toast("创建失败，PIN 至少 4 位")
        }
        return ok
    }

    fun unlock(pin: String): Boolean {
        val ok = repo.unlock(pin.toCharArray())
        if (ok) {
            sessionPin = pin
            _state.value = _state.value.copy(
                hasVault = true,
                unlocked = true,
                entries = repo.all()
            )
            startTicker()
        }
        return ok
    }

    fun lock() {
        repo.lock()
        sessionPin = null
        ticker?.cancel()
        ticker = null
        _state.value = _state.value.copy(unlocked = false, entries = emptyList(), query = "", filter = null, tab = Tab.HOME)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(500)
            }
        }
    }

    // ── 增删改 ───────────────────────────────────────────────

    fun saveEntry(entry: TokenEntry) {
        repo.upsert(entry)
        refresh()
        toast("已保存")
        autoDavBackup()
    }

    fun deleteEntry(id: String) {
        repo.delete(id)
        refresh()
        toast("已删除")
        autoDavBackup()
    }

    fun toggleFavorite(id: String) {
        repo.toggleFavorite(id)
        refresh()
    }

    fun byId(id: String): TokenEntry? = repo.byId(id)

    private fun refresh() {
        _state.value = _state.value.copy(entries = repo.all())
    }

    // ── 搜索 / 筛选 / Tab ────────────────────────────────────

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun setFilter(key: String?) {
        _state.value = _state.value.copy(filter = key)
    }

    fun setTab(tab: Tab) {
        _state.value = _state.value.copy(tab = tab)
    }

    // ── 设置项 ───────────────────────────────────────────────

    fun setTheme(mode: ThemeMode) {
        settings.edit().putString(K_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setHideCode(v: Boolean) {
        settings.edit().putBoolean(K_HIDE, v).apply()
        _state.value = _state.value.copy(hideCode = v)
    }

    fun setShowRing(v: Boolean) {
        settings.edit().putBoolean(K_RING, v).apply()
        _state.value = _state.value.copy(showRing = v)
    }

    fun setAutoLock(min: Int) {
        settings.edit().putInt(K_AUTOLOCK, min).apply()
        _state.value = _state.value.copy(autoLockMin = min)
    }

    fun setClipClear(sec: Int) {
        settings.edit().putInt(K_CLIP, sec).apply()
        _state.value = _state.value.copy(clipClearSec = sec)
    }

    fun changePin(old: String, new: String): Boolean {
        val ok = repo.changePin(old.toCharArray(), new.toCharArray())
        if (ok) sessionPin = new
        toast(if (ok) "PIN 已更新" else "原 PIN 不正确")
        return ok
    }

    // ── 自定义背景 ───────────────────────────────────────────

    fun setBackground(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                IconStore.saveBackground(getApplication(), uri)
            }
            if (ok) {
                _state.value = _state.value.copy(
                    hasBg = true,
                    bgTick = _state.value.bgTick + 1
                )
                toast("背景已更新")
            } else {
                toast("这张图片读不出来，换一张")
            }
        }
    }

    fun clearBackground() {
        IconStore.clearBackground(getApplication())
        _state.value = _state.value.copy(hasBg = false, bgTick = _state.value.bgTick + 1)
        toast("已恢复默认背景")
    }

    // ── WebDAV 云备份 ────────────────────────────────────────

    fun davCfg(): DavCfg = DavStore.load(getApplication())

    fun davSave(cfg: DavCfg) = DavStore.save(getApplication(), cfg)

    /** 测连通：[done] 在主线程回调 (成功?, 文案) */
    fun davTest(cfg: DavCfg, done: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (ok, msg) = when (val out = withContext(Dispatchers.IO) { WebDavClient.test(cfg) }) {
                is WebDavClient.Out.Ok -> true to out.text
                is WebDavClient.Out.Err -> false to out.msg
            }
            done(ok, msg)
        }
    }

    /** 立即备份到云端。[pin] 为空则用当前解锁用的 PIN 作备份密码 */
    fun davUpload(cfg: DavCfg, pin: String?, done: (Boolean, String) -> Unit) {
        val usePin = pin?.takeIf { it.length >= 4 } ?: currentPin() ?: ""
        if (usePin.length < 4) {
            done(false, "备份密码至少 4 位（留空则用解锁 PIN）")
            return
        }
        viewModelScope.launch {
            val json = repo.exportEncrypted(usePin.toCharArray())
            if (json == null) {
                done(false, "生成备份内容失败")
                return@launch
            }
            val out = withContext(Dispatchers.IO) { WebDavClient.upload(cfg, json) }
            val msg = when (out) {
                is WebDavClient.Out.Ok -> out.text
                is WebDavClient.Out.Err -> out.msg
            }
            if (out is WebDavClient.Out.Ok) {
                val now = System.currentTimeMillis()
                DavStore.mark(getApplication(), now, msg)
                DavStore.addHistory(getApplication(), DavBackup(out.savedAs, now, json.length.toLong(), out.savedAs == "backup.json"))
            }
            done(out is WebDavClient.Out.Ok, msg)
        }
    }

    /** 本地记账的历史（不联网，主线程安全） */
    fun davLocalHistory(): List<DavBackup> = DavStore.history(getApplication())

    /** 列云端可恢复的备份：远端列不出来就用本地记账兜底 */
    fun davList(cfg: DavCfg, done: (List<DavBackup>) -> Unit) {
        viewModelScope.launch {
            val local = DavStore.history(getApplication())
            val remote = withContext(Dispatchers.IO) {
                if (cfg.configured) WebDavClient.list(cfg) else emptyList()
            }
            // 远端列表里带真实修改时间，优先用它；再补上只在本地记过的名字
            val merged = (remote + local.filter { l -> remote.none { it.name == l.name } })
                .sortedWith(compareByDescending<DavBackup> { it.main }.thenByDescending { it.at })
            done(merged)
        }
    }

    /** 从云端恢复指定那一次（[name] 为文件名） */
    fun davRestoreNamed(cfg: DavCfg, name: String, pin: String?, done: (Boolean, String) -> Unit) {
        val usePin = pin?.takeIf { it.length >= 4 } ?: currentPin() ?: ""
        if (usePin.length < 4) {
            done(false, "备份密码至少 4 位（留空则用解锁 PIN）")
            return
        }
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { WebDavClient.restoreNamed(cfg, name) }
            if (out is WebDavClient.Out.Err) {
                done(false, out.msg)
                return@launch
            }
            val text = (out as WebDavClient.Out.Ok).text
            val list = repo.importEncrypted(text, usePin.toCharArray())
            if (list == null) {
                done(false, "密码不对或那份备份已损坏")
                return@launch
            }
            val added = repo.mergeAll(list)
            refresh()
            done(true, "已恢复 $name（$added 条）")
        }
    }

    /** 从云端恢复（合并进现有库，不覆盖已有条目） */
    fun davRestore(cfg: DavCfg, pin: String?, done: (Boolean, String) -> Unit) {
        val usePin = pin?.takeIf { it.length >= 4 } ?: currentPin() ?: ""
        if (usePin.length < 4) {
            done(false, "备份密码至少 4 位（留空则用解锁 PIN）")
            return
        }
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { WebDavClient.download(cfg) }
            if (out is WebDavClient.Out.Err) {
                done(false, out.msg)
                return@launch
            }
            val text = (out as WebDavClient.Out.Ok).text
            val list = repo.importEncrypted(text, usePin.toCharArray())
            if (list == null) {
                done(false, "密码不对或云端文件已损坏")
                return@launch
            }
            val added = repo.mergeAll(list)
            refresh()
            done(true, "已从云端恢复 $added 条")
        }
    }

    /** 自动备份：改动后等 8 秒再传，连续编辑只上传最后一份 */
    private var davJob: Job? = null

    private fun autoDavBackup() {
        val cfg = DavStore.load(getApplication())
        if (!cfg.configured || !cfg.auto) return
        val pin = currentPin() ?: return
        davJob?.cancel()
        davJob = viewModelScope.launch {
            delay(8000)
            if (!isActive) return@launch
            val json = repo.exportEncrypted(pin.toCharArray()) ?: return@launch
            val out = withContext(Dispatchers.IO) { WebDavClient.upload(cfg, json) }
            val msg = when (out) {
                is WebDavClient.Out.Ok -> out.text
                is WebDavClient.Out.Err -> out.msg
            }
            val now = System.currentTimeMillis()
            DavStore.mark(getApplication(), now, msg)
            if (out is WebDavClient.Out.Ok) {
                DavStore.addHistory(getApplication(), DavBackup(out.savedAs, now, json.length.toLong(), out.savedAs == "backup.json"))
            }
        }
    }

    // ── 导入 / 导出 ──────────────────────────────────────────

    fun exportBackup(pin: String): String? {
        val text = repo.exportEncrypted(pin.toCharArray())
        if (text == null) toast("导出失败")
        return text
    }

    fun importBackup(text: String, pin: String) {
        val list = repo.importEncrypted(text, pin.toCharArray())
        if (list == null) {
            toast("导入失败：密码错误或文件损坏")
            return
        }
        val added = repo.mergeAll(list)
        refresh()
        toast("已导入 $added 条")
    }

    fun importOtpAuthText(text: String) {
        val list = repo.parseOtpAuthList(text)
        if (list.isEmpty()) {
            toast("没找到 otpauth:// 链接")
            return
        }
        val added = repo.mergeAll(list)
        refresh()
        toast("已导入 $added 条")
    }

    /**
     * 从扫码 / 网页抓取 / 相册图片 / 剪贴板拿到的一批密钥入库。
     * 返回真正新增的条数（已存在的会被 mergeAll 去重）。
     */
    fun importItems(items: List<OtpImporter.Item>): Int {
        if (!_state.value.unlocked) {
            toast("请先解锁再导入")
            return 0
        }
        if (items.isEmpty()) {
            toast("没识别到有效密钥")
            return 0
        }
        val entries = items.map { OtpImporter.toEntry(it.parsed) }
        val added = repo.mergeAll(entries)
        refresh()
        toast(
            when {
                added == 0 -> "这些密钥已经在库里了"
                entries.size == 1 -> "已添加「${entries.first().name.ifBlank { "新令牌" }}」"
                else -> "已添加 $added 条密钥"
            }
        )
        return added
    }

    /** 把任意文本解析后入库，origin 只影响提示文案 */
    fun importAnyText(
        text: String,
        origin: OtpImporter.Origin = OtpImporter.Origin.LINK
    ): Int = importItems(OtpImporter.parse(text, origin))

    // ── 工具 ─────────────────────────────────────────────────

    fun generateSecret(length: Int = 20): String =
        com.yg.tokenvault.util.Base32.randomSecret(length)

    /** 生成随机登录密码：大小写字母 + 数字 + 符号，至少各一个 */
    fun generatePassword(length: Int = 20): String =
        com.yg.tokenvault.util.PasswordGen.random(length)

    fun previewEntry(): TokenEntry = TokenEntry(
        name = "",
        platformKey = "custom",
        type = EntryType.CODE,
        digits = 6,
        period = 30,
        algo = "SHA1",
        charset = "NUM"
    )

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun loadTheme(): ThemeMode = runCatching {
        ThemeMode.valueOf(settings.getString(K_THEME, ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM)

    companion object {
        const val FILTER_FAV = "__fav__"
        const val FILTER_LOGIN = "__login__"
        const val FILTER_CODE = "__code__"
        const val FILTER_NOTE = "__note__"
        private const val K_THEME = "theme"
        private const val K_HIDE = "hideCode"
        private const val K_RING = "showRing"
        private const val K_AUTOLOCK = "autoLockMin"
        private const val K_CLIP = "clipClearSec"
    }
}