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
    val charset: String = "NUM",
    /** 分组：cn 国内 / intl 国际 / other 其他 */
    val cat: String = "intl"
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
        Platform("custom", "自定义", "?", R.drawable.ic_brand_custom, 0xFF1A1B22, 0xFF8B5CF6, cat = "other"),

        // ── 国内 ──────────────────────────────────────────
        Platform("wechat", "微信", "微", R.drawable.ic_brand_wechat, 0xFF07C160, 0xFFFFFFFF, cat = "cn"),
        Platform("qq", "QQ", "QQ", R.drawable.ic_brand_qq, 0xFF12B7F5, 0xFFFFFFFF, cat = "cn"),
        Platform("alipay", "支付宝", "支", R.drawable.ic_brand_alipay, 0xFF1677FF, 0xFFFFFFFF, cat = "cn"),
        Platform("taobao", "淘宝", "淘", R.drawable.ic_brand_taobao, 0xFFFF5000, 0xFFFFFFFF, cat = "cn"),
        Platform("jd", "京东", "京", R.drawable.ic_brand_jd, 0xFFE1251B, 0xFFFFFFFF, cat = "cn"),
        Platform("baidu", "百度", "百", R.drawable.ic_brand_baidu, 0xFF2932E1, 0xFFFFFFFF, cat = "cn"),
        Platform("bilibili", "哔哩哔哩", "B", R.drawable.ic_brand_bilibili, 0xFF00A1D6, 0xFFFFFFFF, cat = "cn"),
        Platform("zhihu", "知乎", "知", R.drawable.ic_brand_zhihu, 0xFF0084FF, 0xFFFFFFFF, cat = "cn"),
        Platform("weibo", "微博", "博", R.drawable.ic_brand_weibo, 0xFFE6162D, 0xFFFFFFFF, cat = "cn"),
        Platform("xiaomi", "小米", "米", R.drawable.ic_brand_xiaomi, 0xFFFF6900, 0xFFFFFFFF, cat = "cn"),
        Platform("huawei", "华为", "华", R.drawable.ic_brand_huawei, 0xFFC7000B, 0xFFFFFFFF, cat = "cn"),
        Platform("douyin", "抖音", "抖", R.drawable.ic_brand_douyin, 0xFF161823, 0xFFFFFFFF, cat = "cn"),
        Platform("netease", "网易", "易", R.drawable.ic_brand_netease, 0xFFDE1414, 0xFFFFFFFF, cat = "cn"),
        Platform("aliyun", "阿里云", "阿", R.drawable.ic_brand_aliyun, 0xFFFF6A00, 0xFFFFFFFF, cat = "cn"),
        Platform("tencentcloud", "腾讯云", "腾", R.drawable.ic_brand_tencentcloud, 0xFF0052D9, 0xFFFFFFFF, cat = "cn"),
        Platform("dingtalk", "钉钉", "钉", R.drawable.ic_brand_dingtalk, 0xFF1E7BFF, 0xFFFFFFFF, cat = "cn"),
        Platform("meituan", "美团", "美", R.drawable.ic_brand_meituan, 0xFFFFC300, 0xFF3A2503, cat = "cn"),
        Platform("pinduoduo", "拼多多", "拼", R.drawable.ic_brand_pinduoduo, 0xFFE22E1F, 0xFFFFFFFF, cat = "cn"),
        Platform("xiaohongshu", "小红书", "红", R.drawable.ic_brand_xiaohongshu, 0xFFFF2442, 0xFFFFFFFF, cat = "cn"),
        Platform("ctrip", "携程", "携", R.drawable.ic_brand_ctrip, 0xFF2577E3, 0xFFFFFFFF, cat = "cn"),

        // ── 国际服务 ──────────────────────────────────────
        Platform("paypal", "PayPal", "PP", R.drawable.ic_brand_paypal, 0xFF003087, 0xFFFFFFFF),
        Platform("netflix", "Netflix", "NF", R.drawable.ic_brand_netflix, 0xFFE50914, 0xFFFFFFFF),
        Platform("stripe", "Stripe", "S", R.drawable.ic_brand_stripe, 0xFF635BFF, 0xFFFFFFFF),
        Platform("vercel", "Vercel", "V", R.drawable.ic_brand_vercel, 0xFF0B0B0C, 0xFFFFFFFF),
        Platform("digitalocean", "DigitalOcean", "DO", R.drawable.ic_brand_digitalocean, 0xFF0080FF, 0xFFFFFFFF),
        Platform("bitwarden", "Bitwarden", "BW", R.drawable.ic_brand_bitwarden, 0xFF175DDC, 0xFFFFFFFF),
        Platform("onepassword", "1Password", "1P", R.drawable.ic_brand_onepassword, 0xFF1A8CFF, 0xFFFFFFFF),
        Platform("nintendo", "Nintendo", "NT", R.drawable.ic_brand_nintendo, 0xFFE60012, 0xFFFFFFFF),
        Platform("playstation", "PlayStation", "PS", R.drawable.ic_brand_playstation, 0xFF0070D1, 0xFFFFFFFF),
        Platform("battlenet", "战网", "战", R.drawable.ic_brand_battlenet, 0xFF148EFF, 0xFFFFFFFF),
        Platform("roblox", "Roblox", "RB", R.drawable.ic_brand_roblox, 0xFFE2231A, 0xFFFFFFFF),
        Platform("zoom", "Zoom", "ZM", R.drawable.ic_brand_zoom, 0xFF2D8CFF, 0xFFFFFFFF),
        Platform("figma", "Figma", "FG", R.drawable.ic_brand_figma, 0xFFF24E1E, 0xFFFFFFFF),
        Platform("openai", "OpenAI", "AI", R.drawable.ic_brand_openai, 0xFF10A37F, 0xFFFFFFFF)
    )

    private val BY_KEY = ALL.associateBy { it.key }

    /** 编辑页展示的顺序（高频平台在前） */
    val picker: List<Platform> = ALL

    /** 平台搜索：匹配 key / label / 中文别名 */
    fun search(query: String?, cat: String = "all"): List<Platform> {
        val q = (query ?: "").trim().lowercase()
        return ALL.filter { p ->
            if (cat != "all" && p.cat != cat) return@filter false
            if (q.isBlank()) return@filter true
            p.key.contains(q) || p.label.lowercase().contains(q) ||
                (ALIASES[p.key]?.any { it.contains(q) } == true)
        }
    }

    fun of(key: String?): Platform = BY_KEY[key] ?: BY_KEY.getValue("custom")

    /** 根据 issuer / 标签猜测平台，用于 otpauth:// 导入 */
    fun guess(issuerOrLabel: String?): Platform {
        val s = (issuerOrLabel ?: "").trim().lowercase()
        if (s.isBlank()) return of("custom")
        // 1) key 精确命中
        BY_KEY[s]?.let { return it }
        // 2) key / label 包含匹配。key 长度 < 3 的（x）跳过，否则任何含 x 的 issuer 都会误判
        ALL.firstOrNull {
            it.key != "custom" && it.key.length >= 3 &&
                (s.contains(it.key) || (it.label.length >= 3 && s.contains(it.label.lowercase())))
        }?.let { return it }
        // 3) 常见别名
        for ((key, words) in ALIASES) {
            if (words.any { s.contains(it) }) return of(key)
        }
        return when {
            s.contains("steam") -> of("steam")
            s.contains("git") -> of("github")
            s.contains("window") || s.contains("office") || s.contains("outlook") -> of("microsoft")
            else -> of("custom")
        }
    }

    /** issuer 里常见的非品牌名 */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "google" to listOf("gmail", "googlemail", "youtube", "workspace", "gsuite"),
        "microsoft" to listOf("outlook", "hotmail", "office365", "office 365", "azure", "onedrive", "skype", "msn", "live.com"),
        "apple" to listOf("icloud", "appleid", "apple id"),
        "amazon" to listOf("aws", "amazon web"),
        "proton" to listOf("protonmail"),
        "facebook" to listOf("meta", "fb"),
        "x" to listOf("twitter"),
        "epic" to listOf("epicgames", "epic games"),
        "xbox" to listOf("xbox live"),
        // Telegram：网页版 / 频道链接 t.me、web.telegram.org 都归到 Telegram
        "telegram" to listOf("t.me", "web.telegram", "telegram web", "tg web"),
        // ── 国内 ──
        "wechat" to listOf("微信", "weixin", "enterprise wechat", "企业微信"),
        "qq" to listOf("腾讯qq", "qqmail", "foxmail"),
        "alipay" to listOf("支付宝", "zhifubao", "ant group", "蚂蚁"),
        "taobao" to listOf("淘宝", "天猫"),
        "jd" to listOf("京东", "jd.com", "jingdong"),
        "baidu" to listOf("百度", "baidu.com"),
        "bilibili" to listOf("哔哩", "b站", "bili", "bilibili.com"),
        "zhihu" to listOf("知乎", "zhihu.com"),
        "weibo" to listOf("微博", "sina", "新浪"),
        "xiaomi" to listOf("小米", "mi.com", "miui"),
        "huawei" to listOf("华为", "harmonyos", "华为云"),
        "douyin" to listOf("抖音", "douyin", "tiktok", "字节"),
        "netease" to listOf("网易", "163.com", "126.com"),
        "aliyun" to listOf("阿里云", "alibabacloud"),
        "tencentcloud" to listOf("腾讯云", "tencent cloud", "qcloud"),
        "dingtalk" to listOf("钉钉"),
        "meituan" to listOf("美团", "dianping"),
        "pinduoduo" to listOf("拼多多", "pdd"),
        "xiaohongshu" to listOf("小红书", "rednote", "xhs"),
        "ctrip" to listOf("携程", "trip.com", "去哪儿"),
        // ── 国际服务 ──
        "paypal" to listOf("贝宝", "paypal.com"),
        "netflix" to listOf("奈飞", "netflix.com"),
        "onepassword" to listOf("1password", "agilebits"),
        "nintendo" to listOf("任天堂", "switch"),
        "playstation" to listOf("psn", "sony", "playstation network"),
        "battlenet" to listOf("battle.net", "战网", "blizzard", "暴雪"),
        "openai" to listOf("chatgpt", "gpt", "anthropic", "claude")
    )
}