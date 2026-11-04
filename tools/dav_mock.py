#!/usr/bin/env python3
"""最小 WebDAV 服务端（内存），只为验收钥匣的 WebDAV 备份链路。

支持 OPTIONS / PROPFIND / MKCOL / PUT / GET / DELETE，带 CORS 头。
用法： python tools/dav_mock.py 8778
"""
import sys
import base64
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STORE = {}          # path -> bytes
DIRS = {"/"}        # 已存在目录
USER = "yg"
PASS = "dav-test-pass"


def norm(p):
    p = p.split("?")[0]
    if not p.startswith("/"):
        p = "/" + p
    return p


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "DavMock/1.0"

    def log_message(self, fmt, *args):   # 安静点，只保留错误
        pass

    # ---------- 公共 ----------
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Depth, Content-Type, Accept, X-Requested-With")
        self.send_header("Access-Control-Allow-Methods", "OPTIONS, GET, PUT, DELETE, PROPFIND, MKCOL, HEAD")
        self.send_header("Access-Control-Max-Age", "600")
        self.send_header("Access-Control-Expose-Headers", "Content-Length, Content-Type")

    def _authed(self):
        head = self.headers.get("Authorization", "")
        if not head.startswith("Basic "):
            return False
        try:
            raw = base64.b64decode(head[6:]).decode("utf-8")
        except Exception:
            return False
        return raw == f"{USER}:{PASS}"

    def _send(self, code, body=b"", ctype="text/plain; charset=utf-8", extra=None):
        self.send_response(code)
        self._cors()
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if body and self.command != "HEAD":
            self.wfile.write(body)

    def _deny(self):
        self._send(401, b"unauthorized", extra={"WWW-Authenticate": 'Basic realm="DavMock"'})

    # ---------- 方法 ----------
    def do_OPTIONS(self):
        self._send(204)

    def do_PROPFIND(self):
        if not self._authed():
            return self._deny()
        p = norm(self.path)
        exists = p in STORE or p in DIRS or p.rstrip("/") in DIRS or (p + "/") in DIRS
        if not exists:
            return self._send(404, b"not found")
        is_dir = not p.endswith(".json")
        xml = f"""<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>{p}</d:href>
    <d:propstat>
      <d:prop>
        <d:displayname>{p.rstrip('/').split('/')[-1]}</d:displayname>
        <d:resourcetype>{'<d:collection/>' if is_dir else ''}</d:resourcetype>
        <d:getcontentlength>{len(STORE.get(p, b''))}</d:getcontentlength>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"""
        self._send(207, xml.encode("utf-8"), "application/xml; charset=utf-8")

    def do_MKCOL(self):
        if not self._authed():
            return self._deny()
        p = norm(self.path).rstrip("/") + "/"
        if p in DIRS:
            return self._send(405, b"already exists")
        DIRS.add(p)
        self._send(201, b"created")

    def do_PUT(self):
        if not self._authed():
            return self._deny()
        p = norm(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        data = self.rfile.read(length) if length else b""
        existed = p in STORE
        STORE[p] = data
        self._send(204 if existed else 201, b"")

    def do_GET(self):
        if not self._authed():
            return self._deny()
        p = norm(self.path)
        if p not in STORE:
            return self._send(404, b"not found")
        self._send(200, STORE[p], "application/json; charset=utf-8")

    def do_HEAD(self):
        if not self._authed():
            return self._deny()
        p = norm(self.path)
        self._send(200 if p in STORE else 404, b"")

    def do_DELETE(self):
        if not self._authed():
            return self._deny()
        p = norm(self.path)
        STORE.pop(p, None)
        self._send(204)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8778
    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"DavMock listening on http://127.0.0.1:{port}/ (user={USER} pass={PASS})", flush=True)
    srv.serve_forever()