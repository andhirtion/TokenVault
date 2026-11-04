/* 钥匣 KeyCoffer Service Worker — 首屏优先、慢网不卡、绝不缓存动态请求 */
const CACHE = 'keycoffer-v2';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.webmanifest',
  './icon.svg'
];

/* 安装：逐个缓存，任何一个失败都不阻塞 skipWaiting（弱网下 addAll 会整体失败 → 无限转圈） */
self.addEventListener('install', ev => {
  ev.waitUntil((async () => {
    try {
      const c = await caches.open(CACHE);
      await Promise.all(ASSETS.map(async u => {
        try { await c.add(new Request(u, { cache: 'reload' })); } catch (_) { /* 单个资源失败忽略 */ }
      }));
    } catch (_) { /* 缓存不可用也不阻塞 */ }
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', ev => {
  ev.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

/* 超时包装：弱网下不等太久，先给缓存 */
function withTimeout(p, ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('timeout')), ms);
    p.then(v => { clearTimeout(t); resolve(v); }, e => { clearTimeout(t); reject(e); });
  });
}

async function putCache(req, res) {
  try {
    if (res && res.ok && res.type === 'basic') {
      const copy = res.clone();
      caches.open(CACHE).then(c => c.put(req, copy)).catch(() => {});
    }
  } catch (_) {}
  return res;
}

/* 网络优先（带超时），失败回退缓存 —— 用于导航与核心脚本，保证更新能立刻生效 */
async function networkFirst(req, timeoutMs) {
  try {
    const res = await withTimeout(fetch(req), timeoutMs);
    return putCache(req, res);
  } catch (_) {
    const hit = (await caches.match(req)) || (await caches.match('./index.html'));
    if (hit) return hit;
    return new Response('离线，且本地没有缓存副本', {
      status: 503,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' }
    });
  }
}

/* 缓存优先 + 后台静默更新 —— 用于样式/图标等静态资源 */
async function cacheFirst(req) {
  const hit = await caches.match(req);
  if (hit) {
    fetch(req).then(res => putCache(req, res)).catch(() => {});
    return hit;
  }
  try {
    return putCache(req, await fetch(req));
  } catch (_) {
    const shell = await caches.match('./index.html');
    if (shell) return shell;
    return new Response('', { status: 504 });
  }
}

const NET_FIRST = /(^\/$|\/index\.html$|\/app\.js$|\/sw\.js$)/;

self.addEventListener('fetch', ev => {
  const req = ev.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // 外部请求一律不拦截

  if (req.mode === 'navigate' || NET_FIRST.test(url.pathname)) {
    // 导航 2.6s、脚本 4s 内拿不到网络就用缓存，避免首页白屏转圈
    ev.respondWith(networkFirst(req, req.mode === 'navigate' ? 2600 : 4000));
    return;
  }
  ev.respondWith(cacheFirst(req));
});