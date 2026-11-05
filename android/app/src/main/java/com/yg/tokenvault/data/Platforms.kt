package com.yg.tokenvault.data

import androidx.annotation.DrawableRes
import com.yg.tokenvault.R

/**
 * 一个平台的显示与算法特征。
 *
 * digits / period / algo 是该平台服务端约定的参数；charset 为 STEAM 时走 Steam Guard 的
 * 5 位自定义字母表，否则按十进制取模。
 *
 * 只保留四个高频平台 + 自定义：平台列表越短越好选，其余品牌一律用「自定义」
 * 并通过条目自己的图标（[TokenEntry.iconPath]）区分。
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
    val charset: String = "NUM",
    /** 分组：intl 国际 / other 其他 */
    val cat: String = "intl",
    /**
     * 图标自带官方配色时置 true：不画品牌色底、不 tint，
     * 直接把 VectorDrawable 原色画出来（Google 四色 G、Microsoft 四色方块就是这样）。
     */
    val colored: Boolean = false,
    /** 图标在方块里的占比；四色 logo 通常比单色 glyph 占得大一点 */
    val iconScale: Float = 0.56f
)

object Platforms {

    const val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

    const val KEY_GOOGLE = "google"
    const val KEY_MICROSOFT = "microsoft"
    const val KEY_GITHUB = "github"
    const val KEY_STEAM = "steam"
    const val KEY_CUSTOM = "custom"

    /** 顺序即编辑器里的排列顺序，自定义固定最后一位 */
    private val ALL = listOf(
        Platform(KEY_GOOGLE, "Google", "G", R.drawable.ic_brand_google, 0xFFFFFFFF, 0xFF3C4043,
            colored = true, iconScale = 0.64f),
        Platform(KEY_MICROSOFT, "Microsoft", "M", R.drawable.ic_brand_microsoft, 0xFFF3F4F6, 0xFF1F2937,
            colored = true, iconScale = 0.74f),
        Platform(KEY_STEAM, "Steam", "ST", R.drawable.ic_brand_steam, 0xFF16202D, 0xFFC7D5E0,
            digits = 5, period = 30, algo = "SHA1", charset = "STEAM", iconScale = 0.62f),
        Platform(KEY_GITHUB, "GitHub", "GH", R.drawable.ic_brand_github, 0xFF1B1F24, 0xFFE6EDF3,
            iconScale = 0.62f),
        Platform(KEY_CUSTOM, "自定义", "+", R.drawable.ic_brand_custom, 0xFF1A1B22, 0xFF8B5CF6, cat = "other")
    )

    private val BY_KEY = ALL.associateBy { it.key }

    /** 编辑页展示的顺序（高频平台在前，自定义压尾） */
    val picker: List<Platform> = ALL

    /**
     * 只给「网页登录」主页用的品牌块：Telegram 不是令牌平台（它没有 TOTP 密钥口径），
     * 但登录页要显示纸飞机图标，所以单独放一个不进 picker 的条目。
     */
    val telegramBrand = Platform(
        "telegram", "Telegram", "TG", R.drawable.ic_brand_telegram, 0xFF229ED9, 0xFFFFFFFF,
        cat = "other", iconScale = 0.6f
    )

    /** 平台搜索：匹配 key / label / 中文别名（平台已精简，保留接口供导入猜测用） */
    fun search(query: String?, cat: String = "all"): List<Platform> {
        val q = (query ?: "").trim().lowercase()
        return ALL.filter { p ->
            if (cat != "all" && p.cat != cat) return@filter false
            if (q.isBlank()) return@filter true
            p.key.contains(q) || p.label.lowercase().contains(q) ||
                (ALIASES[p.key]?.any { it.contains(q) } == true)
        }
    }

    fun of(key: String?): Platform = BY_KEY[key] ?: BY_KEY.getValue(KEY_CUSTOM)

    fun isCustom(key: String?): Boolean = key == null || key == KEY_CUSTOM

    /** 根据 issuer / 标签猜测平台，用于 otpauth:// 导入 */
    fun guess(issuerOrLabel: String?): Platform {
        val s = (issuerOrLabel ?: "").trim().lowercase()
        if (s.isBlank()) return of(KEY_CUSTOM)
        // 1) key 精确命中
        BY_KEY[s]?.let { return it }
        // 2) 别名（gmail / outlook / github 等）
        for ((key, words) in ALIASES) {
            if (words.any { s.contains(it) }) return of(key)
        }
        // 3) label 包含平台名
        ALL.firstOrNull {
            it.key != KEY_CUSTOM && s.contains(it.label.lowercase())
        }?.let { return it }
        return of(KEY_CUSTOM)
    }

    /** issuer 里常见的非品牌名（只留四个平台的相关别名） */
    private val ALIASES: Map<String, List<String>> = mapOf(
        KEY_GOOGLE to listOf(
            "gmail", "googlemail", "youtube", "google workspace", "gsuite",
            "google.com", "googleapis", "gstatic"
        ),
        KEY_MICROSOFT to listOf(
            "outlook", "hotmail", "office365", "office 365", "office", "azure",
            "onedrive", "skype", "msn", "live.com", "windows", "microsoft.com",
            "xbox live", "xbox"
        ),
        KEY_GITHUB to listOf("gh", "github.com", "githubusercontent", "gitlab", "git"),
        KEY_STEAM to listOf("steam", "steampowered", "steamcommunity", "steam guard", "valve")
    )
}