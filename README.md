# TokenVault · 令牌保险库

一个**纯离线**的两步验证（TOTP）令牌管理器，同时提供两套实现：

| 交付 | 目录 | 状态 |
| --- | --- | --- |
| Web 版 PWA | [`web/`](web/) | 可直接用，手机浏览器「添加到主屏幕」即装即用 |
| 原生安卓 App | [`android/`](android/) | Kotlin + Jetpack Compose，**已编译出成品包**：[`dist/TokenVault-1.0.0.apk`](dist/)（1.7 MB） |

支持 26 个平台（Google / Microsoft / GitHub / Steam / Apple / Discord / X / Amazon / Dropbox /
Facebook / GitLab / LinkedIn / Slack / Telegram / Twitter / Reddit / Twitch / Spotify / Binance /
Cloudflare / Notion / Proton / Epic / Instagram / Xbox / 自定义），
标准 6 位 TOTP、8 位 TOTP、**Steam 5 位字母码**全部支持。

---

## 一、Web 版：现在就能用

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
TokenVault/dist/TokenVault-1.0.0.apk      # 1.7 MB，正式包名 com.yg.tokenvault
TokenVault/dist/TokenVault-1.0.0-debug.apk # 19 MB，debug 版（带日志，排查用）
```

把 APK 传到手机（数据线 / 微信文件传输 / 网盘）→ 点开 → 允许「安装未知来源应用」→ 装完打开。

- 要求 **Android 8.0（API 26）** 及以上
- 用的是 debug 证书签名，Play 商店上不了，自己用完全没问题
- 装之前**先联网同步一次手机时间** —— TOTP 严格依赖系统时间

> `TokenVault-1.0.0.apk` 经过 R8 压缩混淆（1.7 MB）。万一在你的机型上启动闪退，
> 换 `TokenVault-1.0.0-debug.apk`（19 MB，未混淆）—— 两者功能完全一致，只是后者带调试符号。

### 用 Android Studio 改代码

1. Android Studio → **Open** → 选 `TokenVault/android` 目录（不是 `TokenVault`）；
2. 等 Gradle Sync 完成（首次会下 Compose 依赖，需联网，约几分钟）；
3. 手机开「USB 调试」插上，或开一个模拟器 → 点 ▶ Run。

### 命令行打包（本机已自带全套工具链，不需要装任何东西）

```bash
cd TokenVault
bash tools/build_apk.sh            # → dist/... 之前先出 app/build/outputs/apk/debug/app-debug.apk
bash tools/build_apk.sh release    # 正式包，已配 debug 签名，直接能装
bash tools/build_apk.sh test       # 跑 18 项 JVM 单元测试（TOTP 算法自检）
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

**安全**
- 主密码 / PIN 经 **PBKDF2-HMAC-SHA256（25 万次迭代）** 派生 256 位密钥
- 全部令牌用 **AES-256-GCM** 加密后落盘（认证加密，密文被改即解不开）
- 换 PIN 时重新生成随机盐、整体重加密
- 自动锁定（1/3/5 分钟，切到后台开始计时）
- 「立即锁定」清空内存中的密钥
- **零网络权限**：安卓清单里没有 `INTERNET`，物理上不可能外传
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

**方式 A：二维码 / 密钥文本（最通用）**

在 Google、GitHub、微软等网站的安全设置里打开两步验证，选择「无法扫描二维码」，
网站会给你一串 Base32 密钥（形如 `JBSW Y3DP EHPK 3PXP`）。

- Web 版：点 ＋ → 选平台 → 把密钥粘进「密钥」框（空格、连字符会自动清理）→ 保存
- 安卓版：同上，密钥框右侧有「粘贴」按钮，粘 `otpauth://` 链接会自动拆出平台、账号、参数

**方式 B：otpauth 链接**

任何形如
`otpauth://totp/GitHub:me@mail.com?secret=XXXX&issuer=GitHub&digits=6&period=30`
的链接，粘进导入框可一次批量导入多行。

**方式 C：从别的验证器迁移**

大部分验证器（Authy、Aegis、2FAS 等）支持「导出」为 `otpauth-migration://` 或
明文 `otpauth://` 列表。本应用接受明文列表；迁移用的 protobuf 格式请先用对方 App
导出成链接/二维码再逐条录入。

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
不做。App 没有网络权限，同步只能靠手动导出/导入。

---

## 六、目录结构

```
TokenVault/
├─ dist/                      # 成品 APK（release 1.7 MB + debug 19 MB）
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
│  │  ├─ security/           # VaultCrypto（PBKDF2 + AES-GCM）
│  │  ├─ ui/theme/           # 配色、字体、深浅主题
│  │  ├─ ui/components/      # BrandTile / TokenCard / RingProgress / PinPad / AppLogo
│  │  ├─ ui/screens/         # Lock / Vault / Editor / Detail / Settings / Tools
│  │  ├─ VaultViewModel.kt
│  │  ├─ TokenVaultApp.kt
│  │  └─ MainActivity.kt
│  ├─ app/src/test/java/com/yg/tokenvault/CoreLogicTest.kt   # 18 项算法单测
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
node tools/verify.js          # Web 版（JS）
bash tools/build_apk.sh test  # 安卓版（Kotlin，18 项 JVM 单元测试）
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

当前结果：**Web 30 通过 / 0 失败；安卓 18 通过 / 0 失败。**