"""账号密码条目 + 更名 + 指纹优先 验收。

跑法：
    cd TokenVault && python -m http.server 8777 -d web   # 另开一个终端
    cd TokenVault && python tools/shoot_login.py
产出：
    shots/20-editor-login.png    编辑器里的「账号密码」类型
    shots/21-list-login.png      列表里的账号密码条目
    shots/22-detail-login.png    详情页密码隐藏 / 显示
    shots/23-bio-auto.png        重新打开后自动唤起指纹（指纹优先）
"""
import os
import sys

from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "shots")
URL = "http://localhost:8777/index.html"
os.makedirs(SHOTS, exist_ok=True)


def tap(page, keys):
    for k in keys:
        page.click(f'[data-k="{k}"]')
        page.wait_for_timeout(85)


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

        cdp = ctx.new_cdp_session(page)
        cdp.send("WebAuthn.enable", {"enableUI": False})
        cdp.send("WebAuthn.addVirtualAuthenticator", {
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

        page.goto(URL)
        page.evaluate("localStorage.clear()")
        page.reload()
        page.wait_for_timeout(500)

        # 建库 123456
        tap(page, "123456")
        page.wait_for_timeout(600)
        tap(page, "123456")
        page.wait_for_timeout(1300)
        print("标题:", page.title())

        # 加一条账号密码
        page.click("#fab")
        page.wait_for_timeout(600)
        page.click('#editorSeg button[data-kind="login"]')
        page.wait_for_timeout(300)
        pwFieldVisible = page.evaluate("!document.querySelector('#passwordField').hidden")
        print("密码字段可见:", pwFieldVisible)
        page.fill("#fName", "GitHub")
        page.fill("#fAccount", "yg@example.com")
        page.fill("#fPassword", "S3cr3t-Pa55w0rd!")
        page.click("#togglePassword")
        page.wait_for_timeout(200)
        print("类型按钮数:", page.evaluate("document.querySelectorAll('#editorSeg button').length"))
        page.evaluate("document.querySelector('.editor-scroll').scrollTop = 0")
        page.wait_for_timeout(250)
        page.screenshot(path=os.path.join(SHOTS, "20-editor-login.png"))
        page.click("#editorSave")
        page.wait_for_timeout(900)

        # 列表
        count = page.evaluate("state.entries.length")
        kind = page.evaluate("state.entries[0] && state.entries[0].type")
        pwdSaved = page.evaluate("state.entries[0] && state.entries[0].password")
        print(f"条目数: {count} | 类型: {kind} | 密码落库: {pwdSaved!r}")
        page.screenshot(path=os.path.join(SHOTS, "21-list-login.png"))

        # 详情：默认遮住，点显示
        page.click(".card")
        page.wait_for_timeout(700)
        masked = page.evaluate("document.querySelector('#detailPw') && document.querySelector('#detailPw').textContent")
        print("初始显示:", repr(masked))
        page.click("#detailPwShow")
        page.wait_for_timeout(300)
        shown = page.evaluate("document.querySelector('#detailPw').textContent")
        print("点显示后:", repr(shown))
        page.screenshot(path=os.path.join(SHOTS, "22-detail-login.png"))
        page.click("#detailClose")
        page.wait_for_timeout(500)

        # 筛选 chip
        page.click('[data-filter="login"]')
        page.wait_for_timeout(400)
        print("筛选后条数:", page.evaluate("document.querySelectorAll('#list .card').length"))
        page.click('[data-filter="all"]')
        page.wait_for_timeout(300)

        # 开指纹，然后重载验证「指纹优先」自动唤起
        page.click('[data-tab="settings"]')
        page.wait_for_timeout(600)
        page.click("#setBio")
        page.wait_for_timeout(1200)
        if page.evaluate("!document.querySelector('#modalScrim').hidden"):
            page.fill("#modalInput", "123456")
            page.click("#modalOk")
            page.wait_for_timeout(2200)
        print("cfg.bio =", page.evaluate("cfg.bio"))

        page.reload()
        page.wait_for_timeout(3200)   # 自动唤起的延时 + 认证耗时
        auto = page.evaluate("state.unlocked")
        print("重载后自动指纹解锁:", auto)
        page.screenshot(path=os.path.join(SHOTS, "23-bio-auto.png"))

        print("页面错误:", errors[:6] if errors else "无")
        browser.close()


if __name__ == "__main__":
    sys.exit(main())