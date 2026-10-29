"""调试：为什么 enableBiometric 没生效。"""
import json
import os

from playwright.sync_api import sync_playwright

URL = "http://localhost:8777/index.html"

JS = """(async () => {
  const out = { supported: bioSupported(), available: await bioAvailable(),
                secure: window.isSecureContext, hasSubtle: HAS_SUBTLE };
  out.currentPin = currentPin;
  try {
    const cred = await navigator.credentials.create({
      publicKey: {
        challenge: crypto.getRandomValues(new Uint8Array(32)),
        rp: { name: 'TokenVault' },
        user: { id: crypto.getRandomValues(new Uint8Array(16)), name: 'tv', displayName: 'TokenVault' },
        pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
        authenticatorSelection: { authenticatorAttachment: 'platform', userVerification: 'required' },
        timeout: 20000,
        attestation: 'none',
        extensions: { prf: { eval: { first: new TextEncoder().encode('tokenvault/biometric/v1') } } }
      }
    });
    out.create = cred ? 'ok' : 'null';
    out.ext = cred ? JSON.stringify(cred.getClientExtensionResults()) : '';
  } catch (e) { out.err = e.name + ' / ' + e.message; }
  return out;
})()"""


def main():
    with sync_playwright() as p:
        b = p.chromium.launch(channel="msedge", headless=True)
        ctx = b.new_context(viewport={"width": 390, "height": 844}, locale="zh-CN")
        page = ctx.new_page()
        cdp = ctx.new_cdp_session(page)
        cdp.send("WebAuthn.enable", {"enableUI": False})
        cdp.send("WebAuthn.addVirtualAuthenticator", {"options": {
            "protocol": "ctap2", "ctap2Version": "ctap2_1", "transport": "internal",
            "hasResidentKey": True, "hasUserVerification": True, "isUserVerified": True,
            "automaticPresenceSimulation": True}})
        page.goto(URL)
        page.evaluate("localStorage.clear()")
        page.reload()
        page.wait_for_timeout(400)
        # 建库
        for k in "123456":
            page.click(f'[data-k="{k}"]')
            page.wait_for_timeout(70)
        page.wait_for_timeout(500)
        for k in "123456":
            page.click(f'[data-k="{k}"]')
            page.wait_for_timeout(70)
        page.wait_for_timeout(1200)
        print(json.dumps(page.evaluate(JS), ensure_ascii=False, indent=2))
        b.close()


if __name__ == "__main__":
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    main()