"""
把 Web 端的品牌 SVG 图标转换成 Android VectorDrawable XML
输出到 android/app/src/main/res/drawable/
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.join(HERE, '..', 'web', 'app.js')
OUT = os.path.join(HERE, '..', 'android', 'app', 'src', 'main', 'res', 'drawable')
os.makedirs(OUT, exist_ok=True)

src = open(WEB, encoding='utf-8').read()

# 提取 ICONS = { ... };
m = re.search(r'const ICONS = \{(.*?)\n\};', src, re.S)
if not m:
    sys.exit('未找到 ICONS 定义')
body = m.group(1)

# 逐个抓 key: `...`
entries = re.findall(r'(\w+):\s*`(<svg.*?</svg>)`', body, re.S)
print(f'抓到 {len(entries)} 个图标')

NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def rect_to_path(x, y, w, h, rx):
    """把 <rect> 转成 pathData（带圆角）"""
    x, y, w, h, rx = float(x), float(y), float(w), float(h), float(rx or 0)
    if rx <= 0:
        return f'M{x},{y} H{x+w} V{y+h} H{x} Z'
    r = min(rx, w / 2, h / 2)
    return (
        f'M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} '
        f'V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} '
        f'H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} '
        f'V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z'
    )


def circle_to_path(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return (
        f'M{cx-r},{cy} A{r},{r} 0 1 1 {cx+r},{cy} A{r},{r} 0 1 1 {cx-r},{cy} Z'
    )


def convert(name, svg):
    vb = re.search(r'viewBox="([\d.\s-]+)"', svg)
    vw, vh = (vb.group(1).split()[2:4] if vb else ('24', '24'))
    body_svg = re.sub(r'^<svg[^>]*>|</svg>$', '', svg.strip())

    paths = []

    # <path ...>
    for pm in re.finditer(r'<path([^>]*)/>', body_svg):
        attrs = pm.group(1)
        d = re.search(r'\bd="([^"]+)"', attrs)
        if not d:
            continue
        fill = re.search(r'fill="([^"]+)"', attrs)
        stroke = re.search(r'stroke="([^"]+)"', attrs)
        sw = re.search(r'stroke-width="([\d.]+)"', attrs)
        op = re.search(r'opacity="([\d.]+)"', attrs)

        # 继承 svg 根节点的 fill
        root_fill = re.search(r'<svg[^>]*\bfill="([^"]+)"', svg)
        fc = fill.group(1) if fill else (root_fill.group(1) if root_fill else '#000000')

        parts = [f'android:pathData="{d.group(1)}"']
        if fc == 'none':
            parts.append('android:fillColor="#00000000"')
        elif fc == 'currentColor':
            parts.append('android:fillColor="#FFFFFFFF"')
        else:
            parts.append(f'android:fillColor="{fc}"')
        if stroke:
            sc = stroke.group(1)
            parts.append(f'android:strokeColor="{"#FFFFFFFF" if sc == "currentColor" else sc}"')
            parts.append(f'android:strokeWidth="{sw.group(1) if sw else "1"}"')
            parts.append('android:strokeLineCap="round"')
            parts.append('android:strokeLineJoin="round"')
        if op:
            parts.append(f'android:fillAlpha="{op.group(1)}"')
        paths.append('    <path ' + '\n        '.join(parts) + ' />')

    # <rect ...>
    for rm in re.finditer(r'<rect([^>]*)/>', body_svg):
        a = rm.group(1)
        g = lambda k, d=None: (re.search(rf'\b{k}="([^"]+)"', a).group(1)
                               if re.search(rf'\b{k}="([^"]+)"', a) else d)
        d = rect_to_path(g('x', 0), g('y', 0), g('width'), g('height'), g('rx', 0))
        paths.append(f'    <path android:pathData="{d}" android:fillColor="{g("fill", "#000000")}" />')

    # <circle ...>
    for cm in re.finditer(r'<circle([^>]*)/>', body_svg):
        a = cm.group(1)
        g = lambda k, d=None: (re.search(rf'\b{k}="([^"]+)"', a).group(1)
                               if re.search(rf'\b{k}="([^"]+)"', a) else d)
        d = circle_to_path(g('cx', 12), g('cy', 12), g('r', 5))
        fill = g('fill')
        if not fill:
            stroke = g('stroke', '#FFFFFF')
            paths.append(
                f'    <path android:pathData="{d}" android:fillColor="#00000000" '
                f'android:strokeColor="{"#FFFFFFFF" if stroke == "currentColor" else stroke}" '
                f'android:strokeWidth="{g("stroke-width", "2")}" />'
            )
        else:
            paths.append(f'    <path android:pathData="{d}" android:fillColor="{fill}" />')

    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<vector {NS}\n'
        f'    android:width="24dp"\n    android:height="24dp"\n'
        f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n'
        + '\n'.join(paths) + '\n</vector>\n'
    )
    fn = os.path.join(OUT, f'ic_brand_{name}.xml')
    open(fn, 'w', encoding='utf-8').write(xml)
    return len(paths)


total = 0
for name, svg in entries:
    try:
        n = convert(name, svg)
        total += n
        print(f'  ✓ {name:12s} {n} 条路径')
    except Exception as e:
        print(f'  ✗ {name}: {e}')

print(f'\n完成：{len(entries)} 个图标，共 {total} 条路径 → {os.path.normpath(OUT)}')