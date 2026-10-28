/* 校验 Web 版「获取令牌」解析层：migration protobuf / 混合文本 / 裸 Base32 */
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(path.join(__dirname, '..', 'web', 'app.js'), 'utf8');

// 抽出纯逻辑区（ICONS ... parseAnyOtp / addParsedTokens 之前）
const start = src.indexOf('const ICONS');
const end = src.indexOf('/* ───────────────── 加密存储');
if (start < 0 || end < 0) { console.error('切片失败'); process.exit(1); }
let logic = src.slice(start, end);

// addParsedTokens 依赖 state/persist/renderList，这里只要解析层
const cut = logic.indexOf('/* 把解析结果去重入库 */');
if (cut > 0) logic = logic.slice(0, cut);

const prelude = `
const PLATFORMS_PLACEHOLDER = null;
`;
const api = new Function(prelude + logic + `
  return { parseAnyOtp, parseOtpMigration, b32encode, b32decode, isValidSecret,
           parseOtpAuth, PLATFORMS, B32_ALPHABET };
`)();

let pass = 0, fail = 0;
function eq(name, got, want) {
  const g = JSON.stringify(got), w = JSON.stringify(want);
  if (g === w) { pass++; console.log('  ✓ ' + name); }
  else { fail++; console.log('  ✗ ' + name + '\n      得到 ' + g + '\n      期望 ' + w); }
}
function ok(name, cond, info = '') {
  if (cond) { pass++; console.log('  ✓ ' + name); }
  else { fail++; console.log('  ✗ ' + name + (info ? '  (' + info + ')' : '')); }
}

/* ── 构造 migration protobuf ── */
function varint(v) {
  const out = [];
  let n = v;
  do { let b = n & 0x7f; n = Math.floor(n / 128); if (n > 0) b |= 0x80; out.push(b); } while (n > 0);
  return out;
}
function fBytes(field, data) {
  const arr = Array.from(data);
  return [...varint((field << 3) | 2), ...varint(arr.length), ...arr];
}
function fVarint(field, v) { return [...varint((field << 3) | 0), ...varint(v)]; }
function params(secret, name, issuer, algo = 1, digits = 1, type = 2) {
  const body = [
    ...fBytes(1, secret),
    ...fBytes(2, Array.from(Buffer.from(name, 'utf8'))),
    ...fBytes(3, Array.from(Buffer.from(issuer, 'utf8'))),
    ...fVarint(4, algo), ...fVarint(5, digits), ...fVarint(6, type)
  ];
  return body;
}
function migrationUri(list) {
  let body = [];
  for (const p of list) body = body.concat(fBytes(1, p));
  const b64 = Buffer.from(body).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return 'otpauth-migration://offline?data=' + b64;
}

const secret20 = Buffer.from(Array.from({ length: 20 }, (_, i) => (i * 7 + 3) & 0xff));
const secret20b32 = api.b32encode(new Uint8Array(secret20));
const secret20c = Buffer.from(Array.from({ length: 20 }, (_, i) => (i * 11 + 5) & 0xff));
const secret20cB32 = api.b32encode(new Uint8Array(secret20c));

console.log('\n[1] Base32 编码');
eq('b32encode(20B) 长度 32', secret20b32.length, 32);
ok('b32encode 只含合法字符', /^[A-Z2-7]{32}$/.test(secret20b32), secret20b32);
eq('b32encode → b32decode 往返', Array.from(api.b32decode(secret20b32)), Array.from(secret20));

console.log('\n[2] migration 解析');
const uri2 = migrationUri([
  params(secret20, 'alice@example.com', 'GitHub'),
  params(secret20c, 'bob@example.com', 'Steam')
]);
const m2 = api.parseOtpMigration(uri2);
eq('两条账户', m2.length, 2);
eq('第一条平台', m2[0].platform, 'github');
eq('第一条密钥', m2[0].secret, secret20b32);
eq('第二条是 steam', m2[1].platform, 'steam');
eq('第二条密钥', m2[1].secret, secret20cB32);
eq('steam 5 位', m2[1].digits, 5);
eq('steam charset', m2[1].charset, 'steam');

