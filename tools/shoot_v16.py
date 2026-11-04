# -*- coding: utf-8 -*-
"""钥匣 1.4.0 验收（关于页 / 检查更新 / 多源兜底）。

先启动静态服务器：python -m http.server 8777 -d web
再运行：python tools/shoot_v16.py

版本清单走 page.route 假数据，不真的去打 GitHub，避免网络波动影响结论。
"""
import asyncio
import json
import os
import sys
from pathlib import Path

from playwright.async_api import async_playwright

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

ROOT = Path(__file__).resolve().parents[1]
SHOTS = str(ROOT / "shots")
URL = "http://localhost:8777/index.html"

# 由用例改写：返回哪份清单
STATE = {"payload": None, "hit": []}


def make_payload(version, size=2443013):
    return {
        "app": "TokenVault",
        "name": "钥匣 KeyCoffer",
        "version": version,
        "code": 8 if version == "1.4.0" else 9,
        "notes": ["新增 GitHub 仓库入口", "新增检查更新与应用内更新", "下载源自动切镜像"],
        "repo": "https://github.com/andhirtion/TokenVault",
        "web": "https://andhirtion.github.io/TokenVault/",
        "release": f"https://github.com/andhirtion/TokenVault/releases/tag/v{version}",
        "apk": {
            "name": f"TokenVault-{version}.apk",
            "size": size,
            "sha256": "0" * 64,
            "urls": [f"https://github.com/andhirtion/TokenVault/releases/download/v{version}/TokenVault-{version}.apk"],
        },
    }


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

        async def handle(route):
            STATE["hit"].append(route.request.url[:70])
            if STATE["payload"] is None:
                await route.abort()
            else:
                await route.fulfill(
                    status=200,
                    content_type="application/json",
                    body=json.dumps(STATE["payload"], ensure_ascii=False),
                )

        await page.route("**/version.json*", handle)
        await page.route("https://api.github.com/**", handle)

        await page.goto(URL)
        await page.wait_for_timeout(800)

        # 先建库（无库状态下点设置会被锁屏拦住），再进设置页
        await page.click('.home-card[data-go="code"]')
        await page.wait_for_timeout(1100)
        for _ in range(2):
            for d in "123456":
                await page.click(f'#keypad button[data-k="{d}"]')
            await page.wait_for_timeout(320)
        await page.wait_for_timeout(1400)
        print("hasVault:", await page.evaluate("cfg.hasVault"))

        await page.click("#settingsBtn")
        await page.wait_for_timeout(1000)
        if not await page.is_visible("#updBtn"):
            print("设置页没渲染，当前 tab:", await page.evaluate("state.tab"))
            print("body 片段:", (await page.eval_on_selector("body", "e => e.innerText")).strip()[:200])
        print("设置页 updBtn 可见:", await page.is_visible("#updBtn"))
        print("关于区文案:", (await page.eval_on_selector("#repoLink", "e => e.getAttribute('href')")))
        print("仓库链接正确:", (await page.eval_on_selector("#repoLink", "e => e.getAttribute('href')")) == "https://github.com/andhirtion/TokenVault")

        el = await page.query_selector("#updBtn")
        await el.scroll_into_view_if_needed()
        await page.wait_for_timeout(300)
        await page.screenshot(path=os.path.join(SHOTS, "42-about.png"))

        # ── 用例 A：有新版本 1.4.1
        STATE["payload"] = make_payload("1.4.1")
        await page.click("#updBtn")
        await page.wait_for_timeout(1800)
        new_txt = (await page.eval_on_selector(".upd-new", "e => e.textContent")).strip() if await page.is_visible(".upd-new") else ""
        print("A 发现新版本文案:", new_txt)
        print("A 有「立即更新」:", await page.is_visible("#updApplyBtn"))
        notes_cnt = await page.evaluate("document.querySelectorAll('.upd-notes li').length")
        print("A 更新说明条数:", notes_cnt)
        await page.screenshot(path=os.path.join(SHOTS, "43-update-available.png"))

        # ── 用例 B：已是最新
        STATE["payload"] = make_payload("1.4.0")
        await page.click("#updBtn")
        await page.wait_for_timeout(1500)
        ok_txt = (await page.eval_on_selector(".upd-ok", "e => e.textContent")).strip() if await page.is_visible(".upd-ok") else ""
        print("B 已是最新文案:", ok_txt)
        await page.screenshot(path=os.path.join(SHOTS, "44-update-latest.png"))

        # ── 用例 C：全部来源失败 → 报错 + 重试按钮
        STATE["payload"] = None
        await page.click("#updBtn")
        await page.wait_for_timeout(1500)
        err_txt = (await page.eval_on_selector(".upd-err", "e => e.textContent")).strip() if await page.is_visible(".upd-err") else ""
        print("C 失败文案:", err_txt[:80])
        print("C 有重试按钮:", await page.is_visible("#updRetryBtn"))
        await page.screenshot(path=os.path.join(SHOTS, "45-update-failed.png"))

        print("尝试过的来源数:", len(STATE["hit"]))
        print("页面错误:", errs if errs else "无")
        await browser.close()


asyncio.run(main())