# TokenVault · 令牌保险库

**在线直接用（无需安装）：<https://andhirtion.github.io/TokenVault/>**
手机浏览器打开即可，菜单里「添加到主屏幕」就像个 App，离线也能出码。

一个**本地加密、离线生成**的两步验证（TOTP）令牌管理器，同时提供两套实现：

| 交付 | 目录 | 状态 |
| --- | --- | --- |
| 在线网页版（GitHub Pages） | [`docs/`](docs/) | **打开就用** <https://andhirtion.github.io/TokenVault/> |
| Web 版 PWA（本地） | [`web/`](web/) | 源码；`docs/` 是它的发布副本 |
| 原生安卓 App | [`android/`](android/) | Kotlin + Jetpack Compose，**已编译出成品包**：[`dist/TokenVault-1.1.1.apk`](dist/)（2.2 MB） |

> **v1.1.0 新增：四条「自动获取密钥」通道** —— 相机扫码 / 登录账号后自动抓取 / 相册图片识别 / 剪贴板粘贴。
> 详见[第四节](#四怎么把已有令牌搬进来)。令牌本体仍是本地 AES-256-GCM 加密，从不上传。**网页版同样支持扫码 / 图片 / 粘贴 / Google 迁移串。**

支持 26 个平台（Google / Microsoft / GitHub / Steam / Apple / Discord / X / Amazon / Dropbox /
Facebook / GitLab / LinkedIn / Slack / Telegram / Twitter / Reddit / Twitch / Spotify / Binance /
Cloudflare / Notion / Proton / Epic / Instagram / Xbox / 自定义），
标准 6 位 TOTP、8 位 TOTP、**Steam 5 位字母码**全部支持。

---

## 一、Web 版：现在就能用

### 1. 在线地址（最快）

打开 <https://andhirtion.github.io/TokenVault/> —— 手机上也能直接开，无需装任何东西。
登录一次 PIN 后，令牌存本地浏览器（IndexedDB），**不会上传服务器**（GitHub Pages 只托管静态文件）。
想更像 App：浏览器菜单 → 「添加到主屏幕」。

网页版的「获取」也能扫码：Chrome / Edge / Safari 支持 `BarcodeDetector` 时直接调摄像头，
不支持就用「从图片识别」（相册里的二维码截图）。

### 2. 本地起服务（改代码时用）

```bash
cd TokenVault
python tools/serve.py 8777
```

然后：

1. 电脑上打开 <http://127.0.0.1:8777/index.html>（先桌面浏览器试）；
2. 手机连**同一个 WiFi**，查电脑内网 IP（`ipconfig`），手机浏览器打开
   `http://<电脑IP>:8777/index.html`；
3. 首次进入设置 6 位 PIN → 确认 → 开始使用；
4. 手机浏览器菜单里选「添加到主屏幕 / 安装应用」，之后像原生 App 一样全屏打开，离线可用。

> `tools/serve.py` 是必要的：Windows 注册表常把 `.js` 映射成 `text/plain`，
> 那样 Service Worker 会注册失败、离线缓存不可用。用 `python -m http.server` 起服务时如果
> 控制台报 `The script has an unsupported MIME type ('text/plain')`，就是这个原因。

## 二、安卓原生 App

### 直接装（最快）

```
TokenVault/dist/TokenVault-1.1.1.apk      # 2.2 MB，正式包名 com.yg.tokenvault
TokenVault/dist/TokenVault-1.1.1-debug.apk # 20 MB，debug 版（带日志，排查用）
```

把 APK 传到手机（数据线 / 微信文件传输 / 网盘）→ 点开 → 允许「安装未知来源应用」→ 装完打开。

- 要求 **Android 8.0（API 26）** 及以上
- 用的是 debug 证书签名，Play 商店上不了，自己用完全没问题
- 装之前**先联网同步一次手机时间** —— TOTP 严格依赖系统时间

> `TokenVault-1.1.1.apk` 经过 R8 压缩混淆（2.2 MB）。万一在你的机型上启动闪退，
> 换 `TokenVault-1.1.1-debug.apk`（20 MB，未混淆）—— 两者功能完全一致，只是后者带调试符号。

### 用 Android Studio 改代码

1. Android Studio → **Open** → 选 `TokenVault/android` 目录（不是 `TokenVault`）；
2. 等 Gradle Sync 完成（首次会下 Compose 依赖，需联网，约几分钟）；
3. 手机开「USB 调试」插上，或开一个模拟器 → 点 ▶ Run。

### 命令行打包（本机已自带全套工具链，不需要装任何东西）

```bash
cd TokenVault
bash tools/build_apk.sh            # → dist/... 之前先出 app/build/outputs/apk/debug/app-debug.apk
bash tools/build_apk.sh release    # 正式包，已配 debug 签名，直接能装
bash tools/build_apk.sh test       # 跑 28 项 JVM 单元测试（TOTP 算法自检）
```

`.toolchain/` 里已经备好 **JDK 17 + Android SDK Platform 35 + Build-Tools 35.0.0 + Gradle 8.11.1**，
脚本会自己设好 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME` / 临时目录，
不污染系统环境。国内网络下 `dl.google.com` 常不通，所以 `settings.gradle.kts` 里把
**阿里云 Maven 镜像排在官方源前面**，这也是它能在这台机器上编出来的原因。

### 工程要点

- 包名 `com.yg.tokenvault`，minSdk **26**（Android 8.0）、targetSdk **35**
- Kotlin 2.0.21 + Compose BOM 2024.12.01 + Material 3
- 统一 Compose 主题，深/浅色 + 跟随系统三档
- 品牌图标全部是手写 VectorDrawable（`res/drawable/ic_brand_*.xml`），**不依赖网络图片**

---

## 三、功能

**令牌**
- 26 平台品牌图标与配色，标准 TOTP（SHA-1/256/512，6/8 位，可改周期）
- Steam Guard 专用 5 位字母码算法
- 环形倒计时 + 进度条，剩余 5 秒变红提示
- 点验证码即复制，可设置 15/30/60 秒后自动清剪贴板
- 长按卡片直接收藏，收藏置顶分组
- 「隐藏验证码」模式：列表只显圆点，点开详情才显示
- 搜索（名称/账号/备注）+ 平台筛选

**获取密钥（v1.1.0 新增，安卓版）**
- 相机扫码：CameraX + ZXing，对着 `otpauth://` 二维码即入库
- 登录账号自动获取：内置浏览器登录平台 2FA 设置页，自动识别页面里的 otpauth 链接、
  密钥文本和二维码图片，底部一键全部导入
- 相册图片识别：截图也能读，纯本地解码
- 剪贴板粘贴：`otpauth://`、`otpauth-migration://`、裸 Base32 都认
- Google Authenticator 导出的一整条 `otpauth-migration://offline?data=...` 原生解析，
  一次导入全部账号（手写 protobuf，不引第三方库）
- 浏览器里点 `otpauth://` 链接直接跳进 App 导入

**安全**
- 主密码 / PIN 经 **PBKDF2-HMAC-SHA256（25 万次迭代）** 派生 256 位密钥
- 全部令牌用 **AES-256-GCM** 加密后落盘（认证加密，密文被改即解不开）
- 换 PIN 时重新生成随机盐、整体重加密
- 自动锁定（1/3/5 分钟，切到后台开始计时）
- 「立即锁定」清空内存中的密钥
- **令牌从不上传**：所有加密、解密、验证码计算都在本机完成，没有任何服务端
- **`INTERNET` 权限的唯一用途**：内置浏览器页面。v1.1.0 的「登录账号自动获取」需要打开
  平台官网的 2FA 设置页，这个页面本身要联网；抓到的密钥立刻落到本地加密库，不回传
- 系统备份 / 设备迁移全部排除，密钥不会被云备份带走

**数据**
- 导出：AES-256-GCM 加密备份（独立密码，可放网盘）
- 导入：加密备份，或一堆 `otpauth://` 链接文本批量导入
- 安卓还支持点浏览器里的 `otpauth://` 链接直接跳进 App 导入

**工具页**
- 随机口令生成器（8–48 位，大小写/数字/符号开关）
- Base32 密钥生成（给其它验证器用）
- 验证码试算（不保存，临时核对某个密钥算得对不对）

---

## 四、怎么把已有令牌搬进来

安卓版点列表页右上角 **「获取」**（或工具箱第一张卡），会弹出四种方式：

| 方式 | 适用场景 | 怎么做 |
| --- | --- | --- |
| **扫二维码** | 正在给某个账号开启两步验证 | 网站显示二维码时，点「扫二维码」对准它，一秒入库，平台/账号/参数全自动填好 |
| **登录账号自动获取** | 想让 App 自己把密钥抓回来 | 点进去选平台（Google/微软/GitHub/Steam/Dropbox/Amazon/Epic/Discord），在 App 内置浏览器里正常登录，走到 2FA 设置页，页面上的二维码和密钥文本会被自动识别，底部出现「已捕获 N 条密钥 → 全部导入」 |
| **从图片识别** | 二维码已经截图存在相册里 | 选图片，本地解码，不上传任何图像 |
| **从剪贴板粘贴** | 手上已经有 `otpauth://` 链接或 Base32 密钥 | 复制后点这一项，自动拆出平台、账号、算法、位数、周期 |

> **关于「能不能把已经开着的 2FA 密钥直接读回来」**：这是各平台自己的限制，不是本 App 的
> 限制——Google / 微软 / GitHub 的安全设置里只会显示「已开启」，密钥本身在开启那一刻之后
> 就不再展示（GitHub 只在 setup 页给一次 secret；Steam 要走官方手机令牌流程）。所以任何
> 验证器 App（含 Authy、1Password）都拿不到「事后读取」，只能在那一次 setup 时把二维码/密钥
> 抄下来。本 App 的做法是把这一步做到最省事：**扫码**或**内置浏览器登录**，剩下的全自动。

**方式 A：二维码 / 密钥文本（最通用）**

在 Google、GitHub、微软等网站的安全设置里打开两步验证，选择「无法扫描二维码」，
网站会给你一串 Base32 密钥（形如 `JBSW Y3DP EHPK 3PXP`）。

- Web 版：点 ＋ → 选平台 → 把密钥粘进「密钥」框（空格、连字符会自动清理）→ 保存
- 安卓版：同上，密钥框右侧有「粘贴」按钮，粘 `otpauth://` 链接会自动拆出平台、账号、参数

**方式 B：otpauth 链接**

任何形如
`otpauth://totp/GitHub:me@mail.com?secret=XXXX&issuer=GitHub&digits=6&period=30`
的链接，粘进导入框可一次批量导入多行。安卓版还注册了 `otpauth` 与 `otpauth-migration`
两个 URL scheme，在浏览器里点这种链接会直接跳进 App 导入。

**方式 C：从别的验证器迁移**

Google Authenticator 的「导出账号」生成的是
`otpauth-migration://offline?data=<base64>`，本应用**原生解析这个 protobuf**，一条链接可
一次导入全部令牌（含 issuer、账号、算法、位数）。Authy、Aegis、2FAS 等支持导出明文
`otpauth://` 列表的，直接粘贴也行。

> Steam 的密钥在 Steam 手机令牌里是通过「添加验证器」流程给出的，注意 Steam 用的是
> 5 位字母码，本应用会自动按 Steam 规则生成，不需要你改任何参数。

---

## 五、常见问题

**验证码一直不对？**
1. 手机系统时间是否自动同步（TOTP 严格依赖时间，误差超过 30 秒必错）；
2. 密钥是否抄漏字符 —— 本应用会把 `0` 当 `O`、`1` 当 `L` 容错，但漏字符救不了；
3. 平台是否选错（Steam 必须选 Steam，普通 6 位算法算不出它的 5 位码）。

**换手机怎么办？**
Web 版点「设置 → 导出加密备份」，把文本存到网盘；新机导入。安卓版同理。
不要指望系统备份 —— 本应用主动排除了备份，密钥不会跟着账号云同步。

**忘了 PIN？**
没有找回通道。这是设计取舍：能找回就等于能被别人找回。请务必先导出一份加密备份。

**能不能云端同步？**
不做。令牌本体不上云，同步只能靠手动导出/导入加密备份。
（v1.1.0 起 App 有 `INTERNET` 权限，但它只服务于「登录账号自动获取密钥」的内置浏览器页面，
不会把库里任何数据发出去——代码里没有任何上传逻辑。）

**「登录账号自动获取」抓不到东西？**
1. 要走到 **2FA 设置页**，而不是登录页或首页——密钥只在这个页面出现；
2. 有些平台在已开启状态下只显示「已启用」，不会重新给密钥。此时只能先「移除」再「重新开启」，
   在重新开启的那一次把二维码扫进来；
3. 抓取是每 1.8 秒扫一次页面，页面加载慢就多点几次右上角「抓取」。

---

## 六、目录结构

```
TokenVault/
├─ dist/                      # 成品 APK（release 2.2 MB + debug 26 MB）
├─ web/                      # PWA，零依赖零构建
│  ├─ index.html
│  ├─ styles.css             # 设计系统：深色 #07080c / 紫青渐变
│  ├─ app.js                 # TOTP 算法 + AES-GCM 保险库 + 全部交互
│  ├─ sw.js                  # Service Worker，离线优先
│  ├─ manifest.webmanifest
│  └─ icon.svg
├─ android/
│  ├─ app/src/main/java/com/yg/tokenvault/
│  │  ├─ data/               # Platforms / TokenEntry / Totp / VaultRepository
│  │  │                      # + OtpImporter（统一导入）/ OtpMigration（protobuf 解析）
│  │  ├─ security/           # VaultCrypto（PBKDF2 + AES-GCM）
│  │  ├─ util/               # Base32 / QrDecoder（zxing 封装）
│  │  ├─ ui/theme/           # 配色、字体、深浅主题
│  │  ├─ ui/components/      # BrandTile / TokenCard / RingProgress / PinPad / AppLogo
│  │  ├─ ui/screens/         # Lock / Vault / Editor / Detail / Settings / Tools
│  │  │                      # + ImportSheet（四选一）/ ScanScreen（CameraX 扫码）
│  │  │                      # + LoginScreen（内置浏览器登录抓取）
│  │  ├─ VaultViewModel.kt
│  │  ├─ TokenVaultApp.kt
│  │  └─ MainActivity.kt     # 含 otpauth:// 外链导入
│  ├─ app/src/test/java/com/yg/tokenvault/CoreLogicTest.kt   # 18 项算法单测 + 10 项导入解析单测
│  └─ app/src/main/res/      # 品牌图标、自适应启动图标、字符串、备份排除规则
├─ tools/
│  ├─ serve.py               # 带正确 MIME 的本地服务器
│  ├─ verify.js              # Web 版算法自检（RFC 6238 官方向量）
│  ├─ build_apk.sh           # 一键编译 / 跑测试（自带工具链）
│  ├─ unpack_toolchain.sh    # 解压 JDK
│  ├─ unpack_android_sdk.sh  # 从国内镜像铺 Android SDK
│  ├─ export_icons.py        # 从 web/app.js 的品牌 SVG 生成安卓 VectorDrawable
│  └─ shoot.py               # Playwright 界面截图
├─ shots/                    # 界面截图
└─ .toolchain/               # 自带 JDK17 + SDK35 + Gradle 8.11.1（不装系统环境）
```

---

## 七、算法自检

两套实现各自带一份自检，都跑一遍才能说「两边算的是同一个码」。

```bash
node tools/verify.js             # Web 版算法（JS）
node tools/verify_web_import.js  # Web 版「获取令牌」解析层（JS）
bash tools/build_apk.sh test     # 安卓版（Kotlin，30 项 JVM 单元测试）
```

覆盖范围：

| 项 | Web（JS） | 安卓（Kotlin） |
| --- | --- | --- |
| RFC 6238 SHA-1 六组官方向量 | ✅ | ✅ |
| RFC 6238 SHA-256 / SHA-512 | ✅ | ✅ |
| 6 位 / 8 位 / 前导零补位 | ✅ | ✅ |
| Steam 5 位字母码字符集 | ✅ | ✅ |
| Base32 容错（大小写/空格/0→O/1→L） | ✅ | ✅ |
| 过短密钥拒绝 | ✅ | ✅ |
| 剩余时间与进度 | ✅ | ✅ |
| `otpauth://` 解析（Google/GitHub/Steam/自定义） | ✅ | ✅ |
| 26 个平台默认参数合法性 | — | ✅ |
| WebCrypto 与纯 JS 实现交叉验证 | ✅ | — |
| `otpauth-migration://` protobuf 解析（多账号 / SHA256 / 8 位 / 跳过 HOTP） | ✅ | ✅ |
| 混合文本识别（otpauth + migration + 裸 Base32）与按 secret 去重 | ✅ | ✅ |
| HTML 实体反转义（`&amp;`）、垃圾文本拒绝 | ✅ | ✅ |
| 平台别名识别（Gmail→Google、Outlook→Microsoft、iCloud→Apple、AWS→Amazon） | ✅ | ✅ |
| 单字符 key 不误判（`x` vs `Xbox`） | ✅ | ✅ |

当前结果：**Web 算法 30 项 + Web 导入 39 项 = 69 通过 / 0 失败；安卓 30 通过 / 0 失败。**