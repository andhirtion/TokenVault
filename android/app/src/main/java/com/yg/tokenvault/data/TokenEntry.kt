package com.yg.tokenvault.data

import java.util.UUID

/** 条目类型：TOTP 动态码、账号密码、纯文本密钥笔记 */
enum class EntryType { CODE, LOGIN, NOTE }

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
    /** 账号密码条目的密码原文（与 secret 一样随整库 AES-GCM 加密落盘） */
    val password: String = "",
    val digits: Int = 6,
    val period: Int = 30,
    val algo: String = "SHA1",
    val charset: String = "NUM",
    val note: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    /**
     * 条目自己的图标：app 私有目录 `files/icons/` 下的文件名。
     * 空串表示用平台内置图标；自定义平台上传或联网抓取的图标都存这里。
     */
    val iconPath: String = "",
    /**
     * 抓图标用的域名，例如 `github.com`。用户手填过一次就记住，
     * 下次编辑还在，不必再从账号里猜。空串表示没填。
     */
    val iconHost: String = ""
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