"""钥匣 1.2.0 界面截图：动态码 + 账号密码 + 笔记混合列表。

跑法：
    cd TokenVault && python -m http.server 8777 -d web   # 另开一个终端
    cd TokenVault && python tools/shoot_v12.py
产出：
    shots/24-mixed-list.png      混合列表（动态码 / 账号密码 / 笔记）
    shots/25-detail-login.png    账号密码详情（密码可见）
    shots/26-editor-login.png    编辑器里的账号密码类型
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
        page.wait_for_timeout(80)


def add_code(page, platform, name, secret):
    page.click("#fab")
    page.wait_for_timeout(550)
    page.click('#editorSeg button[data-kind="code"]')
    page.wait_for_timeout(200)
    page.click(f'#platformGrid .pf-opt[data-pf="{platform}"]')
    page.wait_for_timeout(180)
    page.fill("#fName", name)
    page.fill("#fSecret", secret)
    page.click("#editorSave")
    page.wait_for_timeout(800)


def add_login(page, platform, name, account, password):
    page.click("#fab")
    page.wait_for_timeout(550)
    page.click('#editorSeg button[data-kind="login"]')
    page.wait_for_timeout(200)
    page.click(f'#platformGrid .pf-opt[data-pf="{platform}"]')
    page.wait_for_timeout(180)
    page.fill("#fName", name)
    page.fill("#fAccount", account)
    page.fill("#fPassword", password)
    page.click("#editorSave")
    page.wait_for_timeout(800)


def add_note(page, name, text):
    page.click("#fab")
    page.wait_for_timeout(550)
    page.click('#editorSeg button[data-kind="note"]')
    page.wait_for_timeout(200)
    page.fill("#fName", name)
    page.fill("#fNote", text)
    page.click("#editorSave")
    page.wait_for_timeout(800)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        ctx = browser.new_context(viewport={"width": 390, "height": 844},
                                  device_scale_factor=3, locale="zh-CN")
        page = ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)

        page.goto(URL)
        page.evaluate("localStorage.clear()")
        page.reload()
        page.wait_for_timeout(450)

        tap(page, "123456")
        page.wait_for_timeout(600)
        tap(page, "123456")
        page.wait_for_timeout(1300)

        add_code(page, "google", "Google", "JBSWY3DPEHPK3PXP")
        add_code(page, "steam", "Steam", "KRSXG5CTMVRXEZLU")
        add_login(page, "github", "GitHub", "yg@example.com", "S3cr3t-Pa55w0rd!")
        add_login(page, "microsoft", "Microsoft", "yg@outlook.com", "Tr0ub4dor&3")
        add_note(page, "恢复码", "备用恢复码：A1B2-C3D4-E5F6\n下次换机前记得先导出备份")

        # 收藏无效（验证码卡片点击=复制），改成等 toast 消失后直接截干净列表
        page.wait_for_timeout(2600)

        print("条目数:", page.evaluate("state.entries.length"))
        print("类型分布:", page.evaluate("state.entries.map(e => e.type).join(',')"))
        page.evaluate("window.scrollTo(0,0)")
        page.wait_for_timeout(300)
        page.screenshot(path=os.path.join(SHOTS, "24-mixed-list.png"))

        # 打开 GitHub（列表第 3 张卡）详情，展开密码
        page.locator("#list .card").nth(2).click()
        page.wait_for_timeout(700)
        if page.evaluate("document.querySelector('#detailPwShow') !== null"):
            page.click("#detailPwShow")
            page.wait_for_timeout(350)
        print("详情标题:", page.evaluate("document.querySelector('#detailName') && document.querySelector('#detailName').textContent"))
        page.screenshot(path=os.path.join(SHOTS, "25-detail-login.png"))
        page.click("#detailClose")
        page.wait_for_timeout(450)

        # 编辑器账号密码类型
        page.click("#fab")
        page.wait_for_timeout(550)
        page.click('#editorSeg button[data-kind="login"]')
        page.wait_for_timeout(220)
        page.click('#platformGrid .pf-opt[data-pf="github"]')
        page.wait_for_timeout(200)
        page.fill("#fName", "GitHub")
        page.fill("#fAccount", "yg@example.com")
        page.fill("#fPassword", "S3cr3t-Pa55w0rd!")
        page.click("#togglePassword")
        page.wait_for_timeout(250)
        page.evaluate("document.querySelector('.editor-scroll').scrollTop = 0")
        page.wait_for_timeout(300)
        page.screenshot(path=os.path.join(SHOTS, "26-editor-login.png"))

        print("页面错误:", [e for e in errors if "MIME" not in e][:5] or "无")
        browser.close()


if __name__ == "__main__":
    sys.exit(main())