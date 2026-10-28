/**
 * TokenVault 算法验证：RFC 6238 / RFC 4226 测试向量 + Steam Guard
 * 运行：node tools/verify.js
 */
const fs = require('fs');
const path = require('path');
const nc = require('crypto');

const SRC = fs.readFileSync(path.join(__dirname, '..', 'web', 'app.js'), 'utf8');

// 抽取纯算法段（从 PLATFORMS 定义到 配置段之前，跳过纯 SVG 的 ICONS）
const start = SRC.indexOf('const PLATFORMS = {');
const end = SRC.indexOf('/* ───────────────── 配置 ───────────────── */');
if (start < 0 || end < 0) { console.error('抽取失败'); process.exit(1); }

let algo = SRC.slice(start, end);

// 注入 Node 环境替身
const prelude = `
const crypto = globalThis.__crypto;
const window = { isSecureContext: true };
const document = { querySelector: () => null, querySelectorAll: () => [] };
const navigator = { vibrate: () => {} };
const btoa = s => Buffer.from(s, 'binary').toString('base64');
const atob = s => Buffer.from(s, 'base64').toString('binary');
const TextEncoder = globalThis.TextEncoder;
const TextDecoder = globalThis.TextDecoder;
const localStorage = { getItem: () => null, setItem: () => {} };
`;
const epilogue = `
return { totp, b32decode, hmacSha1, sha1Bytes, randomSecret, parseOtpAuth, isValidSecret, formatCode };
`;

globalThis.__crypto = nc.webcrypto;

const factory = new Function(prelude + algo + epilogue);
const M = factory();

let pass = 0, fail = 0;
const check = (name, got, exp) => {
  const ok = String(got) === String(exp);
  console.log(`${ok ? '✅' : '❌'} ${name}  →  ${got}${ok ? '' : `  (期望 ${exp})`}`);
  ok ? pass++ : fail++;
};

function atTime(sec, fn) {
  const real = Date.now;
  Date.now = () => sec * 1000;
  try { return fn(); } finally { Date.now = real; }
}

