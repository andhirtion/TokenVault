"""
TokenVault 界面验收截图脚本 —— 真实浏览器跑完整用户流程
用法: python tools/shoot.py [--url http://127.0.0.1:8777/index.html]
"""
import asyncio, os, sys
from playwright.async_api import async_playwright

OUT = os.path.join(os.path.dirname(__file__), '..', 'shots')
os.makedirs(OUT, exist_ok=True)
URL = 'http://127.0.0.1:8777/index.html'

DEMO = [
    ('google',    'Google',    'yg.main@gmail.com', 'JBSWY3DPEHPK3PXP'),
    ('github',    'GitHub',    'yg-dev',            'KRSXG5CTMVRXEZLU'),
    ('microsoft', 'Microsoft', 'yg@outlook.com',    'MFRGGZDFMZTWQ2LK'),
    ('steam',     'Steam',     'yg_gamer',          'ONSWG4TFOQZTGNBV'),
    ('discord',   'Discord',   'yg#2026',           'NBSWY3DPEB3W64TB'),
]


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
        await page.wait_for_timeout(600)

        await page.screenshot(path=f'{OUT}/01-lock-setup.png')
        await set_pin(page)
        await page.wait_for_timeout(700)
        await page.screenshot(path=f'{OUT}/02-lock-confirm.png')
        await set_pin(page)
        await page.wait_for_timeout(1200)
        await page.screenshot(path=f'{OUT}/03-empty.png')

        for args in DEMO:
            await add_entry(page, *args)
        await page.wait_for_timeout(1500)
        await page.screenshot(path=f'{OUT}/04-list-dark.png')

        await page.click('.card:nth-child(2) .pf-icon')
        await page.wait_for_timeout(700)
        await page.screenshot(path=f'{OUT}/05-detail.png')
        await page.click('#detailClose')
        await page.wait_for_timeout(500)

        await page.click('#fab')
        await page.wait_for_timeout(520)
        await page.screenshot(path=f'{OUT}/06-editor.png')
        await page.click('#editorCancel')
        await page.wait_for_timeout(450)

        await page.click('.tab[data-tab="settings"]')
        await page.wait_for_timeout(650)
        await page.screenshot(path=f'{OUT}/07-settings.png')

        await page.click('#themeSeg button[data-theme="light"]')
        await page.wait_for_timeout(500)
        await page.click('.tab[data-tab="vault"]')
        await page.wait_for_timeout(900)
        await page.screenshot(path=f'{OUT}/08-list-light.png')

        await page.click('.tab[data-tab="generator"]')
        await page.wait_for_timeout(600)
        try:
            await page.click('#pwGen')
            await page.click('#secGen')
        except Exception:
            pass
        await page.wait_for_timeout(500)
        await page.screenshot(path=f'{OUT}/09-toolbox.png')

        # 回深色 + 搜索态
        await page.click('.tab[data-tab="settings"]')
        await page.wait_for_timeout(400)
        await page.click('#themeSeg button[data-theme="dark"]')
        await page.wait_for_timeout(300)
        await page.click('.tab[data-tab="vault"]')
        await page.wait_for_timeout(500)
        await page.click('#searchBtn')
        await page.wait_for_timeout(400)
        await page.fill('#searchInput', 'git')
        await page.wait_for_timeout(700)
        await page.screenshot(path=f'{OUT}/10-search.png')

        codes = await page.evaluate("""() => {
            const out = [];
            document.querySelectorAll('[data-code]').forEach(n => out.push(n.innerText.replace(/\\s+/g,' ')));
            return out;
        }""")
        print('页面验证码：', codes)
        print('控制台错误：', errs if errs else '无')
        await browser.close()

asyncio.run(main())