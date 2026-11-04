#!/usr/bin/env python3
"""钥匣 WebDAV 链路实测：填地址 → 测试连接 → 立即备份 → 清本地库 → 从云端恢复。

前置： python -m http.server 8777 -d web
       python tools/dav_mock.py 8778
跑法：  $env:PYTHONIOENCODING='utf-8'; python tools/shoot_v15.py
"""
import sys
import time

from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

URL = "http://localhost:8777/index.html"
DAV_URL = "http://127.0.0.1:8778/"
DAV_USER = "yg"
DAV_PASS = "dav-test-pass"
PIN = "123456"


def step(msg):
    print(f"--- {msg}", flush=True)


def enter_app(page, go="settings"):
    """从主界面进入并完成建库/解锁。"""
    page.wait_for_selector("#home", state="visible", timeout=15000)
    page.eval_on_selector(f'.home-card[data-go="{go}"]', "el => el.click()")
    page.wait_for_timeout(900)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        ctx = browser.new_context(
            viewport={"width": 390, "height": 844},
            device_scale_factor=3,
            is_mobile=True,
            has_touch=True,
            locale="zh-CN",
        )
        ctx.add_init_script("try{localStorage.clear()}catch(e){}")
        page = ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.goto(URL, wait_until="domcontentloaded")

        # 1) 首次进入 → 建库
        step("建库")
        page.eval_on_selector('.home-card[data-go="settings"]', "el => el.click()")
        page.wait_for_timeout(1000)
        page.wait_for_selector("#lockScreen", state="visible", timeout=15000)
        for d in PIN:
            page.click(f'#keypad button[data-k="{d}"]')
            page.wait_for_timeout(70)
        page.wait_for_timeout(500)
        for d in PIN:
            page.click(f'#keypad button[data-k="{d}"]')
            page.wait_for_timeout(70)
        page.wait_for_timeout(1200)
        has_vault = page.evaluate("() => !!(window.cfg && window.cfg.hasVault)")
        print(f"建库成功: {has_vault}", flush=True)

        # 2) 先加一条 login 条目，作为待备份数据
        step("添加一条账号密码")
        page.click('#tabbar button[data-tab="vault"]')
        page.wait_for_timeout(600)
        page.click("#fab")
        page.wait_for_timeout(600)
        page.click('#editorSeg button[data-kind="login"]')
        page.wait_for_timeout(220)
        page.fill("#fName", "云端测试")
        page.fill("#fAccount", "cloud@example.com")
        page.fill("#fPassword", "DavPass!2026")
        page.click("#editorSave")
        page.wait_for_timeout(900)
        count0 = page.evaluate("() => (window.state && state.entries.length) || 0")
        print(f"本地条目数: {count0}", flush=True)

        # 3) 进设置 → 填 WebDAV
        step("填写 WebDAV 配置")
        page.click('#tabbar button[data-tab="settings"]')
        page.wait_for_timeout(700)
        page.fill("#davUrl", DAV_URL)
        page.fill("#davUser", DAV_USER)
        page.fill("#davPass", DAV_PASS)
        page.fill("#davPath", "KeyCoffer/backup.json")
        page.wait_for_timeout(300)
        cfg_dav = page.evaluate("() => JSON.parse(localStorage.getItem('tokenvault_dav_v1') || '{}')")
        print(f"配置落盘: url={cfg_dav.get('url')} user={cfg_dav.get('user')} path={cfg_dav.get('path')}", flush=True)

        # 4) 测试连接
        step("测试连接")
        page.click("#davTestBtn")
        page.wait_for_timeout(2200)
        print(f"测试连接结果: {page.inner_text('#davState')}", flush=True)

        # 5) 立即备份
        step("立即备份")
        page.click("#davUploadBtn")
        page.wait_for_timeout(2600)
        print(f"备份结果: {page.inner_text('#davState')}", flush=True)
        print(f"底部提示: {page.inner_text('#davFoot')}", flush=True)
        page.screenshot(path="shots/40-webdav-upload.png")

        # 6) 清空本地条目 → 从云端恢复
        step("清空本地后从云端恢复")
        page.evaluate("() => { state.entries = []; persist(); renderList(); }")
        page.wait_for_timeout(500)
        print(f"清空后条目数: {page.evaluate('() => state.entries.length')}", flush=True)
        page.click("#davRestoreBtn")
        page.wait_for_timeout(3000)
        state_txt = page.inner_text("#davState")
        print(f"恢复结果: {state_txt}", flush=True)
        # 若弹出密码框（云端备份用的密码与 PIN 不同才会弹），填 PIN
        if page.query_selector("#modalInput") and page.is_visible("#modalScrim"):
            page.fill("#modalInput", PIN)
            page.click("#modalOk")
            page.wait_for_timeout(2200)
            print(f"补填密码后: {page.inner_text('#davState')}", flush=True)

        count1 = page.evaluate("() => state.entries.length")
        restored = page.evaluate("() => state.entries.map(e => e.name + '|' + e.password)")
        print(f"恢复后条目数: {count1}", flush=True)
        print(f"恢复内容: {restored}", flush=True)

        # 7) 校验云端文件确实是密文
        step("检查云端文件")
        import urllib.request
        import base64 as b64
        req = urllib.request.Request(
            DAV_URL + "KeyCoffer/backup.json",
            headers={"Authorization": "Basic " + b64.b64encode(f"{DAV_USER}:{DAV_PASS}".encode()).decode()},
        )
        try:
            with urllib.request.urlopen(req, timeout=8) as r:
                body = r.read().decode("utf-8")
            import json
            doc = json.loads(body)
            payload = doc.get("payload", {})
            ct = payload.get("ct", "")
            print(f"云端 doc: app={doc.get('app')} count={doc.get('count')} alg={payload.get('alg')}", flush=True)
            print(f"密文长度: {len(ct)} | 含明文密码: {'DavPass!2026' in body} | 含条目名: {'云端测试' in body}", flush=True)
        except Exception as e:
            print(f"读取云端失败: {e}", flush=True)

        page.screenshot(path="shots/41-webdav-restored.png")
        page.wait_for_timeout(200)
        print(f"页面错误: {errors or '无'}", flush=True)
        browser.close()


if __name__ == "__main__":
    t0 = time.time()
    main()
    print(f"耗时 {time.time() - t0:.1f}s", flush=True)