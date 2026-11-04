# -*- coding: utf-8 -*-
"""钥匣 1.4.0 验收：主界面首屏、自定义图标（上传/抓取）、WebDAV 设置组。

先启动静态服务器：python -m http.server 8777 -d web
再运行：python tools/shoot_v14.py
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
ICON_FILE = str(ROOT / "web" / "icon.svg")


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
        await ctx.add_init_script("try{localStorage.clear()}catch(e){}")
        page = await ctx.new_page()
        errs = []
        page.on("pageerror", lambda e: errs.append(str(e)))
        await page.goto(URL)
        await page.wait_for_timeout(900)

        # ① 主界面首屏
        home_visible = await page.is_visible("#home")
        cards = await page.eval_on_selector_all(".home-card", "els => els.map(e => e.dataset.go)")
        lock_hidden = await page.evaluate("document.getElementById('lockScreen').hidden")
        status = await page.eval_on_selector("#homeStatus", "e => e.textContent")
        print("主界面可见:", home_visible)
        print("卡片:", cards)
        print("锁屏初始隐藏:", lock_hidden)
        print("状态文案:", status)
        await page.screenshot(path=os.path.join(SHOTS, "34-home.png"))

        # 选「动态验证码」进入
        await page.click('.home-card[data-go="code"]')
        await page.wait_for_timeout(1200)
        print("进入后锁屏可见:", await page.is_visible("#lockScreen"))
        print("进入后主界面隐藏:", await page.evaluate("document.getElementById('home').hidden"))
        await page.screenshot(path=os.path.join(SHOTS, "35-home-entered.png"))

        # 建库
        for d in "123456":
            await page.click(f'#keypad button[data-k="{d}"]')
        await page.wait_for_timeout(320)
        for d in "123456":
            await page.click(f'#keypad button[data-k="{d}"]')
        await page.wait_for_timeout(1500)
        print("hasVault:", await page.evaluate("cfg.hasVault"))
        print("进入后 filter:", await page.evaluate("state.filter"))

        # ③ 编辑器自定义图标行
        await page.click("#fab")
        await page.wait_for_timeout(460)
        print("图标行可见:", await page.is_visible(".icon-row"))
        print("预览初始有 svg:", await page.evaluate("!!document.querySelector('#iconPreview svg')"))
        await page.click("#pfPicker")
        await page.wait_for_timeout(260)
        await page.fill("#pfSearch", "github")
        await page.wait_for_timeout(220)
        await page.click('#platformGrid .pf-opt[data-pf="github"]')
        await page.wait_for_timeout(200)
        await page.fill("#fName", "GitHub")
        await page.fill("#fAccount", "yg@example.com")
        await page.click('#editorSeg button[data-kind="login"]')
        await page.wait_for_timeout(200)
        await page.fill("#fPassword", "S3cr3t-Pa55w0rd!")
        await page.screenshot(path=os.path.join(SHOTS, "36-editor-icon.png"))

        # 上传自定义图标
        await page.set_input_files("#iconFile", ICON_FILE)
        await page.wait_for_timeout(700)
        print("上传后 editorIcon 长度:", await page.evaluate("(state.editorIcon||'').length"))
        print("预览变成图片:", await page.evaluate("!!document.querySelector('#iconPreview img.brand-img')"))
        print("「用默认」按钮显示:", await page.is_visible("#iconReset"))
        await page.screenshot(path=os.path.join(SHOTS, "37-icon-uploaded.png"))

        await page.click("#editorSave")
        await page.wait_for_timeout(700)
        print("条目数:", await page.evaluate("state.entries.length"))
        print("落库 icon 长度:", await page.evaluate("(state.entries[0].icon||'').length"))
        # 条目是 login，当前 filter 是 code，先切回全部再查图标
        await page.click('#filterChips .chip[data-filter="all"]')
        await page.wait_for_timeout(500)
        print("列表出现自定义图:", await page.evaluate("!!document.querySelector('#list .card .pf-icon img.brand-img')"))
        await page.screenshot(path=os.path.join(SHOTS, "38-list-custom-icon.png"))

        # ⑤ WebDAV 设置组
        await page.click("#settingsBtn")
        await page.wait_for_timeout(700)
        print("WebDAV 地址栏可见:", await page.is_visible("#davUrl"))
        print("WebDAV 按钮数:", await page.evaluate("document.querySelectorAll('.dav-actions .mini-btn').length"))
        print("dav-tip 文案:", (await page.eval_on_selector(".dav-tip", "e => e.textContent")).strip()[:40])
        await page.fill("#davUrl", "https://dav.example.com/remote.php/dav/files/yg/")
        await page.fill("#davUser", "yg")
        await page.fill("#davPass", "app-password")
        await page.wait_for_timeout(300)
        print("dav 配置落盘:", await page.evaluate("davCfg().url"))
        el = await page.query_selector("#davTestBtn")
        await el.scroll_into_view_if_needed()
        await page.wait_for_timeout(400)
        await page.screenshot(path=os.path.join(SHOTS, "39-settings-webdav.png"))

        # 重载后主界面 → 自动指纹分支不应报错
        await page.reload()
        await page.wait_for_timeout(900)
        print("重载后主界面可见:", await page.is_visible("#home"))
        print("页面错误:", errs if errs else "无")
        await browser.close()


asyncio.run(main())