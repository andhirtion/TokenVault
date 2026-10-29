"""指纹解锁验收：用 CDP 虚拟认证器模拟「系统指纹」。

跑法：
    cd TokenVault && python tools/shoot_bio.py
产出：
    shots/17-settings-bio.png   设置页里的「指纹 / 面容解锁」开关
    shots/18-lock-bio.png       锁屏上的指纹按钮
    shots/19-bio-unlocked.png   指纹验证后直接进入
"""
import os
import sys
import time

from playwright.sync_api import sync_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "shots")
URL = "http://localhost:8777/index.html"
os.makedirs(SHOTS, exist_ok=True)


def tap(page, keys):
    for k in keys:
        page.click(f'[data-k="{k}"]')
        page.wait_for_timeout(90)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True,
                                    args=["--allow-features=WebAuthentication"])
        ctx = browser.new_context(viewport={"width": 390, "height": 844},
                                  device_scale_factor=3, locale="zh-CN")
        page = ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)

        # 装一个虚拟平台认证器（带 UV，等价于系统指纹）
        cdp = ctx.new_cdp_session(page)
        cdp.send("WebAuthn.enable", {"enableUI": False})
        auth = cdp.send("WebAuthn.addVirtualAuthenticator", {
            "options": {
                "protocol": "ctap2",
                "ctap2Version": "ctap2_1",
                "transport": "internal",
                "hasResidentKey": True,
                "hasUserVerification": True,
                "isUserVerified": True,
                "automaticPresenceSimulation": True,
            }
        })
        print("虚拟认证器:", auth.get("authenticatorId"))

        page.goto(URL)
        page.evaluate("localStorage.clear()")
        page.reload()
        page.wait_for_timeout(500)

        # 建库
        tap(page, "123456")
        page.wait_for_timeout(600)
        tap(page, "123456")
        page.wait_for_timeout(1200)
        print("建库完成，指纹支持:", page.evaluate("(async()=>await bioAvailable())()"))

        # 进设置页开指纹
        page.click('[data-tab="settings"]')
        page.wait_for_timeout(700)
        page.screenshot(path=os.path.join(SHOTS, "17-settings-bio.png"))
        # 注意：开关的 onchange 会先把它复位（真的开启成功才勾上），不能用 page.check()
        page.click("#setBio")
        page.wait_for_timeout(1200)
        # 若弹出「验证访问密码」，填进去
        if page.evaluate("!document.querySelector('#modalScrim').hidden"):
            page.fill("#modalInput", "123456")
            page.click("#modalOk")
            page.wait_for_timeout(2500)
        else:
            page.wait_for_timeout(1500)
        print("cfg.bio =", page.evaluate("cfg.bio"), "| bioId 长度 =",
              page.evaluate("(cfg.bioId||'').length"),
              "| bioKey 落盘 =", page.evaluate("!!cfg.bioKey"))
        page.screenshot(path=os.path.join(SHOTS, "17b-bio-on.png"))

        # 锁定 → 锁屏应出现指纹按钮
        page.evaluate("lockApp()")
        page.wait_for_timeout(900)
        vis = page.evaluate("!document.querySelector('#bioBtn').hidden")
        print("锁屏指纹按钮可见:", vis)
        page.screenshot(path=os.path.join(SHOTS, "18-lock-bio.png"))

        # 点指纹解锁
        if vis:
            page.click("#bioBtn")
            page.wait_for_timeout(2200)
            unlocked = page.evaluate("state.unlocked")
            print("指纹解锁结果:", unlocked)
            page.screenshot(path=os.path.join(SHOTS, "19-bio-unlocked.png"))
            if not unlocked:
                print("!! 指纹解锁未成功")

        print("页面错误:", errors[:6] if errors else "无")
        browser.close()


if __name__ == "__main__":
    sys.exit(main())