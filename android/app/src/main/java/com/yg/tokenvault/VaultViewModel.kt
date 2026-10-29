package com.yg.tokenvault

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.tokenvault.data.EntryType
import com.yg.tokenvault.data.OtpImporter
import com.yg.tokenvault.data.Platforms
import com.yg.tokenvault.data.TokenEntry
import com.yg.tokenvault.data.Totp
import com.yg.tokenvault.data.VaultRepository
import com.yg.tokenvault.ui.theme.ThemeMode
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

/**
 * 全部界面状态的唯一来源。
 *
 * 密钥留在 [VaultRepository] 里，ViewModel 只负责状态流转与设置项。
 */
class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VaultRepository(app)
    private val settings = app.getSharedPreferences("tokenvault_settings", Context.MODE_PRIVATE)

    enum class Tab { VAULT, TOOLS, SETTINGS }

    data class State(
        val hasVault: Boolean = false,
        val unlocked: Boolean = false,
        val entries: List<TokenEntry> = emptyList(),
        val query: String = "",
        val filter: String? = null,
        val tab: Tab = Tab.VAULT,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val hideCode: Boolean = false,
        val showRing: Boolean = true,
        val autoLockMin: Int = 3,
        val clipClearSec: Int = 30,
        val busy: Boolean = false
    ) {
        val filtered: List<TokenEntry>
            get() {
                var list = entries
                filter?.let { f ->
                    list = if (f == FILTER_FAV) list.filter { it.favorite }
                    else list.filter { it.platformKey == f }
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
            clipClearSec = settings.getInt(K_CLIP, 30)
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
            toast("保险库已创建")
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
        _state.value = _state.value.copy(unlocked = false, entries = emptyList(), query = "", filter = null, tab = Tab.VAULT)
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
    }

    fun deleteEntry(id: String) {
        repo.delete(id)
        refresh()
        toast("已删除")
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
        private const val K_THEME = "theme"
        private const val K_HIDE = "hideCode"
        private const val K_RING = "showRing"
        private const val K_AUTOLOCK = "autoLockMin"
        private const val K_CLIP = "clipClearSec"
    }
}