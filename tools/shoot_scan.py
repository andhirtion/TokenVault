"""扫二维码层 + 「从相册选择」按钮的界面验收截图。

无摄像头环境下用 Chromium 的 fake device（--use-fake-device-for-media-stream），
并注入 BarcodeDetector 桩，让扫码层正常呈现。
用法: python tools/shoot_scan.py
"""
import asyncio
import os

from playwright.async_api import async_playwright

OUT = os.path.join(os.path.dirname(__file__), '..', 'shots')
os.makedirs(OUT, exist_ok=True)
URL = 'http://127.0.0.1:8777/index.html'

STUB = """
(() => {
  if (typeof window.BarcodeDetector === 'function') return;
  window.BarcodeDetector = class {
    async detect() { return []; }
  };
})();
"""


async def set_pin(page, digits='123456'):
    for d in digits:
        await page.click(f'[data-k="{d}"]')
        await page.wait_for_timeout(40)


async def main():
    errs = []
    async with async_playwright() as p:
        browser = await p.chromium.launch(
            channel='msedge',
            args=['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
        )
        ctx = await browser.new_context(
            viewport={'width': 390, 'height': 844},
            device_scale_factor=3, is_mobile=True, has_touch=True,
            locale='zh-CN', color_scheme='dark',
            permissions=['camera'],
        )
        await ctx.add_init_script(STUB)
        page = await ctx.new_page()
        page.on('pageerror', lambda e: errs.append(str(e)))
        await page.goto(URL, wait_until='networkidle')

        await set_pin(page)
        await page.wait_for_timeout(500)
        await set_pin(page)
        await page.wait_for_timeout(900)

        await page.click('#grabBtn')
        await page.wait_for_timeout(450)
        await page.click('.grab-row:nth-child(1)')
        await page.wait_for_timeout(1200)
        await page.screenshot(path=os.path.join(OUT, '16-scan-with-album.png'))
        print('已截图 16-scan-with-album.png')
        print('页面错误:', errs or '无')

        await page.click('#scanClose')
        await page.wait_for_timeout(300)
        await browser.close()


if __name__ == '__main__':
    asyncio.run(main())