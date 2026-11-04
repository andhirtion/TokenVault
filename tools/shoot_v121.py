# -*- coding: utf-8 -*-
"""钥匣 1.2.1 验收：平台折叠 / 搜索 / 指纹优先。

跑法：
    python -m http.server 8777 -d web
    python tools/shoot_v121.py
"""
import asyncio
import sys

from playwright.async_api import async_playwright

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

BASE = "http://localhost:8777/index.html"
SHOTS = r"C:\Users\15386\Desktop\work\TokenVault\shots"
PIN = "123456"


async def type_pin(page, pin=PIN):
    for ch in pin:
        await page.click(f'#keypad button[data-k="{ch}"]')
        await page.wait_for_timeout(70)


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(channel="msedge", headless=True)
        page = await browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=3)
        cdp = await page.context.new_cdp_session(page)
        await cdp.send("WebAuthn.enable", {"enableUI": False})
        await cdp.send("WebAuthn.addVirtualAuthenticator", {"options": {
            "protocol": "ctap2", "ctap2Version": "ctap2_1", "transport": "internal",
            "hasResidentKey": True, "hasUserVerification": True,
            "isUserVerified": True, "automaticPresenceSimulation": True,
        }})

        await page.goto(BASE)
        await page.evaluate("localStorage.clear()")
        await page.reload()
        await page.wait_for_timeout(500)

        # ── 建库 ──
        await type_pin(page)
        await page.wait_for_timeout(900)
        await type_pin(page)
        await page.wait_for_timeout(1200)
        print("建库后 hasVault:", await page.evaluate("!!cfg.hasVault"))

        # ── 编辑器：平台折叠态 ──
        await page.click("#fab")
        await page.wait_for_timeout(600)
        await page.click('#editorSeg button[data-kind="login"]')
        await page.wait_for_timeout(300)
        collapsed = await page.evaluate("$('#pfPanel').hidden")
        print("默认折叠（面板隐藏）:", collapsed)
        await page.screenshot(path=SHOTS + r"\27-editor-platform-collapsed.png")

        # ── 展开面板 ──
        await page.click("#pfPicker")
        await page.wait_for_timeout(420)
        print("展开后面板可见:", await page.evaluate("!$('#pfPanel').hidden"))
        print("平台总数文案:", await page.inner_text("#pfPickerCats"))
        await page.screenshot(path=SHOTS + r"\28-platform-panel.png")

        # ── 搜索中文 ──
        await page.fill("#pfSearch", "微信")
        await page.wait_for_timeout(320)
        hits = await page.eval_on_selector_all("#platformGrid .pf-opt", "els => els.map(e => e.dataset.pf)")
        print("搜「微信」命中:", hits)
        await page.screenshot(path=SHOTS + r"\29-platform-search.png")

        # ── 国内分类 ──
        await page.fill("#pfSearch", "")
        await page.click('#pfCats button[data-cat="cn"]')
        await page.wait_for_timeout(320)
        print("国内平台数:", await page.eval_on_selector_all("#platformGrid .pf-opt", "els => els.length"))
        await page.screenshot(path=SHOTS + r"\29b-platform-cn.png")

        # ── 选微信 ──
        await page.click('#platformGrid .pf-opt[data-pf="wechat"]')
        await page.wait_for_timeout(360)
        print("选中后折叠行文字:", await page.inner_text("#pfPickerName"))
        print("保存后平台 key 检查:", await page.evaluate("$('#pfPanel').hidden"))

        # ── 填内容并保存 ──
        await page.fill("#fName", "微信")
        await page.fill("#fAccount", "yg_wx")
        await page.fill("#fPassword", "Wx@2026Pass")
        await page.click("#editorSave")
        await page.wait_for_timeout(900)
        entry = await page.evaluate("state.entries[0] ? {p: state.entries[0].platform, t: state.entries[0].type} : null")
        print("落库条目:", entry)

        # ── 开启指纹 ──
        await page.click('#tabbar button[data-tab="settings"]')
        await page.wait_for_timeout(500)
        await page.click("#setBio")
        await page.wait_for_timeout(1400)
        modal = await page.evaluate("!$('#modalScrim').hidden")
        if modal:
            await page.fill("#modalInput", PIN)
            await page.click("#modalOk")
            await page.wait_for_timeout(1500)
        print("指纹已开启:", await page.evaluate("cfg.bio"))
        print("指纹按钮可见:", await page.evaluate("!$('#bioBtn').hidden"))
        print("锁屏副标:", await page.evaluate("$('#lockSub').textContent"))

        # ── 指纹优先：锁屏后任意一次触碰就自动解锁 ──
        await page.evaluate("lockApp()")
        await page.wait_for_timeout(900)
        unlocked_before = await page.evaluate("state.unlocked")
        print("锁屏后 bioHooks 布防数:", await page.evaluate("bioHooks.length"))
        if not unlocked_before:
            # 模拟用户在锁屏上的一次真实触碰（WebAuthn 需要手势）
            await page.mouse.click(195, 430)
        await page.wait_for_timeout(2400)
        unlocked_after = await page.evaluate("state.unlocked")
        print("锁屏时 unlocked:", unlocked_before, "→ 触碰后:", unlocked_after)
        await page.screenshot(path=SHOTS + r"\30-bio-gesture-unlock.png")

        errs = await page.evaluate("window.__errs || []")
        print("页面错误:", errs or "无")

        # ── 冷启动：重载页面后应自动弹指纹并直接进库 ──
        await page.reload()
        await page.wait_for_timeout(3000)
        print("重载后自动解锁:", await page.evaluate("state.unlocked"))
        print("重载后锁屏是否隐藏:", await page.evaluate("$('#lockScreen').hidden"))
        await page.screenshot(path=SHOTS + r"\31-reload-bio-auto.png")

        await browser.close()


asyncio.run(main())