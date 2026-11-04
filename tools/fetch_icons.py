#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用 Simple Icons（CC0）官方几何重绘 web/app.js 里的品牌图标。

背景：早期图标是手绘近似，部分形状不准（用户反馈「有的图标错误」）。
本脚本按 平台key -> simple-icons slug 的映射，从 CDN 拉官方 path，
统一转成 `<svg viewBox="0 0 24 24" fill="currentColor">…</svg>` 内联形式，
再按 key 精确替换 app.js 中 ICONS 的对应条目。拉不到的品牌保持原样。

用法：
    $env:PYTHONIOENCODING='utf-8'; python tools/fetch_icons.py
"""
import re
import sys
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "web" / "app.js"
CDN = "https://cdn.jsdelivr.net/npm/simple-icons@latest/icons/{}.svg"

# 平台 key -> simple-icons slug（None 表示 CDN 上没有，保留手绘）
MAP = {
    # 国际
    "google": "google",
    "microsoft": "microsoft",
    "github": "github",
    "steam": "steam",
    "apple": "apple",
    "discord": "discord",
    "slack": "slack",
    "telegram": "telegram",
    "twitter": "x",
    "x": "x",
    "facebook": "facebook",
    "amazon": "amazon",
    "dropbox": "dropbox",
    "gitlab": "gitlab",
    "linkedin": "linkedin",
    "reddit": "reddit",
    "twitch": "twitch",
    "spotify": "spotify",
    "binance": "binance",
    "cloudflare": "cloudflare",
    "notion": "notion",
    "proton": "proton",
    "instagram": "instagram",
    "epic": "epicgames",
    "xbox": "xbox",
    "paypal": "paypal",
    "netflix": "netflix",
    "stripe": "stripe",
    "vercel": "vercel",
    "digitalocean": "digitalocean",
    "bitwarden": "bitwarden",
    "onepassword": "1password",
    "nintendo": "nintendoswitch",
    "playstation": "playstation",
    "battlenet": "battledotnet",
    "roblox": "roblox",
    "zoom": "zoom",
    "figma": "figma",
    "openai": "openai",
    # 国内
    "wechat": "wechat",
    "qq": "qq",
    "alipay": "alipay",
    "taobao": "taobao",
    "baidu": "baidu",
    "bilibili": "bilibili",
    "zhihu": "zhihu",
    "weibo": "sinaweibo",
    "xiaomi": "xiaomi",
    "huawei": "huawei",
    "douyin": "tiktok",
    "netease": "neteasecloudmusic",
    "aliyun": "alibabacloud",
    "meituan": "meituan",
    "xiaohongshu": "xiaohongshu",
    "ctrip": "tripdotcom",
    "jd": None,
    "tencentcloud": None,
    "dingtalk": None,
    "pinduoduo": None,
}


def fetch(slug: str) -> str:
    req = urllib.request.Request(
        CDN.format(slug), headers={"User-Agent": "KeyCoffer/1.3 (+icon-export)"}
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode("utf-8", "replace")


def to_inline(svg: str) -> str:
    m = re.search(r"<svg[^>]*>(.*?)</svg>", svg, re.S)
    if not m:
        raise ValueError("no svg body")
    inner = m.group(1)
    inner = re.sub(r"<title>.*?</title>", "", inner, flags=re.S)
    inner = inner.strip()
    if not inner:
        raise ValueError("empty svg body")
    return '<svg viewBox="0 0 24 24" fill="currentColor">' + inner + "</svg>"


def main() -> int:
    src = APP.read_text(encoding="utf-8")
    ok, miss, fail = [], [], []

    for key, slug in MAP.items():
        if not slug:
            miss.append(key)
            continue
        pat = re.compile(r"(?m)^  " + re.escape(key) + r":\s*`<svg.*?</svg>`,")
        if not pat.search(src):
            fail.append(f"{key}: app.js 无此条目")
            continue
        try:
            inline = to_inline(fetch(slug))
        except Exception as e:  # noqa: BLE001 - 网络异常直接记录
            fail.append(f"{key}<{slug}>: {e}")
            continue
        src = pat.sub(lambda _m, s=inline, k=key: "  " + k + ": `" + s + "`,", src, count=1)
        ok.append(key)

    APP.write_text(src, encoding="utf-8")
    print(f"更新 {len(ok)} 个：{', '.join(ok)}")
    if miss:
        print(f"CDN 无此品牌，保留手绘 {len(miss)} 个：{', '.join(miss)}")
    if fail:
        print("失败：")
        for f in fail:
            print("  -", f)
    return 0 if not fail else 1


if __name__ == "__main__":
    sys.exit(main())