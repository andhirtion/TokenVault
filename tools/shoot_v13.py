# -*- coding: utf-8 -*-
"""钥匣 1.3.0：生成「混合列表（含国内平台）」截图。

先启动静态服务器：python -m http.server 8777 -d web
再运行：python tools/shoot_v13.py
"""
import asyncio
import os
import sys
from pathlib import Path

from playwright.async_api import async_playwright

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

ROOT = Path(__file__).resolve().parents[1]
SHOTS = str(ROOT / "shots")
URL = "http://localhost:8777/index.html"


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(channel="msedge", headless=True)
        ctx = await browser.new_context(
            viewport={"width": 390, "height": 844},
            device_scale_factor=3,
            color_scheme="dark",
            has_touch=True,
            is_mobile=True,
        )
        page = await ctx.new_page()
        errs = []
        page.on("pageerror", lambda e: errs.append(str(e)))
        await page.goto(URL)
        await page.wait_for_timeout(700)

        # 建库
        await page.evaluate("localStorage.clear()")
        await page.reload()
        await page.wait_for_timeout(600)
        for d in "123456":
            await page.click(f'#keypad button[data-k="{d}"]')
        await page.wait_for_timeout(300)
        for d in "123456":
            await page.click(f'#keypad button[data-k="{d}"]')
        await page.wait_for_timeout(1500)
        print("hasVault:", await page.evaluate("cfg.hasVault"))

        async def add(kind, platform, name, account, secret=None, password=None, note=None):
            await page.click("#fab")
            await page.wait_for_timeout(420)
            await page.click(f'#editorSeg button[data-kind="{kind}"]')
            await page.wait_for_timeout(180)
            await page.click("#pfPicker")
            await page.wait_for_timeout(280)
            await page.fill("#pfSearch", platform)
            await page.wait_for_timeout(220)
            await page.click(f'#platformGrid .pf-opt[data-pf="{platform}"]')
            await page.wait_for_timeout(200)
            await page.fill("#fName", name)
            await page.fill("#fAccount", account)
            if secret:
                await page.fill("#fSecret", secret)
            if password:
                await page.fill("#fPassword", password)
            if note:
                await page.fill("#fNote", note)
            await page.click("#editorSave")
            await page.wait_for_timeout(520)

        await add("code", "google", "Google", "yg@gmail.com", secret="JBSWY3DPEHPK3PXP")
        await add("login", "wechat", "微信", "yg_wechat", password="Wx!2026pass")
        await add("login", "alipay", "支付宝", "yg@alipay.com", password="Alipay#8899")
        await add("code", "bilibili", "哔哩哔哩", "yg_bili", secret="KRSXG5CTMVRXEZLU")
        await add("login", "jd", "京东", "138****8888", password="Jd@2026buy")
        await add("login", "github", "GitHub", "yg@example.com", password="S3cr3t-Pa55w0rd!")
        await page.wait_for_timeout(2800)

        print("条目数:", await page.evaluate("state.entries.length"))
        await page.screenshot(path=os.path.join(SHOTS, "32-list-cn.png"))

        # 国内平台条目详情
        await page.click('#list .card[data-id]')
        await page.wait_for_timeout(700)
        await page.screenshot(path=os.path.join(SHOTS, "33-detail-wechat.png"))
        await page.click("#detailClose")
        await page.wait_for_timeout(400)

        print("页面错误:", errs or "无")
        await browser.close()


asyncio.run(main())