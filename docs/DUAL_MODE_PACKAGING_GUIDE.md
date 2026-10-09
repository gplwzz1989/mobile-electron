# Mobile Electron 双模打包使用指南 (在线 URL 与本地离线页面)

> **版本**：v2.0.0 Enterprise  
> **核心特性**：一键切换“在线远程 URL 模式”与“内置本地离线包模式”，本地模式全量资产打入 APK/IPA，原生启动即加载且享有完整特权 SDK 能力。

---

## 1. 核心特性概述

本容器框架现已全面支持两种运行与打包形态：

| 特性维度 | 本地离线包模式 (`local`) | 在线 URL 模式 (`online`) |
| :--- | :--- | :--- |
| **典型适用场景** | 独立离线 App、控制中心仪表盘、内网无网络运行 | 远程部署的生产 SPA 网站、即时更新业务站点 |
| **资源打包形式** | 前端全量 HTML/CSS/JS/图片打入 APK (`assets/dist`) 及 IPA Bundle | 仅打入原生容器底座，不强制捆绑前端静态文件 |
| **网络依赖** | **零网络依赖**，启动即秒开 | 依赖移动端网络加载远程服务 |
| **原生特权能力** | 支持（`DomainWhitelistManager` 默认放行本地特权） | 支持（需在配置的 `security.whitelist` 白名单内） |
| **现代前端特性** | 完整支持 ES6 Modules、Fetch、LocalStorage、Worker 等 | 完整支持现代前端标准特性 |

---

## 2. 命令行一键打包操作

项目根目录下提供了统一的预处理脚本 [scripts/prepare-build.js](file:///f:/mywork/安卓cookie读写/scripts/prepare-build.js) 与 npm 快捷指令：

### 2.1 打包为“本地离线包”App

执行以下命令，工具将自动完成 SDK 构建、Vite 相对路径构建、资源同步拷贝以及配置切换：

```bash
# 方式一：使用 npm 脚本一键预处理
npm run mode:local

# 方式二：直接执行 Node 脚本
node scripts/prepare-build.js --mode local

# 方式三：一键预处理并直接生成 Android Release APK
npm run build:apk:local
```

执行后：
1. `sdk` 编译输出到 `sdk/dist`；
2. `web` 编译输出到 `web/dist`（静态资源引用使用相对路径 `./assets/...`）；
3. 产物自动全量同步至：
   - Android: `app/src/main/assets/dist/`
   - iOS: `ios/MobileElectron/dist/`
4. Android 与 iOS 的 `app-config.json` 自动更新为 `"defaultUrl": "file:///dist/index.html"`。

此时执行 `./gradlew assembleRelease` 生成的 APK，以及 Xcode 导出的 IPA 将内置完整离线网页。

---

### 2.2 打包为“在线远程 URL”App

若需要让 App 启动后直接加载线上部署的网站（如生产域名或测试服务器）：

```bash
# 方式一：指定在线目标 URL 进行预处理
node scripts/prepare-build.js --mode online --url https://m.baidu.com

# 方式二：一键预处理并直接编译 Android Release APK
node scripts/prepare-build.js --mode online --url https://your-site.com && gradlew assembleRelease
```

执行后：
- 自动将 Android 与 iOS 的 `app-config.json` 中的 `defaultUrl` 设置为目标 URL；
- 自动将目标域名的 Host 动态追加进 `security.whitelist` 白名单中，保障特权通信畅通。

---

## 3. GitHub Actions CI/CD 自动化流水线

在 [.github/workflows/ci.yml](file:///f:/mywork/安卓cookie读写/.github/workflows/ci.yml) 中已接入流水线手动触发（`workflow_dispatch`）：

1. 进入 GitHub 仓库 -> 点击 **Actions** -> 选择 **Mobile Electron CI (Build APK & IPA)**；
2. 点击 **Run workflow**：
   - **打包模式 (`build_mode`)**：下拉选择 `local`（内置本地离线页面）或 `online`（加载在线 URL）；
   - **在线 URL 地址 (`online_url`)**：若选择 `online`，输入启动网页地址（如 `https://example.com`）；
3. 流水线执行完毕后，自动在 Artifacts 中生成对应模式的 APK 及 IPA 安装包！

---

## 4. 底层技术实现与保障机制

### 4.1 前端相对路径机制
- 在 [web/vite.config.ts](file:///f:/mywork/安卓cookie读写/web/vite.config.ts) 中配置 `base: './'`；
- 编译生成的 `index.html` 采用相对寻址（`./assets/index-xxx.js`），在本地沙箱中彻底杜绝根路径 `/assets/` 404 导致的白屏。

### 4.2 Android 端双轨安全加载
- **优先启用 AndroidX `WebViewAssetLoader`**：
  在 [WebViewPool.java](file:///f:/mywork/安卓cookie读写/app/src/main/java/com/example/cookiebrowser/core/WebViewPool.java) 与 [MainActivity.java](file:///f:/mywork/安卓cookie读写/app/src/main/java/com/example/cookiebrowser/MainActivity.java) 中将本地资源映射至虚拟 HTTPS 域名 `https://appassets.androidplatform.net/assets/dist/index.html`；
  完全消除了 Chromium 原生对 `file:///` 协议加载 `<script type="module">` 时的 CORS 限制。
- **保留 `file:///android_asset/` 双保险支持**：
  开启 `setAllowFileAccessFromFileURLs(true)` 与 `setAllowUniversalAccessFromFileURLs(true)`。

### 4.3 iOS 端安全读取权限
- 在 [WebViewPool.swift](file:///f:/mywork/安卓cookie读写/ios/MobileElectron/Core/WebViewPool.swift) 与 [ViewController.swift](file:///f:/mywork/安卓cookie读写/ios/MobileElectron/ViewController.swift) 中增强路径规范化处理；
- 使用 `mainWebView.loadFileURL(fileUrl, allowingReadAccessTo: Bundle.main.bundleURL)`，向 WKWebView 授予 App Bundle 根目录的完整只读沙盒权限，所有静态资源及字体图片平滑加载。

### 4.4 安全白名单网关放行
- [DomainWhitelistManager.java](file:///f:/mywork/安卓cookie读写/app/src/main/java/com/example/cookiebrowser/security/DomainWhitelistManager.java) 与 [DomainWhitelistManager.swift](file:///f:/mywork/安卓cookie读写/ios/MobileElectron/Security/DomainWhitelistManager.swift) 对 `file://`、`local://` 以及 `appassets.androidplatform.net` 统一信任为 Tier 2（核心高危特权），确保本地页面内的 SDK 拥有调用全部原生接口的合法权限。
