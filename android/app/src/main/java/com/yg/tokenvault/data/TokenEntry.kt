package com.yg.tokenvault.data

import java.util.UUID

/** 令牌类型：TOTP 动态码，或纯文本密钥笔记 */
enum class EntryType { CODE, NOTE }

/**
 * 一条令牌。
 *
 * secret 在仓库层以 AES-256-GCM 加密后落盘；内存中保持明文，退出时随进程消失。
 */
data class TokenEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: EntryType = EntryType.CODE,
    val platformKey: String = "custom",
    val name: String = "",
    val account: String = "",
    val secret: String = "",
    val digits: Int = 6,
    val period: Int = 30,
    val algo: String = "SHA1",
    val charset: String = "NUM",
    val note: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    val platform: Platform get() = Platforms.of(platformKey)

    /** 当前验证码；无密钥或密钥非法时为 null */
    fun currentCode(nowMs: Long = System.currentTimeMillis()): Totp.Result? =
        if (type != EntryType.CODE) null
        else Totp.generate(
            secret = secret,
            timestampMs = nowMs,
            digits = digits,
            period = period,
            algo = algo,
            charset = if (platformKey == "steam") "STEAM" else charset
        )

    /** 按平台习惯分组的验证码文本 */
    fun displayCode(nowMs: Long = System.currentTimeMillis()): String =
        currentCode(nowMs)?.let { Totp.format(it.code) } ?: ""
}