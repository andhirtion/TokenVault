package com.yg.tokenvault.data

import android.content.Context
import android.content.SharedPreferences
import com.yg.tokenvault.security.VaultCrypto
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

/**
 * 保险库仓库：负责加密落盘、解锁校验、增删改查与导入导出。
 *
 * 落盘结构（SharedPreferences）：
 *   salt       Base64(32B)          —— PBKDF2 盐，创建库时生成一次
 *   payload    Base64(iv + 密文)     —— AES-256-GCM 加密后的 JSON 数组
 *
 * 内存中的密钥只在解锁后存在，[lock] 会清空。
 */
class VaultRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private var key: SecretKey? = null
    private var cache: MutableList<TokenEntry> = mutableListOf()

    val isUnlocked: Boolean get() = key != null

    /** 是否已创建过保险库（决定首启是「设置密码」还是「输入密码」） */
    fun hasVault(): Boolean = prefs.contains(K_SALT) && prefs.contains(K_PAYLOAD)

    // ── 解锁 / 创建 ────────────────────────────────────────────

    fun create(pin: CharArray): Boolean {
        if (pin.size < 4) return false
        val salt = VaultCrypto.newSalt()
        val k = VaultCrypto.deriveKey(pin, salt)
        key = k
        cache = mutableListOf()
        prefs.edit().putString(K_SALT, VaultCrypto.b64(salt)).apply()
        return persist()
    }

    fun unlock(pin: CharArray): Boolean {
        val saltText = prefs.getString(K_SALT, null) ?: return false
        val payload = prefs.getString(K_PAYLOAD, null) ?: return false
        val salt = VaultCrypto.unb64(saltText) ?: return false
        val blob = VaultCrypto.unb64(payload) ?: return false
        if (blob.size <= VaultCrypto.IV_BYTES) return false

        val k = VaultCrypto.deriveKey(pin, salt)
        val iv = blob.copyOfRange(0, VaultCrypto.IV_BYTES)
        val body = blob.copyOfRange(VaultCrypto.IV_BYTES, blob.size)
        val plain = VaultCrypto.decrypt(k, iv, body) ?: return false

        val parsed = runCatching { decodeEntries(String(plain, Charsets.UTF_8)) }.getOrNull()
            ?: return false

        key = k
        cache = parsed.toMutableList()
        return true
    }

    fun lock() {
        key = null
        cache = mutableListOf()
    }

    /** 改密码：用旧密钥解出来的数据，改用新密钥重新加密 */
    fun changePin(oldPin: CharArray, newPin: CharArray): Boolean {
        if (!unlock(oldPin)) return false
        val salt = VaultCrypto.newSalt()
        val k = VaultCrypto.deriveKey(newPin, salt)
        key = k
        prefs.edit().putString(K_SALT, VaultCrypto.b64(salt)).apply()
        return persist()
    }

    // ── 读写 ───────────────────────────────────────────────────

    fun all(): List<TokenEntry> = cache.sortedWith(
        compareByDescending<TokenEntry> { it.favorite }.thenBy { it.sortOrder }.thenBy { it.createdAt }
    )

    fun byId(id: String): TokenEntry? = cache.firstOrNull { it.id == id }

    fun upsert(entry: TokenEntry): Boolean {
        val i = cache.indexOfFirst { it.id == entry.id }
        if (i >= 0) cache[i] = entry else cache.add(entry.copy(sortOrder = cache.size))
        return persist()
    }

    fun delete(id: String): Boolean {
        if (cache.removeAll { it.id == id }) return persist()
        return false
    }

    fun toggleFavorite(id: String): Boolean {
        val i = cache.indexOfFirst { it.id == id }
        if (i < 0) return false
        cache[i] = cache[i].copy(favorite = !cache[i].favorite)
        return persist()
    }

    fun mergeAll(incoming: List<TokenEntry>): Int {
        var added = 0
        for (e in incoming) {
            // 同平台+同账号+同密钥视为重复
            val dup = cache.any {
                it.secret == e.secret && it.account == e.account && it.platformKey == e.platformKey
            }
            if (!dup) {
                cache.add(e.copy(sortOrder = cache.size))
                added++
            }
        }
        if (added > 0) persist()
        return added
    }

    private fun persist(): Boolean {
        val k = key ?: return false
        val json = encodeEntries(cache)
        val sealed = VaultCrypto.encrypt(k, json.toByteArray(Charsets.UTF_8))
        val blob = sealed.iv + sealed.cipherText
        prefs.edit().putString(K_PAYLOAD, VaultCrypto.b64(blob)).apply()
        return true
    }

    // ── 序列化 ────────────────────────────────────────────────

    private fun encodeEntries(list: List<TokenEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("type", e.type.name)
                put("platform", e.platformKey)
                put("name", e.name)
                put("account", e.account)
                put("secret", e.secret)
                put("digits", e.digits)
                put("period", e.period)
                put("algo", e.algo)
                put("charset", e.charset)
                put("note", e.note)
                put("favorite", e.favorite)
                put("createdAt", e.createdAt)
                put("sortOrder", e.sortOrder)
            })
        }
        return arr.toString()
    }

    private fun decodeEntries(text: String): List<TokenEntry> {
        if (text.isBlank()) return emptyList()
        val arr = JSONArray(text)
        val out = ArrayList<TokenEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                TokenEntry(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    type = runCatching { EntryType.valueOf(o.optString("type", "CODE")) }
                        .getOrDefault(EntryType.CODE),
                    platformKey = o.optString("platform", "custom"),
                    name = o.optString("name"),
                    account = o.optString("account"),
                    secret = o.optString("secret"),
                    digits = o.optInt("digits", 6),
                    period = o.optInt("period", 30),
                    algo = o.optString("algo", "SHA1"),
                    charset = o.optString("charset", "NUM"),
                    note = o.optString("note"),
                    favorite = o.optBoolean("favorite", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    sortOrder = o.optInt("sortOrder", i)
                )
            )
        }
        return out
    }

    // ── 导出 / 导入 ───────────────────────────────────────────

    /** 导出为「不含明文密钥」的加密备份（独立密码，可与主密码不同） */
    fun exportEncrypted(backupPin: CharArray): String? {
        val salt = VaultCrypto.newSalt()
        val k = VaultCrypto.deriveKey(backupPin, salt)
        val sealed = VaultCrypto.encrypt(k, encodeEntries(cache).toByteArray(Charsets.UTF_8))
        val root = JSONObject().apply {
            put("app", "TokenVault")
            put("version", 1)
            put("salt", VaultCrypto.b64(salt))
            put("iv", VaultCrypto.b64(sealed.iv))
            put("data", VaultCrypto.b64(sealed.cipherText))
        }
        return root.toString(2)
    }

    fun importEncrypted(json: String, backupPin: CharArray): List<TokenEntry>? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("app") != "TokenVault") return null
        val salt = VaultCrypto.unb64(root.optString("salt")) ?: return null
        val iv = VaultCrypto.unb64(root.optString("iv")) ?: return null
        val data = VaultCrypto.unb64(root.optString("data")) ?: return null
        val key = runCatching { VaultCrypto.deriveKey(backupPin, salt) }.getOrNull() ?: return null
        val plain = VaultCrypto.decrypt(key, iv, data) ?: return null
        return runCatching { decodeEntries(String(plain, Charsets.UTF_8)) }.getOrNull()
    }

    /** 解析纯文本：每行一条 otpauth:// 链接 */
    fun parseOtpAuthList(text: String): List<TokenEntry> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("otpauth://", ignoreCase = true) }
            .mapNotNull { uri ->
                Totp.parseOtpAuth(uri)?.let { p ->
                    TokenEntry(
                        platformKey = p.platformKey,
                        name = p.label.ifBlank { Platforms.of(p.platformKey).label },
                        account = p.account,
                        secret = p.secret,
                        digits = p.digits,
                        period = p.period,
                        algo = p.algo,
                        charset = p.charsetHint
                    )
                }
            }
            .toList()
    }

    companion object {
        private const val PREF = "tokenvault_store_v1"
        private const val K_SALT = "salt"
        private const val K_PAYLOAD = "payload"
    }
}