const mA = api.parseOtpMigration(migrationUri([
  params(Buffer.from(secret20), 'me@gmail.com', 'Gmail', 2, 2)
]));
eq('SHA256+8位 算法', mA[0].algo, 'SHA256');
eq('SHA256+8位 位数', mA[0].digits, 8);
eq('Gmail 归到 google', mA[0].platform, 'google');
eq('account 剥离前缀', mA[0].account, 'me@gmail.com');

eq('HOTP 被跳过', api.parseOtpMigration(migrationUri([
  params(Buffer.from(secret20), 'x', 'Y', 1, 1, 1)
])).length, 0);

console.log('\n[2b] 平台别名识别');
function platformOf(issuer, name = 'a@b.c') {
  const r = api.parseOtpMigration(migrationUri([params(Buffer.from(secret20), name, issuer)]));
  return r.length ? r[0].platform : null;
}
eq('Gmail → google', platformOf('Gmail'), 'google');
eq('Outlook → microsoft', platformOf('Microsoft', 'a@outlook.com'), 'microsoft');
eq('iCloud → apple', platformOf('iCloud'), 'apple');
eq('Xbox 不误判成 X', platformOf('Xbox Live'), 'xbox');
eq('Twitter 有独立平台', platformOf('Twitter'), 'twitter');
eq('未知 issuer → custom', platformOf('某个小网站'), 'custom');
eq('Amazon Web Services → amazon', platformOf('Amazon Web Services'), 'amazon');

console.log('\n[3] 容错');
eq('非 migration 输入', api.parseOtpMigration('otpauth://totp/x?secret=AAAA').length, 0);
eq('空 data', api.parseOtpMigration('otpauth-migration://offline?data=').length, 0);
eq('乱码 data', api.parseOtpMigration('otpauth-migration://offline?data=%%%').length, 0);
ok('带 padding 的 base64', api.parseOtpMigration(
  'otpauth-migration://offline?data=' + Buffer.from(
    fBytes(1, params(Buffer.from(secret20), 'a', 'GitHub'))
  ).toString('base64')).length === 1);

console.log('\n[4] parseAnyOtp 混合文本');
const mixed = '看这个 ' + uri2 + ' 还有 otpauth://totp/Discord:carol?secret=JBSWY3DPEHPK3PXP&issuer=Discord 结束';
const mm = api.parseAnyOtp(mixed);
eq('混合文本 3 条', mm.length, 3);
ok('含 github', mm.some(x => x.platform === 'github'));
ok('含 steam', mm.some(x => x.platform === 'steam'));
ok('含 discord', mm.some(x => x.platform === 'discord'));

const htmlEsc = api.parseAnyOtp('otpauth://totp/GitHub:dan?secret=JBSWY3DPEHPK3PXP&amp;issuer=GitHub&amp;algorithm=SHA1');
eq('HTML 实体反转义后能解析', htmlEsc.length, 1);
eq('反转义后 platform', htmlEsc[0].platform, 'github');

const dup = api.parseAnyOtp(
  'otpauth://totp/A?secret=JBSWY3DPEHPK3PXP otpauth://totp/B?secret=JBSWY3DPEHPK3PXP'
);
eq('同 secret 去重', dup.length, 1);

console.log('\n[5] 裸 Base32');
const raw = api.parseAnyOtp('JBSWY3DPEHPK3PXP');
eq('裸密钥 1 条', raw.length, 1);
eq('裸密钥平台 custom', raw[0].platform, 'custom');
eq('裸密钥保留原文', raw[0].secret, 'JBSWY3DPEHPK3PXP');

eq('垃圾文本不给结果', api.parseAnyOtp('hello world 你好').length, 0);
eq('太短的密钥拒绝', api.parseAnyOtp('ABC').length, 0);
eq('null 安全', api.parseAnyOtp(null).length, 0);

console.log('\n' + (fail === 0 ? '全部通过' : '有失败') + `：${pass} 通过 / ${fail} 失败`);
process.exit(fail === 0 ? 0 : 1);