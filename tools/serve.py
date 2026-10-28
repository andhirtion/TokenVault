"""
TokenVault 本地静态服务器
- 修正 Windows 注册表把 .js 映射成 text/plain 导致 Service Worker 注册失败的问题
- 只监听 127.0.0.1，不对外暴露
用法: python tools/serve.py [端口，默认 8777]
"""
import http.server, os, socketserver, sys, mimetypes

mimetypes.add_type('text/javascript', '.js')
mimetypes.add_type('application/json', '.webmanifest')
mimetypes.add_type('image/svg+xml', '.svg')

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'web')
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8777


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Service-Worker-Allowed', '/')
        super().end_headers()

    def log_message(self, fmt, *args):
        pass  # 安静模式


socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(('127.0.0.1', PORT), Handler) as httpd:
    print(f'TokenVault 已启动 → http://127.0.0.1:{PORT}/index.html')
    print(f'根目录: {os.path.normpath(ROOT)}')
    print('Ctrl+C 停止')
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print('\n已停止')