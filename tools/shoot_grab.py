"""
「获取令牌」新功能验收截图 —— 真实浏览器跑一遍
用法: python tools/shoot_grab.py
"""
import asyncio, os, base64
from playwright.async_api import async_playwright

OUT = os.path.join(os.path.dirname(__file__), '..', 'shots')
os.makedirs(OUT, exist_ok=True)
URL = 'http://127.0.0.1:8777/index.html'

DEMO = [
    ('google',    'Google',    'yg.main@gmail.com', 'JBSWY3DPEHPK3PXP'),
    ('github',    'GitHub',    'yg-dev',            'KRSXG5CTMVRXEZLU'),
    ('steam',     'Steam',     'yg_gamer',          'ONSWG4TFOQZTGNBV'),
]


def varint(v):
    out = []
    while True:
        b = v & 0x7f
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def fbytes(field, data):
    return varint((field << 3) | 2) + varint(len(data)) + data


def fvarint(field, v):
    return varint((field << 3)) + varint(v)


def params(secret, name, issuer, algo=1, digits=1, type_=2):
    return (fbytes(1, secret) + fbytes(2, name.encode()) + fbytes(3, issuer.encode())
            + fvarint(4, algo) + fvarint(5, digits) + fvarint(6, type_))


def migration_uri(items):
    body = b''.join(fbytes(1, i) for i in items)
    b64 = base64.urlsafe_b64encode(body).decode().rstrip('=')
    return 'otpauth-migration://offline?data=' + b64


MIGRATION = migration_uri([
    params(bytes(range(20)), 'work@corp.com', 'Dropbox'),
    params(bytes(range(20, 40)), 'play@home.com', 'Epic Games'),
])


async def set_pin(page, digits='123456'):
    for d in digits:
        await page.click(f'[data-k="{d}"]')
        await page.wait_for_timeout(40)


async def add_entry(page, platform, name, account, secret):
    await page.click('#fab')
    await page.wait_for_timeout(420)
    await page.click(f'.pf-opt[data-pf="{platform}"]')
    await page.fill('#fName', name)
    await page.fill('#fAccount', account)
    await page.fill('#fSecret', secret)
    await page.click('#editorSave')
    await page.wait_for_timeout(520)


async def main():
    errs = []
    async with async_playwright() as p:
        browser = await p.chromium.launch(channel='msedge')
        ctx = await browser.new_context(
            viewport={'width': 390, 'height': 844},
            device_scale_factor=3, is_mobile=True, has_touch=True,
            locale='zh-CN', color_scheme='dark',
        )
        page = await ctx.new_page()
        page.on('console', lambda m: errs.append('CONSOLE: ' + m.text) if m.type == 'error' else None)
        page.on('pageerror', lambda e: errs.append('PAGEERROR: ' + str(e)))

        await page.goto(URL, wait_until='networkidle')
        await page.wait_for_timeout(500)
        await set_pin(page)
        await page.wait_for_timeout(600)
        await set_pin(page)
        await page.wait_for_timeout(1100)

        for args in DEMO:
            await add_entry(page, *args)
        await page.wait_for_timeout(900)
        await page.screenshot(path=f'{OUT}/11-list-with-grab.png')

        # 打开获取面板
        await page.click('#grabBtn')
        await page.wait_for_timeout(650)
        await page.screenshot(path=f'{OUT}/12-grab-sheet.png')

        # 选「粘贴链接或密钥」
        await page.click('.grab-row:nth-child(3)')
        await page.wait_for_timeout(600)
        await page.screenshot(path=f'{OUT}/13-grab-paste.png')

        # 直接往输入框里塞迁移串（走 parseAnyOtp 的 migration 分支）
        await page.fill('#modalInput', MIGRATION)
        await page.click('#modalOk')
        await page.wait_for_timeout(1100)
        await page.screenshot(path=f'{OUT}/14-grab-imported.png')

        names = await page.evaluate("""() => Array.from(document.querySelectorAll('.card .card-name')).map(n => n.innerText)""")
        print('导入后列表：', names)

        # 选「扫二维码」（桌面 edge 无摄像头，看提示是否正确）
        await page.click('#grabBtn')
        await page.wait_for_timeout(500)
        await page.click('.grab-row:nth-child(2)')
        await page.wait_for_timeout(900)
        await page.screenshot(path=f'{OUT}/15-grab-image-dialog-state.png')

        print('控制台错误：', errs if errs else '无')
        await browser.close()

asyncio.run(main())