(async () => {
  console.log('══ RFC 6238 TOTP 测试向量（HMAC-SHA1, 8 位, 30s）══');
  // 种子 ASCII "12345678901234567890" 的 Base32
  const SECRET = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
  const rfc = [
    [59,          '94287082'],
    [1111111109,  '07081804'],
    [1111111111,  '14050471'],
    [1234567890,  '89005924'],
    [2000000000,  '69279037'],
    [20000000000, '65353130'],
  ];
  for (const [t, exp] of rfc) {
    const r = await atTime(t, () => M.totp(SECRET, { digits: 8, period: 30, algo: 'SHA1' }));
    check(`T=${t}`, r.code, exp);
  }

  console.log('\n══ RFC 4226 HOTP/HMAC 截断（6 位）══');
  const r6 = await atTime(59, () => M.totp(SECRET, { digits: 6, period: 30, algo: 'SHA1' }));
  check('T=59 → 6 位', r6.code, '287082');
  const r6b = await atTime(1111111109, () => M.totp(SECRET, { digits: 6, period: 30, algo: 'SHA1' }));
  check('T=1111111109 → 6 位', r6b.code, '081804');

  console.log('\n══ Base32 解码 ══');
  const k = M.b32decode(SECRET);
  check('长度', k.length, 20);
  check('内容', Buffer.from(k).toString(), '12345678901234567890');
  check('容错：小写 + 空格', Buffer.from(M.b32decode('gezd gnbv gy3t qojq gezd gnbv gy3t qojq')).toString(), '12345678901234567890');

  console.log('\n══ 纯 JS SHA-1 / HMAC 与 OpenSSL 对比 ══');
  const msg = Buffer.from('The quick brown fox jumps over the lazy dog');
  const jsSha = Buffer.from(M.sha1Bytes(new Uint8Array(msg))).toString('hex');
  check('SHA1("quick brown fox")', jsSha, '2fd4e1c67a2d28fced849ee1bb76e7391b93eb12');

  const hkey = Buffer.from('12345678901234567890');
  const ctr = Buffer.alloc(8); ctr.writeBigUInt64BE(1n);
  const jsHmac = Buffer.from(M.hmacSha1(new Uint8Array(hkey), new Uint8Array(ctr)));
  const refHmac = nc.createHmac('sha1', hkey).update(ctr).digest();
  check('HMAC-SHA1 一致性', jsHmac.equals(refHmac) ? 'same' : 'diff', 'same');

  console.log('\n══ WebCrypto 与纯 JS 结果一致（交叉验证）══');
  const secretBuf = M.b32decode(SECRET);
  const ctr2 = Buffer.alloc(8); ctr2.writeBigUInt64BE(1234567890n / 30n);
  const wc = new Uint8Array(await nc.webcrypto.subtle.sign(
    'HMAC', await nc.webcrypto.subtle.importKey('raw', secretBuf, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']), ctr2));
  const js = M.hmacSha1(secretBuf, new Uint8Array(ctr2));
  check('两侧 HMAC 相同', Buffer.from(wc).equals(Buffer.from(js)) ? 'same' : 'diff', 'same');

  console.log('\n══ Steam Guard（5 位, 26 字符集）══');
  // 用已知种子做确定性检查：码长与字符集合法性
  const st = await M.totp(SECRET, { digits: 5, period: 30, algo: 'SHA1', charset: 'steam' });
  check('长度', st.code.length, 5);
  check('字符集合法', /^[23456789BCDFGHJKMNPQRTVWXY]{5}$/.test(st.code) ? 'valid' : 'invalid', 'valid');
  // 同一时刻两次调用必须一致
  const st2 = await M.totp(SECRET, { digits: 5, period: 30, algo: 'SHA1', charset: 'steam' });
  check('稳定可复现', st.code === st2.code ? 'same' : 'diff', 'same');

  console.log('\n══ SHA-256 / SHA-512 通路 ══');
  for (const a of ['SHA256', 'SHA512']) {
    const r = await M.totp(SECRET, { digits: 6, period: 30, algo: a });
    check(`${a} 输出 6 位数字`, /^\d{6}$/.test(r.code) ? 'ok' : r.code, 'ok');
  }

  console.log('\n══ 随机密钥与校验 ══');
  const rs = M.randomSecret(20);
  check('长度 32 字符', rs.length, 32);
  check('可解出 20 字节', M.b32decode(rs).length, 20);
  check('isValidSecret 接受', M.isValidSecret(rs) ? 'yes' : 'no', 'yes');
  check('isValidSecret 拒绝短串', M.isValidSecret('ABC') ? 'yes' : 'no', 'no');

  console.log('\n══ otpauth:// 解析 ══');
  const cases = [
    ['otpauth://totp/Google:yg@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Google', 'google', 'yg@example.com', 6, 30],
    ['otpauth://totp/GitHub:dev?secret=JBSWY3DPEHPK3PXP&digits=8&period=60&algorithm=SHA256', 'github', 'dev', 8, 60],
    ['otpauth://totp/Steam:gamer?secret=JBSWY3DPEHPK3PXP&issuer=Steam', 'steam', 'gamer', 5, 30],
    ['otpauth://totp/ACME%20Co:john%40mail.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co', 'custom', 'john@mail.com', 6, 30],
  ];
  for (const [uri, pf, acct, dg, pd] of cases) {
    const p = M.parseOtpAuth(uri);
    check(`解析 ${pf}`, p ? `${p.platform}/${p.account}/${p.digits}/${p.period}` : 'null',
          `${pf}/${acct}/${dg}/${pd}`);
  }

  console.log('\n══ 格式化 ══');
  check('6 位分组', M.formatCode('123456'), '123 456');
  check('8 位分组', M.formatCode('12345678'), '1234 5678');
  check('5 位分组', M.formatCode('ABCDE'), 'AB CDE');

  console.log('\n' + '═'.repeat(46));
  console.log(`结果：${pass} 通过 / ${fail} 失败`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('测试异常：', e); process.exit(1); });