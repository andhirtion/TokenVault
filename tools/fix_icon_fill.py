# -*- coding: utf-8 -*-
"""把 ICONS 里单色 SVG 的顶层 fill 改为 currentColor，交给品牌底色 + fg 控制可见性。

背景：ICONS 里有两种图标 —— 多色 logo（每个 path 自带品牌色，如 Google/Microsoft/Slack）
和单色 glyph（顶层写死一种颜色，如 Amazon 橙、Dropbox 蓝）。单色 glyph 一旦和卡片底色
接近就完全看不见（蓝底蓝图）。统一改成 currentColor，由外层 span 的 color 决定。

用法：python tools/fix_icon_fill.py [--dry]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_JS = ROOT / "web" / "app.js"

src = APP_JS.read_text(encoding="utf-8")

# 定位 ICONS 块
start = src.index("const ICONS = {")
end = src.index("\n};", start)
block = src[start:end]

# 只处理 <svg ...> 开标签内部的 fill，且仅当该 svg 内所有 path 都没有自己的 fill
# （即单色 glyph）。多色 logo 保持原样。
svg_re = re.compile(r"<svg\b[^>]*>.*?</svg>", re.S)
open_re = re.compile(r"^<svg\b[^>]*>", re.S)
fill_attr_re = re.compile(r'\sfill="(#[0-9a-fA-F]{3,8})"')

changed = []
kept = []


def fix(match):
    svg = match.group(0)
    open_tag = open_re.match(svg).group(0)
    body = svg[len(open_tag):]
    # 孩子节点自带 fill → 多色 logo，不动
    if re.search(r'\sfill="', body):
        kept.append(open_tag[:60])
        return svg
    if not fill_attr_re.search(open_tag):
        return svg
    new_open = fill_attr_re.sub(' fill="currentColor"', open_tag)
    changed.append(new_open[:60])
    return new_open + body


new_block = svg_re.sub(fix, block)
if new_block == block:
    print("没有需要修改的图标")
else:
    out = src[:start] + new_block + src[end:]
    if "--dry" in sys.argv:
        print("dry-run：将修改 %d 个图标" % len(changed))
    else:
        APP_JS.write_text(out, encoding="utf-8")
        print("已把 %d 个单色图标改为 currentColor，%d 个多色 logo 保持不变" % (len(changed), len(kept)))
        for c in changed[:6]:
            print("  ·", c)