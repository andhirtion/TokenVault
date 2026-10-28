package com.yg.tokenvault.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.yg.tokenvault.R

/**
 * 一个平台的显示与算法特征。
 *
 * digits / period / algo 是该平台服务端约定的参数；charset 为 STEAM 时走 Steam Guard 的
 * 5 位自定义字母表，否则按十进制取模。
 */
data class Platform(
    val key: String,
    val label: String,
    val short: String,
    @DrawableRes val icon: Int,
    val tileColor: Long,
    val fgColor: Long,
    val digits: Int = 6,
    val period: Int = 30,
    val algo: String = "SHA1",
    val charset: String = "NUM"
)

object Platforms {

    const val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

    private val ALL = listOf(
        Platform("google", "Google", "G", R.drawable.ic_brand_google, 0xFFFFFFFF, 0xFF3C4043),
        Platform("microsoft", "Microsoft", "M", R.drawable.ic_brand_microsoft, 0xFFF3F4F6, 0xFF1F2937),
        Platform("github", "GitHub", "GH", R.drawable.ic_brand_github, 0xFF1B1F24, 0xFFE6EDF3),
        Platform("steam", "Steam", "ST", R.drawable.ic_brand_steam, 0xFF16202D, 0xFFC7D5E0,
            digits = 5, period = 30, algo = "SHA1", charset = "STEAM"),
        Platform("apple", "Apple", "A", R.drawable.ic_brand_apple, 0xFF1C1C1E, 0xFFE8EBF0),
        Platform("discord", "Discord", "D", R.drawable.ic_brand_discord, 0xFF5865F2, 0xFFC9D1F0),
        Platform("x", "X", "X", R.drawable.ic_brand_x, 0xFF0B0B0C, 0xFFE8EBF0),
        Platform("facebook", "Facebook", "FB", R.drawable.ic_brand_facebook, 0xFF182131, 0xFF1877F2),
        Platform("amazon", "Amazon", "AZ", R.drawable.ic_brand_amazon, 0xFF1A1710, 0xFFFF9900),
        Platform("dropbox", "Dropbox", "DB", R.drawable.ic_brand_dropbox, 0xFF0B1B33, 0xFF0061FF),
        Platform("gitlab", "GitLab", "GL", R.drawable.ic_brand_gitlab, 0xFF221708, 0xFFFC6D26),
        Platform("linkedin", "LinkedIn", "IN", R.drawable.ic_brand_linkedin, 0xFF0A1B2A, 0xFF0A66C2),
        Platform("twitter", "Twitter", "TW", R.drawable.ic_brand_twitter, 0xFF0A1B2A, 0xFF1DA1F2),
        Platform("telegram", "Telegram", "TG", R.drawable.ic_brand_telegram, 0xFF0D1B26, 0xFF2AABEE),
        Platform("slack", "Slack", "SL", R.drawable.ic_brand_slack, 0xFF1A1226, 0xFFECB22E),
        Platform("reddit", "Reddit", "RD", R.drawable.ic_brand_reddit, 0xFF1F1108, 0xFFFF4500),
        Platform("twitch", "Twitch", "TV", R.drawable.ic_brand_twitch, 0xFF170F26, 0xFF9146FF),
        Platform("spotify", "Spotify", "SP", R.drawable.ic_brand_spotify, 0xFF08170D, 0xFF1DB954),
        Platform("binance", "Binance", "BN", R.drawable.ic_brand_binance, 0xFF1A1508, 0xFFF0B90B),
        Platform("cloudflare", "Cloudflare", "CF", R.drawable.ic_brand_cloudflare, 0xFF20150C, 0xFFF38020),
        Platform("notion", "Notion", "NO", R.drawable.ic_brand_notion, 0xFF16161A, 0xFFE8EBF0),
        Platform("proton", "Proton", "PM", R.drawable.ic_brand_proton, 0xFF150E26, 0xFF8B5CF6),
        Platform("instagram", "Instagram", "IG", R.drawable.ic_brand_instagram, 0xFF26101C, 0xFFE1306C),
        Platform("epic", "Epic Games", "EG", R.drawable.ic_brand_epic, 0xFF15161A, 0xFFE8EBF0),
        Platform("xbox", "Xbox", "XB", R.drawable.ic_brand_xbox, 0xFF0A1A0C, 0xFF107C10),
        Platform("custom", "自定义", "?", R.drawable.ic_brand_custom, 0xFF1A1B22, 0xFF8B5CF6)
    )

    private val BY_KEY = ALL.associateBy { it.key }

    /** 编辑页展示的顺序（高频平台在前） */
    val picker: List<Platform> = ALL

    fun of(key: String?): Platform = BY_KEY[key] ?: BY_KEY.getValue("custom")

    /** 根据 issuer / 标签猜测平台，用于 otpauth:// 导入 */
    fun guess(issuerOrLabel: String?): Platform {
        val s = (issuerOrLabel ?: "").lowercase()
        if (s.isBlank()) return of("custom")
        // 先精确匹配 key，再按包含关系匹配
        BY_KEY[s]?.let { return it }
        return ALL.firstOrNull { it.key != "custom" && (s.contains(it.key) || it.label.lowercase().let(s::contains)) }
            ?: when {
                s.contains("git") -> of("github")
                s.contains("steam") -> of("steam")
                s.contains("window") || s.contains("office") || s.contains("outlook") -> of("microsoft")
                else -> of("custom")
            }
    }
}